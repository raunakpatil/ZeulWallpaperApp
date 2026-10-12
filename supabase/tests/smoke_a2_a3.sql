-- Smoke test for A2 (schema/RLS) + A3 (RPCs).
-- Runs inside one transaction and rolls back, so it leaves no data behind.
-- Run against a local DB:  psql -v ON_ERROR_STOP=1 -f supabase/tests/smoke_a2_a3.sql
\set QUIET on
begin;

create or replace function pg_temp.check(cond boolean, label text) returns void language plpgsql as $$
begin
  if cond is not true then raise exception 'FAIL: %', label; end if;
  raise notice 'ok  %', label;
end $$;

create or replace function pg_temp.as_user(uid uuid, r text) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', r)::text, true);
  perform set_config('request.jwt.claim.sub', coalesce(uid::text, ''), true);
  execute format('set local role %I', r);
end $$;
grant execute on function pg_temp.check(boolean, text) to anon, authenticated;
grant execute on function pg_temp.as_user(uuid, text) to anon, authenticated, postgres;

-- ---------- fixtures (as postgres) ----------
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', 'admin@test.dev', '{"full_name":"Admin"}'),
  ('00000000-0000-0000-0000-00000000000b', 'user@test.dev',  '{}');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-00000000000a';

select pg_temp.check((select display_name from public.profiles where id = '00000000-0000-0000-0000-00000000000b') = 'user',
                     'profile auto-created on signup');

insert into public.categories (name) values ('Space'), ('Nature') on conflict (name) do nothing;
insert into public.tags (name) values ('  Galaxy '), ('Stars'), ('Mountains'), ('Dark');
select pg_temp.check((select slug from public.tags where name = 'galaxy') = 'galaxy', 'tag normalised + slugged');

insert into public.wallpapers (id, title, storage_key, width, height, device_type, is_amoled, is_curated, curated_order,
                               is_published, dominant_color, category_id, is_premium, publish_at) values
  ('11111111-0000-0000-0000-000000000001', 'Purple Galaxy',   'w/g1', 1440, 3200, 'phone',  true,  true, 2, true, '#000000', (select id from categories where slug='space'),  false, null),
  ('11111111-0000-0000-0000-000000000002', 'Starry Night Sky','w/g2', 1440, 3200, 'both',   false, true, 1, true, '#1a2b6d', (select id from categories where slug='space'),  false, null),
  ('11111111-0000-0000-0000-000000000003', 'Alpine Mountains','w/m1', 2560, 1600, 'tablet', false, false, null, true, '#3a7d44', (select id from categories where slug='nature'), false, null),
  ('11111111-0000-0000-0000-000000000004', 'Draft Wallpaper', 'w/d1', 1440, 3200, 'phone',  false, false, null, false,'#ff0000', null, false, null),
  ('11111111-0000-0000-0000-000000000005', 'Future Drop',     'w/f1', 1440, 3200, 'phone',  false, false, null, true, '#ff0000', null, false, now() + interval '1 day'),
  ('11111111-0000-0000-0000-000000000006', 'Gold Nebula',     'w/p1', 1440, 3200, 'phone',  false, false, null, true, '#d4a017', (select id from categories where slug='space'),  true,  null);

insert into public.wallpaper_tags select '11111111-0000-0000-0000-000000000001', id from tags where name in ('galaxy','dark');
insert into public.wallpaper_tags select '11111111-0000-0000-0000-000000000002', id from tags where name in ('stars');
insert into public.wallpaper_tags select '11111111-0000-0000-0000-000000000003', id from tags where name in ('mountains');
insert into public.wallpaper_tags select '11111111-0000-0000-0000-000000000006', id from tags where name in ('galaxy');

select pg_temp.check((select aspect_ratio from wallpapers where storage_key='w/g1') = 0.45, 'aspect_ratio generated');
select pg_temp.check((select color_family from wallpapers where storage_key='w/g1') = 'black', 'color_family black');
select pg_temp.check((select color_family from wallpapers where storage_key='w/g2') = 'blue',  'color_family blue');
select pg_temp.check((select color_family from wallpapers where storage_key='w/m1') = 'green', 'color_family green');
select pg_temp.check((select search_tsv @@ to_tsquery('english','galaxy') from wallpapers where storage_key='w/g1'), 'search_tsv includes tags');

-- ---------- guest (anon) ----------
select pg_temp.as_user(null, 'anon');

select pg_temp.check((select count(*) from wallpapers) = 4, 'anon sees only published + due wallpapers (4)');
select pg_temp.check((select count(*) from wallpapers_public) = 4, 'view hides drafts + scheduled');
select pg_temp.check((select tags from wallpapers_public where id='11111111-0000-0000-0000-000000000001') = '{dark,galaxy}', 'view aggregates tags');

select pg_temp.check((select array_agg(title) from get_section('curated')) = '{"Starry Night Sky","Purple Galaxy"}', 'curated ordered by curated_order');
select pg_temp.check((select count(*) from get_section('amoled')) = 1, 'amoled section');
select pg_temp.check((select count(*) from get_section('tablet')) = 2, 'tablet section = tablet + both');
select pg_temp.check(not exists (select 1 from get_section('new') where device_type = 'tablet'), 'phone sections exclude tablet-only');
select pg_temp.check((select count(*) from get_section('premium')) = 1, 'premium section');
select pg_temp.check((select count(*) from get_section('new', 0, 2)) = 2 and (select count(*) from get_section('new', 1, 2)) = 1, 'pagination');

do $$ begin
  perform get_section('bogus');
  raise exception 'FAIL: bad section accepted';
exception when invalid_parameter_value then raise notice 'ok  bad section rejected';
end $$;

select pg_temp.check((select title from search_wallpapers('galaxy') limit 1) in ('Purple Galaxy','Gold Nebula'), 'search by tag');
select pg_temp.check(exists (select 1 from search_wallpapers('galxy') where title = 'Purple Galaxy'), 'search typo-tolerant');
select pg_temp.check((select count(*) from search_wallpapers(null, 'black')) = 1, 'filter by colour');
select pg_temp.check((select count(*) from search_wallpapers(null, null, 'tablet')) = 2, 'filter by device');
select pg_temp.check((select count(*) from search_wallpapers(null, null, null, null, 'space')) = 3, 'filter by category');
select pg_temp.check((select count(*) from search_wallpapers('zzqqxx')) = 0, 'no results for junk');
select pg_temp.check(exists (select 1 from search_suggest('gal') where label = 'galaxy' and kind = 'tag'), 'suggest tag by prefix');
select pg_temp.check(exists (select 1 from search_suggest('spa') where kind = 'category'), 'suggest category');
select pg_temp.check((select count(*) from search_suggest('')) = 0, 'suggest empty');

-- events + downloads (guest)
select increment_download('11111111-0000-0000-0000-000000000002', 'dev-1');
select pg_temp.check(increment_download('11111111-0000-0000-0000-000000000002', 'dev-1') = 1, 'download rate-limited per device/hour');
select pg_temp.check(increment_download('11111111-0000-0000-0000-000000000002', 'dev-2') = 2, 'other device counts');
select record_event('11111111-0000-0000-0000-000000000001', 'view', 'dev-1');
select record_event('11111111-0000-0000-0000-000000000001', 'view', 'dev-1');

do $$ begin
  perform record_event('11111111-0000-0000-0000-000000000001', 'download', 'dev-1');
  raise exception 'FAIL: download via record_event accepted';
exception when invalid_parameter_value then raise notice 'ok  record_event rejects download';
end $$;
do $$ begin
  perform increment_download('11111111-0000-0000-0000-000000000004', 'dev-1');
  raise exception 'FAIL: draft download accepted';
exception when no_data_found then raise notice 'ok  cannot download unpublished';
end $$;
do $$ begin
  perform increment_download('11111111-0000-0000-0000-000000000002', '  ');
  raise exception 'FAIL: blank device accepted';
exception when invalid_parameter_value then raise notice 'ok  device_id required';
end $$;

select pg_temp.check((select array_agg(title) from get_popular('week', 0, 1)) = '{"Starry Night Sky"}', 'popular week');
select pg_temp.check((select title from get_popular('all') limit 1) = 'Starry Night Sky', 'popular all');
select pg_temp.check((select count(*) from get_for_you()) = 3, 'for-you guest falls back to popular');
select pg_temp.check((select title from get_for_you(0, 30, '{galaxy}') limit 1) = 'Purple Galaxy', 'for-you guest seed tags');

-- anon must not read/write private data
select pg_temp.check((select count(*) from user_events) = 0, 'anon cannot read events');
select pg_temp.check((select count(*) from profiles) = 0, 'anon cannot read profiles');
do $$ begin
  insert into wallpapers (title, storage_key) values ('hack', 'w/x');
  raise exception 'FAIL: anon inserted wallpaper';
exception when insufficient_privilege then raise notice 'ok  anon cannot insert wallpaper';
end $$;
do $$ begin
  perform delete_my_account();
  raise exception 'FAIL: anon can call delete_my_account';
exception when insufficient_privilege then raise notice 'ok  anon cannot call delete_my_account';
end $$;
insert into reports (wallpaper_id, reason, device_id) values ('11111111-0000-0000-0000-000000000001', 'copyright', 'dev-1');
select pg_temp.check(true, 'anon can file a report');
reset role;

-- ---------- regular user ----------
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b', 'authenticated');
insert into favorites (user_id, wallpaper_id) values ('00000000-0000-0000-0000-00000000000b', '11111111-0000-0000-0000-000000000006');
select record_event('11111111-0000-0000-0000-000000000006', 'fav', 'dev-3');
select pg_temp.check((select fav_count from wallpapers_public where id = '11111111-0000-0000-0000-000000000006') = 1, 'fav_count trigger');
select pg_temp.check((select count(*) from user_tag_affinity) = 1, 'fav bumps own tag affinity');
select pg_temp.check((select title from get_for_you() limit 1) = 'Purple Galaxy', 'for-you ranks by affinity');
select pg_temp.check(not exists (select 1 from get_for_you() where title = 'Gold Nebula'), 'for-you excludes favourited');
select set_style_preferences('{mountains,stars}');
select pg_temp.check((select count(*) from user_tag_affinity) = 3, 'style preferences stored');
select pg_temp.check((select count(*) from profiles) = 1, 'user sees only own profile');
update profiles set display_name = 'Tester' where id = '00000000-0000-0000-0000-00000000000b';
do $$ begin
  update profiles set role = 'admin' where id = '00000000-0000-0000-0000-00000000000b';
  raise exception 'FAIL: user escalated to admin';
exception when insufficient_privilege then raise notice 'ok  user cannot change own role';
end $$;
do $$ begin
  insert into favorites (user_id, wallpaper_id) values ('00000000-0000-0000-0000-00000000000a', '11111111-0000-0000-0000-000000000001');
  raise exception 'FAIL: user wrote another user''s favourite';
exception when insufficient_privilege then raise notice 'ok  user cannot write others'' favourites';
end $$;
select pg_temp.check((select count(*) from wallpapers) = 4, 'user still sees only published');
select pg_temp.check((select count(*) from reports) = 0, 'user cannot read reports');
reset role;

-- ---------- admin ----------
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'authenticated');
select pg_temp.check((select count(*) from wallpapers) = 6, 'admin sees drafts');
insert into tags (name) values ('Neon');
insert into wallpapers (id, title, storage_key, is_published, dominant_color) values ('11111111-0000-0000-0000-000000000007', 'Neon City', 'w/n1', true, '#ff00aa');
insert into wallpaper_tags select '11111111-0000-0000-0000-000000000007', id from tags where name = 'neon';
select pg_temp.check(exists (select 1 from search_wallpapers('neon') where title = 'Neon City'), 'admin insert + tag → searchable');
update tags set name = 'cyberpunk' where name = 'neon';
select pg_temp.check(exists (select 1 from search_wallpapers('cyberpunk') where title = 'Neon City'), 'tag rename refreshes search');
update app_config set value = '"https://cdn.example.dev"' where key = 'cdn_base_url';
select pg_temp.check((select count(*) from reports) = 1, 'admin reads reports');
select pg_temp.check((select count(*) from search_log where result_count = 0) >= 1, 'zero-result searches logged');
reset role;

-- ---------- delete account ----------
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b', 'authenticated');
select delete_my_account();
reset role;
select pg_temp.check(not exists (select 1 from auth.users where id = '00000000-0000-0000-0000-00000000000b'), 'account deleted');
select pg_temp.check(not exists (select 1 from favorites where user_id = '00000000-0000-0000-0000-00000000000b'), 'favourites cascaded');
select pg_temp.check((select fav_count from wallpapers where storage_key = 'w/p1') = 0, 'fav_count decremented on cascade');
select pg_temp.check(exists (select 1 from user_events where user_id is null and device_id = 'dev-3'), 'events kept anonymised');

\echo 'ALL CHECKS PASSED'
rollback;
