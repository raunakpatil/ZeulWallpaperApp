-- Smoke test for A16 (push log, opens, daily trigger). Runs in a transaction and rolls back.
\set QUIET on
begin;

create or replace function pg_temp.check(cond boolean, label text) returns void language plpgsql as $$
begin
  if cond is not true then raise exception 'FAIL: %', label; end if;
  raise notice 'ok  %', label;
end $$;
grant execute on function pg_temp.check(boolean, text) to anon, authenticated;

insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a6', 'admin16@test.dev'),
  ('00000000-0000-0000-0000-0000000000b6', 'user16@test.dev');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000a6';

insert into public.push_log (id, kind, env, topic, type, title, status) values
  ('16161616-0000-0000-0000-000000000001', 'manual', 'prod', 'prod_new_drops', 'new_drop', 'Fresh drop', 'sent');

-- guest taps the notification
set local role anon;
select record_push_open('16161616-0000-0000-0000-000000000001', 'device-aaaa-1111');
select record_push_open('16161616-0000-0000-0000-000000000001', 'device-aaaa-1111');   -- same device again
select record_push_open('16161616-0000-0000-0000-000000000001', 'device-bbbb-2222');
select record_push_open('16161616-0000-0000-0000-000000000001', 'x');                  -- invalid device id ignored
select record_push_open('16161616-0000-0000-0000-0000000000ff', 'device-cccc-3333');   -- unknown push ignored
do $$ begin
  perform 1 from push_log;
  raise exception 'FAIL: anon read push_log';
exception when insufficient_privilege then raise notice 'ok  anon cannot read push_log';
end $$;
reset role;
select pg_temp.check((select opens from push_log where id = '16161616-0000-0000-0000-000000000001') = 2, 'opens counted once per device');
select pg_temp.check((select count(*) from push_opens) = 2, 'unknown push and bad device ignored');

-- regular user
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b6', true);
select pg_temp.check((select count(*) from push_log) = 0, 'non-admin sees no push log');
do $$ begin
  perform admin_push_status();
  raise exception 'FAIL: user read push status';
exception when insufficient_privilege then raise notice 'ok  non-admin cannot read push status';
end $$;
do $$ begin
  insert into push_log (kind, env, topic, type, title, status) values ('manual','prod','t','x','spoof','sent');
  raise exception 'FAIL: user inserted push_log';
exception when insufficient_privilege then raise notice 'ok  users cannot write push_log';
end $$;

-- admin
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a6', true);
select pg_temp.check((select count(*) from push_log) = 1, 'admin reads push log');
select pg_temp.check((admin_push_status() ->> 'prod_sends_24h')::int = 1, 'status counts prod sends');
select pg_temp.check((admin_push_status() ->> 'cron_configured')::boolean = false, 'cron not configured yet');
reset role;

-- daily trigger decisions
select pg_temp.check(private.trigger_daily_push() = 'disabled', 'daily push off by default');
update app_config set value = jsonb_set(jsonb_set(value, '{enabled}', 'true'), '{hour_utc}',
  to_jsonb((extract(hour from now() at time zone 'utc')::int + 1) % 24)) where key = 'daily_push';
select pg_temp.check(private.trigger_daily_push() = 'not this hour', 'waits for the configured hour');
update app_config set value = jsonb_set(value, '{hour_utc}', to_jsonb(extract(hour from now() at time zone 'utc')::int)) where key = 'daily_push';
select pg_temp.check(private.trigger_daily_push() = 'cron not configured', 'needs url + secret');
update private.push_cron set url = 'http://127.0.0.1:9/send-push', secret = 'test-secret';
select pg_temp.check(private.trigger_daily_push() = 'requested', 'requests the function at the right hour');
insert into push_log (kind, env, topic, type, title, status) values ('daily','prod','prod_daily_wallpaper','daily','Today','sent');
select pg_temp.check(private.trigger_daily_push() = 'already sent today', 'never twice a day');

select pg_temp.check((select count(*) from cron.job where jobname = 'daily-push') = 1, 'hourly cron job scheduled');

\echo 'ALL A16 CHECKS PASSED'
rollback;
