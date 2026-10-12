-- =====================================================================
-- A3 · RPCs the app calls (contract section 2)
-- All are SECURITY DEFINER with a fixed search_path. They read only from
-- the published-only view wallpapers_public and do their own validation.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Private helpers (not callable through the API)
-- ---------------------------------------------------------------------

create or replace function private.page_limit(p_page_size int)
returns int language sql immutable set search_path = '' as $$
  select least(greatest(coalesce(p_page_size, 30), 1), 100)
$$;

create or replace function private.page_offset(p_page int, p_page_size int)
returns int language sql immutable set search_path = '' as $$
  select greatest(coalesce(p_page, 0), 0) * private.page_limit(p_page_size)
$$;

create or replace function private.bump_daily_stat(p_wid uuid, p_type text)
returns void language sql security definer set search_path = '' as $$
  insert into public.wallpaper_daily_stats as s (wallpaper_id, day, views, downloads, applies, favs)
  values (
    p_wid, current_date,
    (p_type = 'view')::int,
    (p_type = 'download')::int,
    (p_type = 'apply')::int,
    case p_type when 'fav' then 1 when 'unfav' then -1 else 0 end
  )
  on conflict on constraint wallpaper_daily_stats_pkey do update set
    views     = s.views     + excluded.views,
    downloads = s.downloads + excluded.downloads,
    applies   = s.applies   + excluded.applies,
    favs      = s.favs      + excluded.favs
$$;

-- Adds p_delta to the user's score for every tag of the wallpaper (never below 0)
create or replace function private.bump_affinity(p_uid uuid, p_wid uuid, p_delta numeric)
returns void language sql security definer set search_path = '' as $$
  insert into public.user_tag_affinity as a (user_id, tag_id, score)
  select p_uid, wt.tag_id, greatest(p_delta, 0)
  from public.wallpaper_tags wt
  where wt.wallpaper_id = p_wid
  on conflict on constraint user_tag_affinity_pkey do update set
    score      = greatest(a.score + p_delta, 0),
    updated_at = now()
$$;

create or replace function private.clean_device_id(p_device_id text)
returns text language plpgsql immutable set search_path = '' as $$
declare v text := left(trim(coalesce(p_device_id, '')), 64);
begin
  if v = '' then
    raise exception 'device_id is required' using errcode = '22023';
  end if;
  return v;
end;
$$;

-- ---------------------------------------------------------------------
-- get_section('curated' | 'new' | 'amoled' | 'tablet' | 'premium', page, page_size)
-- Phone sections exclude tablet-only wallpapers; 'tablet' returns tablet + both.
-- ---------------------------------------------------------------------
create or replace function public.get_section(section text, page int default 0, page_size int default 30)
returns setof public.wallpapers_public
language plpgsql stable security definer
set search_path = public, extensions
as $$
declare
  lim int := private.page_limit($3);
  off int := private.page_offset($2, $3);
begin
  case lower($1)
    when 'curated' then
      return query select v.* from wallpapers_public v
        where v.is_curated and v.device_type <> 'tablet'
        order by v.curated_order nulls last, v.published_at desc, v.id
        limit lim offset off;
    when 'new' then
      return query select v.* from wallpapers_public v
        where v.device_type <> 'tablet'
        order by v.published_at desc, v.id
        limit lim offset off;
    when 'amoled' then
      return query select v.* from wallpapers_public v
        where v.is_amoled and v.device_type <> 'tablet'
        order by v.popularity_score desc, v.published_at desc, v.id
        limit lim offset off;
    when 'tablet' then
      return query select v.* from wallpapers_public v
        where v.device_type in ('tablet', 'both')
        order by v.is_curated desc, v.published_at desc, v.id
        limit lim offset off;
    when 'premium' then
      return query select v.* from wallpapers_public v
        where v.is_premium
        order by v.published_at desc, v.id
        limit lim offset off;
    else
      raise exception 'unknown section: %', $1 using errcode = '22023';
  end case;
end;
$$;

-- ---------------------------------------------------------------------
-- get_popular('week' | 'month' | 'all', page, page_size)
-- week/month: downloads*3 + applies*2 + net favs*2 within the window
-- ---------------------------------------------------------------------
create or replace function public.get_popular("range" text default 'week', page int default 0, page_size int default 30)
returns setof public.wallpapers_public
language plpgsql stable security definer
set search_path = public, extensions
as $$
declare
  lim  int := private.page_limit($3);
  off  int := private.page_offset($2, $3);
  days int;
begin
  days := case lower(coalesce($1, 'week')) when 'week' then 7 when 'month' then 30 when 'all' then 0 else -1 end;
  if days < 0 then
    raise exception 'unknown range: % (use week, month or all)', $1 using errcode = '22023';
  end if;

  if days = 0 then
    return query select v.* from wallpapers_public v
      where v.device_type <> 'tablet'
      order by v.download_count desc, v.fav_count desc, v.id
      limit lim offset off;
  else
    return query select v.* from wallpapers_public v
      left join (
        select s.wallpaper_id, sum(s.downloads * 3 + s.applies * 2 + s.favs * 2) as sc
        from wallpaper_daily_stats s
        where s.day > current_date - days
        group by s.wallpaper_id
      ) p on p.wallpaper_id = v.id
      where v.device_type <> 'tablet'
      order by coalesce(p.sc, 0) desc, v.download_count desc, v.id
      limit lim offset off;
  end if;
end;
$$;

-- ---------------------------------------------------------------------
-- search_wallpapers(q, color, device, amoled, category, page, page_size)
--   q        free text: full-text on title/tags/category + typo-tolerant trigram
--   color    colour family (red, blue, black ... see color_family())
--   device   'phone' | 'tablet'
--   category category slug
-- Logs page-0 queries to search_log (for zero-result insights).
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
  if qn is not null and coalesce($6, 0) = 0 then
    insert into search_log (q, result_count) values (qn, n);
  end if;
end;
$$;

-- ---------------------------------------------------------------------
-- search_suggest(q) → up to 8 {label, kind} where kind ∈ tag | category | title
-- ---------------------------------------------------------------------
create or replace function public.search_suggest(q text)
returns table (label text, kind text)
language sql stable security definer
set search_path = public, extensions
as $$
  with qq as (select left(lower(trim(coalesce($1, ''))), 50) as s)
  select x.label, x.kind
  from (
    select t.name as label, 'tag' as kind, 3 as prio,
           (case when starts_with(t.name, qq.s) then 1 else 0 end) + similarity(t.name, qq.s) as score
    from tags t, qq
    where qq.s <> ''
      and (starts_with(t.name, qq.s) or t.name % qq.s)
      and exists (select 1 from wallpaper_tags wt join wallpapers_public v on v.id = wt.wallpaper_id
                  where wt.tag_id = t.id)
    union all
    select c.name, 'category', 2,
           (case when starts_with(lower(c.name), qq.s) then 1 else 0 end) + similarity(lower(c.name), qq.s)
    from categories c, qq
    where qq.s <> ''
      and (starts_with(lower(c.name), qq.s) or lower(c.name) % qq.s)
    union all
    select min(v.title), 'title', 1,
           max((case when starts_with(lower(v.title), qq.s) then 1 else 0 end) + similarity(lower(v.title), qq.s))
    from wallpapers_public v, qq
    where qq.s <> ''
      and (starts_with(lower(v.title), qq.s) or lower(v.title) % qq.s)
    group by lower(v.title)
  ) x
  order by x.score desc, x.prio desc, x.label
  limit 8
$$;

-- ---------------------------------------------------------------------
-- get_for_you(page, page_size, seed_tags)
--   Signed-in users with affinity → ranked by summed tag affinity (+ a small
--   popularity term for discovery), excluding wallpapers already favourited,
--   downloaded or applied.
--   Guests may pass seed_tags (tag slugs picked in onboarding).
--   No signal at all → Popular this week.
-- ---------------------------------------------------------------------
create or replace function public.get_for_you(page int default 0, page_size int default 30, seed_tags text[] default null)
returns setof public.wallpapers_public
language plpgsql stable security definer
set search_path = public, extensions
as $$
declare
  lim     int  := private.page_limit($2);
  off     int  := private.page_offset($1, $2);
  uid     uuid := auth.uid();
  seeds   text[] := (coalesce($3, '{}'::text[]))[1:20];
  has_aff boolean := false;
begin
  if uid is not null then
    has_aff := exists (select 1 from user_tag_affinity a where a.user_id = uid and a.score > 0);
  end if;

  if not has_aff and cardinality(seeds) = 0 then
    return query select * from get_popular('week', $1, $2);
    return;
  end if;

  return query
  with aff as (
    select a.tag_id, a.score from user_tag_affinity a
      where has_aff and a.user_id = uid and a.score > 0
    union all
    select t.id, 10::numeric from tags t
      where not has_aff and t.slug = any (seeds)
  ),
  scored as (
    select wt.wallpaper_id, sum(aff.score) as s
    from wallpaper_tags wt join aff on aff.tag_id = wt.tag_id
    group by wt.wallpaper_id
  )
  select v.* from wallpapers_public v
  left join scored sc on sc.wallpaper_id = v.id
  where v.device_type <> 'tablet'
    and (uid is null or not exists (
          select 1 from favorites f where f.user_id = uid and f.wallpaper_id = v.id))
    and (uid is null or not exists (
          select 1 from user_events e
          where e.user_id = uid and e.wallpaper_id = v.id and e.type in ('download', 'apply')))
  order by coalesce(sc.s, 0) + 0.5 * ln(1 + greatest(v.popularity_score, 0) + v.download_count) desc,
           v.published_at desc, v.id
  limit lim offset off;
end;
$$;

-- ---------------------------------------------------------------------
-- set_style_preferences(tag_slugs) — onboarding picks for a signed-in user
-- ---------------------------------------------------------------------
create or replace function public.set_style_preferences(tag_slugs text[])
returns void
language plpgsql volatile security definer
set search_path = public, extensions
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'sign-in required' using errcode = '42501';
  end if;
  insert into user_tag_affinity as a (user_id, tag_id, score)
  select uid, t.id, 10
  from tags t
  where t.slug = any ((coalesce($1, '{}'::text[]))[1:20])
  on conflict on constraint user_tag_affinity_pkey do update set
    score = greatest(a.score, 10), updated_at = now();
end;
$$;

-- ---------------------------------------------------------------------
-- record_event(wallpaper_id, type, device_id)
--   type: 'view' | 'apply' | 'fav' | 'unfav'   (downloads → increment_download)
--   Rate-limited per device: view 30 min, apply 1 min, fav/unfav 5 s.
--   Guests are fine (user_id null); signed-in users also update tag affinity.
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
-- increment_download(wallpaper_id, device_id) → new download_count
--   Same device + wallpaper counts once per hour; max 200 counted downloads
--   per device per day. Still returns the current count when not counted.
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
           and e.created_at > now() - interval '1 day') >= 200 then
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
-- delete_my_account() — required by Play policy. Cascades to profile,
-- favourites, unlocks, affinity; events are kept anonymised (user_id → null).
-- ---------------------------------------------------------------------
create or replace function public.delete_my_account()
returns void
language plpgsql volatile security definer
set search_path = ''
as $$
declare uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'sign-in required' using errcode = '42501';
  end if;
  delete from auth.users where id = uid;
end;
$$;

-- ---------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------
revoke all on all functions in schema private from public, anon, authenticated;

revoke execute on function public.get_section(text, int, int)                                   from public;
revoke execute on function public.get_popular(text, int, int)                                   from public;
revoke execute on function public.search_wallpapers(text, text, text, boolean, text, int, int)  from public;
revoke execute on function public.search_suggest(text)                                          from public;
revoke execute on function public.get_for_you(int, int, text[])                                 from public;
revoke execute on function public.record_event(uuid, text, text)                                from public;
revoke execute on function public.increment_download(uuid, text)                                from public;
revoke execute on function public.set_style_preferences(text[])                                 from public, anon;
revoke execute on function public.delete_my_account()                                           from public, anon;

grant execute on function public.get_section(text, int, int)                                  to anon, authenticated;
grant execute on function public.get_popular(text, int, int)                                  to anon, authenticated;
grant execute on function public.search_wallpapers(text, text, text, boolean, text, int, int) to anon, authenticated;
grant execute on function public.search_suggest(text)                                         to anon, authenticated;
grant execute on function public.get_for_you(int, int, text[])                                to anon, authenticated;
grant execute on function public.record_event(uuid, text, text)                               to anon, authenticated;
grant execute on function public.increment_download(uuid, text)                               to anon, authenticated;
grant execute on function public.set_style_preferences(text[])                                to authenticated;
grant execute on function public.delete_my_account()                                          to authenticated;
