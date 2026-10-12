-- Smoke test for migration 0900 (per-IP abuse limits + profile field limits).
-- Runs in a transaction and rolls back. Simulates API calls by setting request.headers like PostgREST does.
\set QUIET on
begin;

create or replace function pg_temp.check(cond boolean, label text) returns void language plpgsql as $$
begin
  if cond is not true then raise exception 'FAIL: %', label; end if;
  raise notice 'ok  %', label;
end $$;
grant execute on function pg_temp.check(boolean, text) to anon, authenticated;

-- Pretend the request came from this IP (what the API gateway passes through)
create or replace function pg_temp.from_ip(ip text) returns void language sql as $$
  select set_config('request.headers', json_build_object('x-forwarded-for', ip || ', 10.0.0.1')::text, true);
$$;
grant execute on function pg_temp.from_ip(text) to anon, authenticated;

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-0000000009a0', 'admin09@test.dev', '{}'),
  ('00000000-0000-0000-0000-0000000009b0', 'user09@test.dev', '{}'),
  ('00000000-0000-0000-0000-0000000009c0', 'long09@test.dev',
     json_build_object('full_name', repeat('n', 200), 'avatar_url', 'http://insecure.example/a.png')::jsonb);
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000009a0';

insert into public.wallpapers (id, title, storage_key, width, height, is_published) values
  ('09090909-0000-0000-0000-000000000001', 'Rate test', 'w/09test0001', 1080, 2400, true);
insert into public.push_log (id, kind, env, topic, type, title, status) values
  ('09090909-0000-0000-0000-0000000000aa', 'manual', 'prod', 'prod_all', 'announcement', 'Hi', 'sent');

-- ---------------------------------------------------------------- no HTTP request → never limited
select pg_temp.check(private.client_key() is null, 'no request headers → no client key');
select pg_temp.check((select bool_and(private.rate_ok('x', 1, interval '1 hour')) from generate_series(1, 5)), 'SQL editor / cron calls are never limited');
select pg_temp.check((select count(*) from private.rate_hits) = 0, 'nothing recorded without a client key');

-- ---------------------------------------------------------------- keys
select pg_temp.from_ip('203.0.113.7');
select pg_temp.check(length(private.client_key()) = 20 and private.client_key() !~ '203', 'IP stored only as a salted hash');
select set_config('request.headers', '{"cf-connecting-ip":"198.51.100.1","x-forwarded-for":"203.0.113.7"}', true);
select pg_temp.check(private.client_key() <> (select private.client_key() from (select pg_temp.from_ip('203.0.113.7')) s), 'cf-connecting-ip preferred over x-forwarded-for');

-- ---------------------------------------------------------------- reports: 20/hour/IP
set local role anon;
select pg_temp.from_ip('203.0.113.7');
do $$ begin
  for i in 1..20 loop
    insert into public.reports (wallpaper_id, device_id, reason) values ('09090909-0000-0000-0000-000000000001', 'dev-' || i, 'spam ' || i);
  end loop;
end $$;
do $$ begin
  insert into public.reports (wallpaper_id, device_id, reason) values ('09090909-0000-0000-0000-000000000001', 'dev-21', 'one more');
  raise exception 'FAIL: 21st report from the same IP was accepted';
exception when sqlstate 'PT429' then raise notice 'ok  21st report in an hour from one IP → HTTP 429';
end $$;
select pg_temp.from_ip('203.0.113.8');
insert into public.reports (wallpaper_id, device_id, reason) values ('09090909-0000-0000-0000-000000000001', 'other', 'different network');
reset role;
select pg_temp.check((select count(*) from public.reports) = 21, 'another IP can still report');

-- admins are never limited
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000009a0', true);
select pg_temp.from_ip('203.0.113.7');
insert into public.reports (wallpaper_id, reason) values ('09090909-0000-0000-0000-000000000001', 'admin test');
reset role;
select pg_temp.check((select count(*) from public.reports) = 22, 'admin not limited');

-- ---------------------------------------------------------------- record_event: 1200/hour/IP
set local role anon;
select pg_temp.from_ip('192.0.2.50');
select count(record_event('09090909-0000-0000-0000-000000000001', 'view', 'rot-' || g)) from generate_series(1, 1250) g;
reset role;
select pg_temp.check((select view_count from public.wallpapers where id = '09090909-0000-0000-0000-000000000001') = 1200,
  'rotating device_id: only 1200 views counted per IP per hour');
select pg_temp.check((select count(*) from public.user_events where type = 'view') = 1200, 'no extra event rows stored past the limit');

-- ---------------------------------------------------------------- increment_download: 1000/day/IP
set local role anon;
select pg_temp.from_ip('192.0.2.60');
select max(increment_download('09090909-0000-0000-0000-000000000001', 'dl-' || g)) from generate_series(1, 1050) g;
reset role;
select pg_temp.check((select download_count from public.wallpapers where id = '09090909-0000-0000-0000-000000000001') = 1000,
  'rotating device_id: only 1000 downloads counted per IP per day');
set local role anon;
select pg_temp.from_ip('192.0.2.61');
select pg_temp.check(increment_download('09090909-0000-0000-0000-000000000001', 'fresh-device') = 1001, 'other IP still counts and gets the new total');
reset role;

-- ---------------------------------------------------------------- search_log: 300/hour/IP (search itself still works)
set local role anon;
select pg_temp.from_ip('192.0.2.70');
select count(*) from (select (search_wallpapers('rate ' || g)).id from generate_series(1, 320) g) s;
select pg_temp.check((select count(*) from search_wallpapers('rate test')) = 1, 'search still answers past the logging limit');
reset role;
select pg_temp.check((select count(*) from public.search_log) = 300, 'only 300 searches per IP per hour written to search_log');

-- ---------------------------------------------------------------- push opens: 60/hour/IP
set local role anon;
select pg_temp.from_ip('192.0.2.80');
select count(record_push_open('09090909-0000-0000-0000-0000000000aa', 'push-dev-' || lpad(g::text, 4, '0'))) from generate_series(1, 70) g;
reset role;
select pg_temp.check((select opens from public.push_log where id = '09090909-0000-0000-0000-0000000000aa') = 60, 'only 60 push opens counted per IP per hour');

-- ---------------------------------------------------------------- normal app use from a fresh IP is unaffected
set local role anon;
select pg_temp.from_ip('192.0.2.99');
select record_event('09090909-0000-0000-0000-000000000001', 'view', 'normal-device-1');
select record_event('09090909-0000-0000-0000-000000000001', 'view', 'normal-device-1');   -- device dedupe still applies
reset role;
select pg_temp.check((select view_count from public.wallpapers where id = '09090909-0000-0000-0000-000000000001') = 1201, 'normal view counted once (device window intact)');

-- ---------------------------------------------------------------- profiles
select pg_temp.check((select char_length(display_name) = 80 and avatar_url is null from public.profiles
  where id = '00000000-0000-0000-0000-0000000009c0'), 'sign-up clamps long name to 80 and drops non-https avatar');
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000009b0', true);
update public.profiles set display_name = 'Okay name', avatar_url = 'https://lh3.googleusercontent.com/a/x' where id = auth.uid();
do $$ begin
  update public.profiles set display_name = repeat('x', 81) where id = auth.uid();
  raise exception 'FAIL: 81-char display name accepted';
exception when check_violation then raise notice 'ok  display_name over 80 chars rejected';
end $$;
do $$ begin
  update public.profiles set avatar_url = 'javascript:alert(1)' where id = auth.uid();
  raise exception 'FAIL: non-https avatar accepted';
exception when check_violation then raise notice 'ok  non-https avatar rejected';
end $$;
do $$ begin
  update public.profiles set is_premium = true where id = auth.uid();
  raise exception 'FAIL: user set own is_premium';
exception when insufficient_privilege then raise notice 'ok  is_premium still not writable by users';
end $$;
reset role;

-- ---------------------------------------------------------------- privacy + cleanup
set local role anon;
do $$ begin
  perform 1 from private.rate_hits;
  raise exception 'FAIL: anon can read rate_hits';
exception when insufficient_privilege or invalid_schema_name then raise notice 'ok  rate_hits not readable by API roles';
end $$;
reset role;
update private.rate_hits set at = now() - interval '3 days' where bucket = 'report';
select private.prune_old_data();
select pg_temp.check(not exists (select 1 from private.rate_hits where bucket = 'report'), 'prune job drops hits older than 2 days');
select pg_temp.check(exists (select 1 from private.rate_hits where bucket = 'event'), 'recent hits kept');

\echo 'ALL 0900 CHECKS PASSED'
rollback;
