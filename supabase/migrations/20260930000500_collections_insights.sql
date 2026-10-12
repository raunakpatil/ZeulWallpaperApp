-- =====================================================================
-- A8 · Collections for the app + insights for the dashboard
-- =====================================================================

-- ---------------------------------------------------------------------
-- get_collections() → collections that contain at least one published wallpaper.
-- Cover = the chosen cover if it's published, otherwise the first item.
-- ---------------------------------------------------------------------
create or replace function public.get_collections()
returns table (
  id                   uuid,
  title                text,
  is_premium           boolean,
  item_count           int,
  cover_storage_key    text,
  cover_blurhash       text,
  cover_dominant_color text
)
language sql stable security definer
set search_path = public, extensions
as $$
  select c.id, c.title, c.is_premium, items.n,
         coalesce(cv.storage_key, first_item.storage_key),
         coalesce(cv.blurhash, first_item.blurhash),
         coalesce(cv.dominant_color, first_item.dominant_color)
  from collections c
  cross join lateral (
    select count(*)::int as n
    from collection_items ci join wallpapers_public v on v.id = ci.wallpaper_id
    where ci.collection_id = c.id
  ) items
  left join wallpapers_public cv on cv.id = c.cover_wallpaper_id
  left join lateral (
    select v.storage_key, v.blurhash, v.dominant_color
    from collection_items ci join wallpapers_public v on v.id = ci.wallpaper_id
    where ci.collection_id = c.id
    order by ci.position, v.id
    limit 1
  ) first_item on true
  where items.n > 0
  order by c.sort_order, c.created_at desc
$$;

-- ---------------------------------------------------------------------
-- get_collection(collection_id, page, page_size) → its published wallpapers, in order.
-- ---------------------------------------------------------------------
create or replace function public.get_collection(collection_id uuid, page int default 0, page_size int default 30)
returns setof public.wallpapers_public
language sql stable security definer
set search_path = public, extensions
as $$
  select v.*
  from collection_items ci
  join wallpapers_public v on v.id = ci.wallpaper_id
  where ci.collection_id = $1
  order by ci.position, v.id
  limit private.page_limit($3) offset private.page_offset($2, $3)
$$;

-- ---------------------------------------------------------------------
-- admin_insights(days) → one JSON document for the dashboard's Insights page.
-- ---------------------------------------------------------------------
create or replace function public.admin_insights(days int default 30)
returns jsonb
language plpgsql stable security definer
set search_path = public, extensions
as $$
declare
  d int := least(greatest(coalesce($1, 30), 1), 365);
begin
  if not is_admin() then
    raise exception 'admin only' using errcode = '42501';
  end if;

  return jsonb_build_object(
    'days', d,
    'totals', (
      select jsonb_build_object(
        'published',  count(*) filter (where is_published and (publish_at is null or publish_at <= now())),
        'scheduled',  count(*) filter (where is_published and publish_at > now()),
        'drafts',     count(*) filter (where not is_published),
        'downloads',  coalesce(sum(download_count), 0),
        'favs',       coalesce(sum(fav_count), 0))
      from wallpapers),
    'users',         (select count(*) from profiles),
    'premium_users', (select count(*) from profiles where is_premium),
    'open_reports',  (select count(*) from reports where status = 'open'),
    'window', (
      select jsonb_build_object(
        'downloads', coalesce(sum(downloads), 0),
        'views',     coalesce(sum(views), 0),
        'applies',   coalesce(sum(applies), 0))
      from wallpaper_daily_stats where day > current_date - d),
    'top_wallpapers', (
      select coalesce(jsonb_agg(to_jsonb(x) order by x.downloads desc, x.views desc), '[]'::jsonb)
      from (
        select w.id, w.title, w.storage_key, w.dominant_color,
               sum(s.downloads)::int as downloads, sum(s.views)::int as views, sum(s.applies)::int as applies
        from wallpaper_daily_stats s join wallpapers w on w.id = s.wallpaper_id
        where s.day > current_date - d
        group by w.id
        order by downloads desc, views desc
        limit 10) x),
    'top_searches', (
      select coalesce(jsonb_agg(to_jsonb(x) order by x.searches desc), '[]'::jsonb)
      from (
        select q, count(*)::int as searches, round(avg(result_count), 1) as avg_results
        from search_log where created_at > now() - make_interval(days => d)
        group by q order by searches desc limit 15) x),
    'zero_result_searches', (
      select coalesce(jsonb_agg(to_jsonb(x) order by x.searches desc), '[]'::jsonb)
      from (
        select q, count(*)::int as searches, max(created_at) as last_searched
        from search_log where result_count = 0 and created_at > now() - make_interval(days => d)
        group by q order by searches desc limit 15) x)
  );
end;
$$;

revoke execute on function public.get_collections()                  from public;
revoke execute on function public.get_collection(uuid, int, int)     from public;
revoke execute on function public.admin_insights(int)                from public, anon;
grant  execute on function public.get_collections()                  to anon, authenticated;
grant  execute on function public.get_collection(uuid, int, int)     to anon, authenticated;
grant  execute on function public.admin_insights(int)                to authenticated;
