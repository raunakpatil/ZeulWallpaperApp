-- =====================================================================
-- A11 · Subscriptions (RevenueCat → Supabase)
--   billing_events : every webhook event once (idempotency + audit), admin-readable
--   expire job     : turns is_premium off when premium_until passes, even if a webhook was missed
-- profiles.is_premium / premium_until already exist (0100) and users can't write them.
-- =====================================================================

create table if not exists public.billing_events (
  id           text primary key,                 -- RevenueCat event.id
  received_at  timestamptz not null default now(),
  type         text not null,
  environment  text,                             -- PRODUCTION | SANDBOX
  app_user_id  text,
  user_id      uuid references auth.users (id) on delete set null,
  premium      boolean,                          -- state we wrote (null = no change)
  premium_until timestamptz,
  payload      jsonb not null
);
create index if not exists billing_events_user_idx on public.billing_events (user_id, received_at desc);

alter table public.billing_events enable row level security;
revoke all on public.billing_events from anon, authenticated;
grant select on public.billing_events to authenticated;
drop policy if exists billing_events_admin_read on public.billing_events;
create policy billing_events_admin_read on public.billing_events for select to authenticated using (public.is_admin());

-- One hour of grace so a renewal that arrives a little late doesn't flicker premium off.
create or replace function private.expire_premium()
returns int
language plpgsql security definer
set search_path = ''
as $$
declare n int;
begin
  update public.profiles set is_premium = false
   where is_premium and premium_until is not null and premium_until < now() - interval '1 hour';
  get diagnostics n = row_count;
  return n;
end;
$$;
revoke all on function private.expire_premium() from public, anon, authenticated;

select cron.schedule('expire-premium', '23 * * * *', $$select private.expire_premium()$$);
