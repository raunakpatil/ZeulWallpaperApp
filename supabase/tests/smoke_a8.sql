-- Smoke test for A8 (collections RPCs + insights). Runs in a transaction and rolls back.
\set QUIET on
begin;

create or replace function pg_temp.check(cond boolean, label text) returns void language plpgsql as $$
begin
  if cond is not true then raise exception 'FAIL: %', label; end if;
  raise notice 'ok  %', label;
end $$;
grant execute on function pg_temp.check(boolean, text) to anon, authenticated;

insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a8', 'admin8@test.dev'),
  ('00000000-0000-0000-0000-0000000000b8', 'user8@test.dev');
update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000a8';

insert into public.wallpapers (id, title, storage_key, is_published, blurhash, dominant_color) values
  ('88888888-0000-0000-0000-000000000001', 'Neon A', 'w/a8n001', true,  'LAAAA', '#ff00aa'),
  ('88888888-0000-0000-0000-000000000002', 'Neon B', 'w/a8n002', true,  'LBBBB', '#00ffaa'),
  ('88888888-0000-0000-0000-000000000003', 'Draft',  'w/a8n003', false, 'LCCCC', '#000000');

insert into public.collections (id, title, cover_wallpaper_id, sort_order) values
  ('c8c8c8c8-0000-0000-0000-000000000001', 'Neon Tokyo', '88888888-0000-0000-0000-000000000003', 1),  -- cover is a draft
  ('c8c8c8c8-0000-0000-0000-000000000002', 'Empty pack', null, 2),
  ('c8c8c8c8-0000-0000-0000-000000000003', 'Drafts only', null, 3);
insert into public.collection_items (collection_id, wallpaper_id, position) values
  ('c8c8c8c8-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000002', 1),
  ('c8c8c8c8-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 2),
  ('c8c8c8c8-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000003', 3),
  ('c8c8c8c8-0000-0000-0000-000000000003', '88888888-0000-0000-0000-000000000003', 1);

insert into public.wallpaper_daily_stats (wallpaper_id, day, views, downloads) values
  ('88888888-0000-0000-0000-000000000001', current_date, 40, 7),
  ('88888888-0000-0000-0000-000000000002', current_date, 10, 2);
insert into public.search_log (q, result_count) values ('neon', 2), ('neon', 2), ('anime', 0), ('anime', 0), ('anime', 0);
insert into public.reports (wallpaper_id, reason) values ('88888888-0000-0000-0000-000000000001', 'test');

-- guest
set local role anon;
select pg_temp.check((select count(*) from get_collections() where title in ('Neon Tokyo','Empty pack','Drafts only')) = 1,
                     'only collections with published items are listed');
select pg_temp.check((select item_count from get_collections() where title = 'Neon Tokyo') = 2, 'item_count ignores drafts');
select pg_temp.check((select cover_storage_key from get_collections() where title = 'Neon Tokyo') = 'w/a8n002',
                     'unpublished cover falls back to the first item');
select pg_temp.check((select array_agg(title) from get_collection('c8c8c8c8-0000-0000-0000-000000000001')) = '{"Neon B","Neon A"}',
                     'collection items in position order, drafts hidden');
select pg_temp.check((select count(*) from get_collection('c8c8c8c8-0000-0000-0000-000000000001', 1, 1)) = 1, 'collection pagination');
do $$ begin
  perform admin_insights();
  raise exception 'FAIL: anon read insights';
exception when insufficient_privilege then raise notice 'ok  anon cannot read insights';
end $$;
reset role;

-- regular user
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b8', true);
do $$ begin
  perform admin_insights();
  raise exception 'FAIL: user read insights';
exception when insufficient_privilege then raise notice 'ok  non-admin cannot read insights';
end $$;

-- admin
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a8', true);
select pg_temp.check((admin_insights(7) -> 'totals' ->> 'drafts')::int >= 1, 'insights totals');
select pg_temp.check((admin_insights(7) -> 'top_wallpapers' -> 0 ->> 'title') = 'Neon A', 'top wallpaper by downloads');
select pg_temp.check((admin_insights(7) -> 'zero_result_searches' -> 0 ->> 'q') = 'anime', 'zero-result searches');
select pg_temp.check((admin_insights(7) -> 'zero_result_searches' -> 0 ->> 'searches')::int = 3, 'zero-result counts');
select pg_temp.check((admin_insights(7) ->> 'open_reports')::int >= 1, 'open reports counted');
select pg_temp.check((admin_insights(9999) ->> 'days')::int = 365, 'window clamped to 365 days');
reset role;

\echo 'ALL A8 CHECKS PASSED'
rollback;
