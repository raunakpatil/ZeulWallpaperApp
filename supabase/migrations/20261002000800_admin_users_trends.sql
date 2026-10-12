-- =====================================================================
-- Dashboard UX: daily trend series + user list (admin only)
-- =====================================================================

-- admin_daily_totals(days) → one row per day (zeros included) for the trend chart.
create or replace function public.admin_daily_totals(days int default 30)
returns table (day date, views int, downloads int, applies int, favs int)
language plpgsql stable security definer
set search_path = public, extensions
as $$
declare d int := least(greatest(coalesce($1, 30), 1), 365);
begin
  if not is_admin() then
    raise exception 'admin only' using errcode = '42501';
  end if;
  return query
    select g.day::date,
           coalesce(sum(s.views), 0)::int,
           coalesce(sum(s.downloads), 0)::int,
           coalesce(sum(s.applies), 0)::int,
           coalesce(sum(s.favs), 0)::int
    from generate_series(current_date - (d - 1), current_date, interval '1 day') as g(day)
    left join wallpaper_daily_stats s on s.day = g.day::date
    group by g.day
    order by g.day;
end;
$$;

-- admin_list_users(search, page, page_size) → accounts with email, plan and activity, newest first.
create or replace function public.admin_list_users(search text default null, page int default 0, page_size int default 50)
returns table (
  id uuid, email text, display_name text, avatar_url text, role text,
  is_premium boolean, premium_until timestamptz, created_at timestamptz, last_sign_in_at timestamptz,
  provider text, favorites int, unlocks int, total_count bigint
)
language plpgsql stable security definer
set search_path = public, extensions
as $$
declare q text := nullif(trim(coalesce($1, '')), '');
begin
  if not is_admin() then
    raise exception 'admin only' using errcode = '42501';
  end if;
  return query
    select p.id, u.email::text, p.display_name, p.avatar_url, p.role, p.is_premium, p.premium_until,
           p.created_at, u.last_sign_in_at,
           coalesce(u.raw_app_meta_data ->> 'provider', 'email'),
           (select count(*)::int from favorites f where f.user_id = p.id),
           (select count(*)::int from unlocks x where x.user_id = p.id),
           count(*) over ()
    from profiles p
    join auth.users u on u.id = p.id
    where q is null or u.email ilike '%' || q || '%' or p.display_name ilike '%' || q || '%'
    order by p.created_at desc
    limit least(greatest(coalesce($3, 50), 1), 200)
    offset greatest(coalesce($2, 0), 0) * least(greatest(coalesce($3, 50), 1), 200);
end;
$$;

revoke execute on function public.admin_daily_totals(int)            from public, anon;
revoke execute on function public.admin_list_users(text, int, int)   from public, anon;
grant  execute on function public.admin_daily_totals(int)            to authenticated;
grant  execute on function public.admin_list_users(text, int, int)   to authenticated;
