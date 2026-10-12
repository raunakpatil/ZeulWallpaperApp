-- =====================================================================
-- A16 · Push notifications (sending side). See CONTRACTS section 8.
--   push_log          : every send (manual or daily), admin-readable
--   push_opens        : one row per (push, device) for the "opens" count
--   record_push_open  : called by the app when a notification is tapped
--   daily push        : pg_cron checks app_config.daily_push every hour and
--                       calls the send-push Edge Function through pg_net
-- =====================================================================

create table if not exists public.push_log (
  id             uuid primary key default gen_random_uuid(),
  created_at     timestamptz not null default now(),
  sent_by        uuid references auth.users (id) on delete set null,
  kind           text not null check (kind in ('manual', 'daily')),
  env            text not null check (env in ('prod', 'debug')),
  topic          text not null,
  type           text not null,
  title          text not null,
  body           text not null default '',
  image_url      text,
  link           text,
  wallpaper_id   uuid references public.wallpapers (id) on delete set null,
  collection_id  uuid references public.collections (id) on delete set null,
  status         text not null check (status in ('sent', 'failed')),
  fcm_message_id text,
  error          text,
  opens          int not null default 0
);
create index if not exists push_log_created_idx on public.push_log (created_at desc);

create table if not exists public.push_opens (
  push_id    uuid not null references public.push_log (id) on delete cascade,
  device_id  text not null,
  created_at timestamptz not null default now(),
  primary key (push_id, device_id)
);

alter table public.push_log   enable row level security;
alter table public.push_opens enable row level security;

-- Admins read the log; only the Edge Function (service role) writes it. Nobody else touches these tables.
revoke all on public.push_log, public.push_opens from anon, authenticated;
grant select on public.push_log to authenticated;
drop policy if exists push_log_admin_read on public.push_log;
create policy push_log_admin_read on public.push_log for select to authenticated using (public.is_admin());

-- ---------------------------------------------------------------------
-- record_push_open(push_id, device_id) → counts each device once.
-- ---------------------------------------------------------------------
create or replace function public.record_push_open(push_id uuid, device_id text)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if $2 is null or length($2) not between 8 and 64 then
    return;
  end if;
  insert into public.push_opens (push_id, device_id)
  select $1, $2 where exists (select 1 from public.push_log where id = $1)
  on conflict do nothing;
  if found then
    update public.push_log set opens = opens + 1 where id = $1;
  end if;
end;
$$;
revoke execute on function public.record_push_open(uuid, text) from public;
grant  execute on function public.record_push_open(uuid, text) to anon, authenticated;

-- ---------------------------------------------------------------------
-- Daily push settings (edited in the dashboard)
--   enabled   : send automatically every day
--   hour_utc  : 0-23 (dashboard shows it in the admin's local time)
--   auto_pick : pick a fresh popular wallpaper each day and set daily_wallpaper_id
--   title     : notification title; the body is the wallpaper title
-- ---------------------------------------------------------------------
insert into public.app_config (key, value)
values ('daily_push', '{"enabled": false, "hour_utc": 4, "auto_pick": true, "title": "Today''s wallpaper ✨"}')
on conflict (key) do nothing;

-- ---------------------------------------------------------------------
-- How pg_cron reaches the Edge Function. Filled in once by the admin with
--   update private.push_cron set url = 'https://<ref>.supabase.co/functions/v1/send-push', secret = '<CRON_SECRET>';
-- (same secret as the function's CRON_SECRET). Not readable through the API.
-- ---------------------------------------------------------------------
create table if not exists private.push_cron (
  id     int primary key default 1 check (id = 1),
  url    text,
  secret text
);
insert into private.push_cron (id) values (1) on conflict do nothing;
revoke all on private.push_cron from public, anon, authenticated;

create extension if not exists pg_net;

create or replace function private.trigger_daily_push()
returns text
language plpgsql security definer
set search_path = ''
as $$
declare
  cfg jsonb := (select value from public.app_config where key = 'daily_push');
  c   private.push_cron;
begin
  if coalesce((cfg ->> 'enabled')::boolean, false) is not true then
    return 'disabled';
  end if;
  if extract(hour from now() at time zone 'utc')::int <> coalesce((cfg ->> 'hour_utc')::int, 4) then
    return 'not this hour';
  end if;
  if exists (select 1 from public.push_log where kind = 'daily' and env = 'prod' and status = 'sent'
             and created_at > now() - interval '20 hours') then
    return 'already sent today';
  end if;
  select * into c from private.push_cron where id = 1;
  if c.url is null or c.secret is null then
    return 'cron not configured';
  end if;
  perform net.http_post(
    url     := c.url,
    body    := '{"mode": "daily"}'::jsonb,
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', c.secret),
    timeout_milliseconds := 20000
  );
  return 'requested';
end;
$$;
revoke all on function private.trigger_daily_push() from public, anon, authenticated;

-- Lets the dashboard show whether the daily push is wired up, without exposing the secret.
create or replace function public.admin_push_status()
returns jsonb
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_admin() then
    raise exception 'admin only' using errcode = '42501';
  end if;
  return jsonb_build_object(
    'cron_configured', exists (select 1 from private.push_cron where url is not null and secret is not null),
    'prod_sends_24h', (select count(*) from public.push_log where kind = 'manual' and env = 'prod' and status = 'sent'
                       and created_at > now() - interval '24 hours'),
    'total_opens', (select coalesce(sum(opens), 0) from public.push_log)
  );
end;
$$;
revoke execute on function public.admin_push_status() from public, anon;
grant  execute on function public.admin_push_status() to authenticated;

select cron.schedule('daily-push', '0 * * * *', $$select private.trigger_daily_push()$$);
