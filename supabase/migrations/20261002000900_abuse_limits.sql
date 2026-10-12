-- =====================================================================
-- Security hardening (audit 2026-10-02)
-- 1. Per-IP rate limits. The RPCs' existing limits key on device_id, which the caller chooses,
--    so one script could rotate ids to flood reports, inflate downloads/views (gaming Popular)
--    or grow user_events/search_log until the free 500 MB database fills.
--    Limits are generous on purpose: mobile carriers (e.g. Jio CGNAT) put many real users
--    behind one IP. They bound what a single machine can do, not what a botnet can do.
-- 2. Length limits on the two profile fields users can write.
-- Only a salted hash of the IP is stored, and only for 2 days.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Rate limiter
-- ---------------------------------------------------------------------
create table if not exists private.rate_salt (
  id   int primary key default 1 check (id = 1),
  salt text not null default replace(gen_random_uuid()::text, '-', '')
);
insert into private.rate_salt (id) values (1) on conflict do nothing;

create table if not exists private.rate_hits (
  key    text        not null,   -- hash of the client IP
  bucket text        not null,   -- what was limited: report, event, download, search_log, push_open
  at     timestamptz not null default now()
);
create index if not exists rate_hits_lookup_idx on private.rate_hits (key, bucket, at desc);
create index if not exists rate_hits_at_idx on private.rate_hits (at);
revoke all on private.rate_salt, private.rate_hits from public, anon, authenticated;

-- Hash of the caller's IP from the API gateway headers; null when there's no HTTP request
-- (SQL editor, pg_cron, service role scripts), which are never limited.
create or replace function private.client_key()
returns text language plpgsql stable security definer set search_path = '' as $$
declare
  h  jsonb;
  ip text;
begin
  begin
    h := nullif(current_setting('request.headers', true), '')::jsonb;
  exception when others then
    h := null;
  end;
  if h is null then return null; end if;
  ip := coalesce(nullif(trim(h ->> 'cf-connecting-ip'), ''),
                 nullif(trim(h ->> 'x-real-ip'), ''),
                 nullif(trim(split_part(coalesce(h ->> 'x-forwarded-for', ''), ',', 1)), ''));
  if ip is null then return null; end if;
  return left(md5(ip || (select s.salt from private.rate_salt s where s.id = 1)), 20);
end;
$$;

-- true = allowed (and counted); false = this IP used up `max_hits` for `bucket` within `win`.
create or replace function private.rate_ok(p_bucket text, p_max int, p_win interval)
returns boolean language plpgsql volatile security definer set search_path = '' as $$
declare k text := private.client_key();
begin
  if k is null then return true; end if;
  if (select count(*) from private.rate_hits r
      where r.key = k and r.bucket = p_bucket and r.at > now() - p_win) >= p_max then
    return false;
  end if;
  insert into private.rate_hits (key, bucket) values (k, p_bucket);
  return true;
end;
$$;

revoke all on function private.client_key() from public, anon, authenticated;
revoke all on function private.rate_ok(text, int, interval) from public, anon, authenticated;

-- ---------------------------------------------------------------------
-- Reports: 20 per hour per IP (legit users file one now and then)
-- ---------------------------------------------------------------------
create or replace function private.reports_rate_limit()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() and not private.rate_ok('report', 20, interval '1 hour') then
    -- PTxyz makes the API answer with HTTP xyz
    raise exception 'Too many reports from your network. Please try again later.' using errcode = 'PT429';
  end if;
  new.device_id := left(new.device_id, 64);
  return new;
end;
$$;
drop trigger if exists reports_rate_limit on public.reports;
create trigger reports_rate_limit before insert on public.reports
  for each row execute function private.reports_rate_limit();

-- ---------------------------------------------------------------------
-- record_event: at most 1200 counted events per hour per IP (silently not counted above that)
-- (same body as migration 0200, plus the rate_ok check)
-- ---------------------------------------------------------------------
create or replace function public.record_event(wallpaper_id uuid, type text, device_id text)
returns void
language plpgsql volatile security definer
set search_path = public, extensions
as $$
#variable_conflict use_column
declare
  v_wid  uuid := $1;
  v_type text := lower(trim(coalesce($2, '')));
  v_dev  text := private.clean_device_id($3);
  v_uid  uuid := auth.uid();
  v_win  interval;
begin
  if v_type = 'download' then
    raise exception 'use increment_download() for downloads' using errcode = '22023';
  end if;
  if v_type not in ('view', 'apply', 'fav', 'unfav') then
    raise exception 'invalid event type: %', $2 using errcode = '22023';
  end if;
  if not exists (select 1 from wallpapers_public v where v.id = v_wid) then
    return;
  end if;

  v_win := case v_type when 'view' then interval '30 minutes'
                       when 'apply' then interval '1 minute'
                       else interval '5 seconds' end;
  if exists (select 1 from user_events e
             where e.device_id = v_dev and e.wallpaper_id = v_wid and e.type = v_type
               and e.created_at > now() - v_win) then
    return;
  end if;
  if not private.rate_ok('event', 1200, interval '1 hour') then
    return;
  end if;

  insert into user_events (user_id, device_id, wallpaper_id, type)
  values (v_uid, v_dev, v_wid, v_type);

  if v_type = 'view' then
    update wallpapers w set view_count = w.view_count + 1 where w.id = v_wid;
  end if;

  perform private.bump_daily_stat(v_wid, v_type);

  if v_uid is not null then
    perform private.bump_affinity(v_uid, v_wid,
      case v_type when 'view' then 1 when 'apply' then 3 when 'fav' then 5 else -5 end);
  end if;
end;
$$;

-- ---------------------------------------------------------------------
-- increment_download: at most 1000 counted downloads per day per IP
-- (same body as migration 0200, plus the rate_ok check)
-- ---------------------------------------------------------------------
create or replace function public.increment_download(wallpaper_id uuid, device_id text)
returns int
language plpgsql volatile security definer
set search_path = public, extensions
as $$
#variable_conflict use_column
declare
  v_wid   uuid := $1;
  v_dev   text := private.clean_device_id($2);
  v_uid   uuid := auth.uid();
  v_count int;
begin
  select v.download_count into v_count from wallpapers_public v where v.id = v_wid;
  if not found then
    raise exception 'wallpaper not found' using errcode = 'P0002';
  end if;

  if exists (select 1 from user_events e
             where e.device_id = v_dev and e.wallpaper_id = v_wid and e.type = 'download'
               and e.created_at > now() - interval '1 hour')
     or (select count(*) from user_events e
         where e.device_id = v_dev and e.type = 'download'
           and e.created_at > now() - interval '1 day') >= 200
     or not private.rate_ok('download', 1000, interval '1 day') then
    return v_count;
  end if;

  insert into user_events (user_id, device_id, wallpaper_id, type)
  values (v_uid, v_dev, v_wid, 'download');

  update wallpapers w set download_count = w.download_count + 1
  where w.id = v_wid
  returning w.download_count into v_count;

  perform private.bump_daily_stat(v_wid, 'download');
  if v_uid is not null then
    perform private.bump_affinity(v_uid, v_wid, 3);
  end if;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------
-- search_wallpapers: searching is never blocked, but at most 300 searches per hour per IP
-- are written to search_log (same body as migration 0200 otherwise)
-- ---------------------------------------------------------------------
create or replace function public.search_wallpapers(
  q         text    default null,
  color     text    default null,
  device    text    default null,
  amoled    boolean default null,
  category  text    default null,
  page      int     default 0,
  page_size int     default 30
)
returns setof public.wallpapers_public
language plpgsql volatile security definer
set search_path = public, extensions
as $$
declare
  lim   int  := private.page_limit($7);
  off   int  := private.page_offset($6, $7);
  qn    text := nullif(lower(trim($1)), '');
  p_col text := nullif(lower(trim($2)), '');
  p_dev text := nullif(lower(trim($3)), '');
  p_amo boolean := $4;
  p_cat text := nullif(lower(trim($5)), '');
  tsq   tsquery;
  n     int;
begin
  if qn is not null then
    qn  := left(qn, 100);
    tsq := websearch_to_tsquery('english', qn);
  end if;

  return query
  select v.* from wallpapers_public v
  join wallpapers w on w.id = v.id
  where (qn is null
         or w.search_tsv @@ tsq
         or lower(w.title) % qn
         or exists (select 1 from wallpaper_tags wt join tags t on t.id = wt.tag_id
                    where wt.wallpaper_id = v.id and t.name % qn))
    and (p_col is null or v.color_family = p_col)
    and (p_dev is null
         or (p_dev = 'phone'  and v.device_type in ('phone', 'both'))
         or (p_dev = 'tablet' and v.device_type in ('tablet', 'both')))
    and (p_amo is null or v.is_amoled = p_amo)
    and (p_cat is null or v.category_slug = p_cat)
  order by
    (case when qn is null then 0
          else ts_rank(w.search_tsv, tsq) + similarity(lower(w.title), qn) end) desc,
    v.popularity_score desc, v.download_count desc, v.id
  limit lim offset off;

  get diagnostics n = row_count;
  if qn is not null and coalesce($6, 0) = 0 and private.rate_ok('search_log', 300, interval '1 hour') then
    insert into search_log (q, result_count) values (qn, n);
  end if;
end;
$$;

-- ---------------------------------------------------------------------
-- record_push_open: at most 60 counted opens per hour per IP
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
  if not exists (select 1 from public.push_log where id = $1) then
    return;
  end if;
  if not private.rate_ok('push_open', 60, interval '1 hour') then
    return;
  end if;
  insert into public.push_opens (push_id, device_id)
  values ($1, $2)
  on conflict do nothing;
  if found then
    update public.push_log set opens = opens + 1 where id = $1;
  end if;
end;
$$;

-- Grants are kept by CREATE OR REPLACE; restated so this file is self-contained.
revoke execute on function public.record_event(uuid, text, text)                               from public;
revoke execute on function public.increment_download(uuid, text)                               from public;
revoke execute on function public.search_wallpapers(text, text, text, boolean, text, int, int) from public;
revoke execute on function public.record_push_open(uuid, text)                                 from public;
grant  execute on function public.record_event(uuid, text, text)                               to anon, authenticated;
grant  execute on function public.increment_download(uuid, text)                               to anon, authenticated;
grant  execute on function public.search_wallpapers(text, text, text, boolean, text, int, int) to anon, authenticated;
grant  execute on function public.record_push_open(uuid, text)                                 to anon, authenticated;

-- ---------------------------------------------------------------------
-- Profiles: bounded name, https-only avatar. NOT VALID = existing rows aren't re-checked.
-- ---------------------------------------------------------------------
alter table public.profiles drop constraint if exists profiles_display_name_len;
alter table public.profiles add constraint profiles_display_name_len
  check (display_name is null or char_length(display_name) <= 80) not valid;
alter table public.profiles drop constraint if exists profiles_avatar_url_ok;
alter table public.profiles add constraint profiles_avatar_url_ok
  check (avatar_url is null or (char_length(avatar_url) <= 1000 and avatar_url ~* '^https://')) not valid;

-- Sign-up copies name/avatar from user-supplied metadata: clamp them so sign-up never fails.
create or replace function private.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  v_name   text := left(coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name',
                                 split_part(new.email, '@', 1)), 80);
  v_avatar text := new.raw_user_meta_data ->> 'avatar_url';
begin
  if v_avatar is not null and (char_length(v_avatar) > 1000 or v_avatar !~* '^https://') then
    v_avatar := null;
  end if;
  insert into public.profiles (id, display_name, avatar_url)
  values (new.id, v_name, v_avatar)
  on conflict (id) do nothing;
  return new;
end;
$$;

-- ---------------------------------------------------------------------
-- Cleanup: rate-limit hits are only needed for the longest window (1 day)
-- ---------------------------------------------------------------------
create or replace function private.prune_old_data()
returns void
language sql security definer
set search_path = ''
as $$
  delete from public.user_events           where created_at < now() - interval '90 days';
  delete from public.wallpaper_daily_stats where day        < current_date - 400;
  delete from public.search_log            where created_at < now() - interval '180 days';
  delete from private.rate_hits            where at         < now() - interval '2 days';
$$;
revoke all on function private.prune_old_data() from public, anon, authenticated;
