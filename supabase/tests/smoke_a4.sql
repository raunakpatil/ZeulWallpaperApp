-- Smoke test for A4 (scheduled jobs). Runs in a transaction and rolls back.
\set QUIET on
begin;

create or replace function pg_temp.check(cond boolean, label text) returns void language plpgsql as $$
begin
  if cond is not true then raise exception 'FAIL: %', label; end if;
  raise notice 'ok  %', label;
end $$;

insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a4', 'admin4@test.dev'),
  ('00000000-0000-0000-0000-0000000000b4', 'user4@test.dev');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000a4';

insert into public.wallpapers (id, title, storage_key, is_published, download_count) values
  ('44444444-0000-0000-0000-000000000001', 'Hot this week', 'w/a4hot', true, 10),
  ('44444444-0000-0000-0000-000000000002', 'Old classic',   'w/a4old', true, 1000),
  ('44444444-0000-0000-0000-000000000003', 'Quiet',         'w/a4qui', true, 0);

insert into public.wallpaper_daily_stats (wallpaper_id, day, views, downloads, applies, favs) values
  ('44444444-0000-0000-0000-000000000001', current_date,      100, 50, 5, 3),   -- 150+10+6+10 = 176
  ('44444444-0000-0000-0000-000000000001', current_date - 30, 0,  999, 0, 0),   -- outside the 7-day window
  ('44444444-0000-0000-0000-000000000002', current_date - 500, 0,   1, 0, 0);   -- older than retention

select pg_temp.check(private.recompute_popularity() >= 2, 'recompute updates changed rows');
select pg_temp.check((select popularity_score from wallpapers where storage_key = 'w/a4hot') = 177, 'hot score = 7-day activity + downloads×0.1 (176 + 1)');
select pg_temp.check((select popularity_score from wallpapers where storage_key = 'w/a4old') = 100, 'old score = all-time downloads×0.1');
select pg_temp.check((select popularity_score from wallpapers where storage_key = 'w/a4qui') = 0,   'quiet score = 0');
select pg_temp.check(private.recompute_popularity() = 0, 'second run changes nothing');

-- affinity decay
insert into public.tags (name) values ('a4tag'), ('a4faded');
insert into public.user_tag_affinity (user_id, tag_id, score)
select '00000000-0000-0000-0000-0000000000b4', id, case name when 'a4tag' then 10 else 0.5 end from public.tags where name in ('a4tag','a4faded');
select private.decay_affinity();
select pg_temp.check((select score from user_tag_affinity a join tags t on t.id = a.tag_id where t.name = 'a4tag') = 9.8, 'affinity decays ×0.98');
select pg_temp.check(not exists (select 1 from user_tag_affinity a join tags t on t.id = a.tag_id where t.name = 'a4faded'), 'tiny affinity removed');

-- pruning
insert into public.user_events (device_id, wallpaper_id, type, created_at) values
  ('d', '44444444-0000-0000-0000-000000000001', 'view', now() - interval '100 days'),
  ('d', '44444444-0000-0000-0000-000000000001', 'view', now() - interval '1 day');
insert into public.search_log (q, result_count, created_at) values ('old', 0, now() - interval '200 days'), ('new', 0, now());
select private.prune_old_data();
select pg_temp.check((select count(*) from user_events where device_id = 'd') = 1, 'events older than 90 days pruned');
select pg_temp.check(not exists (select 1 from wallpaper_daily_stats where day < current_date - 400), 'stats older than 400 days pruned');
select pg_temp.check((select count(*) from search_log where q in ('old','new')) = 1, 'search logs older than 180 days pruned');

-- permissions
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b4', true);
do $$ begin
  perform admin_recompute_popularity();
  raise exception 'FAIL: non-admin recomputed';
exception when insufficient_privilege then raise notice 'ok  non-admin cannot recompute';
end $$;
do $$ begin
  perform private.recompute_popularity();
  raise exception 'FAIL: user called private function';
exception when insufficient_privilege then raise notice 'ok  private functions not callable by users';
end $$;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a4', true);
select pg_temp.check(admin_recompute_popularity() >= 0, 'admin can recompute');
reset role;

select pg_temp.check((select count(*) from cron.job where jobname in ('recompute-popularity','decay-affinity','prune-old-data')) = 3, 'three cron jobs scheduled');

\echo 'ALL A4 CHECKS PASSED'
rollback;
