-- =====================================================================
-- A4 · Scheduled maintenance jobs (pg_cron, free on Supabase)
--   hourly  : recompute wallpapers.popularity_score
--   daily   : fade old tag affinity, prune old events / stats / search logs
-- Keeps the "For You" and AMOLED ordering fresh and the DB under the 500 MB free tier.
-- =====================================================================

create extension if not exists pg_cron;

-- popularity_score = last 7 days (downloads×3 + applies×2 + net favs×2 + views×0.1)
--                  + all-time downloads×0.1   (roadmap section 4)
-- Only rows whose score actually changes are updated.
create or replace function private.recompute_popularity()
returns int
language plpgsql security definer
set search_path = ''
as $$
declare n int;
begin
  with recent as (
    select s.wallpaper_id,
           sum(s.downloads * 3 + s.applies * 2 + s.favs * 2 + s.views * 0.1) as score
    from public.wallpaper_daily_stats s
    where s.day > current_date - 7
    group by s.wallpaper_id
  ),
  target as (
    select w.id,
           round(coalesce(r.score, 0) + w.download_count * 0.1, 3) as score
    from public.wallpapers w
    left join recent r on r.wallpaper_id = w.id
  )
  update public.wallpapers w
     set popularity_score = t.score
    from target t
   where w.id = t.id
     and w.popularity_score is distinct from t.score;
  get diagnostics n = row_count;
  return n;
end;
$$;

-- Tag affinity slowly fades (×0.98 per day ≈ half-life of 34 days) so For You follows current taste.
create or replace function private.decay_affinity()
returns void
language sql security definer
set search_path = ''
as $$
  update public.user_tag_affinity set score = round(score * 0.98, 4) where score > 0;
  delete from public.user_tag_affinity where score < 0.5;
$$;

-- Retention: raw events 90 days (rate limits need minutes; "already seen" in For You uses them),
-- per-day stats 400 days (Popular only needs 30), search logs 180 days.
create or replace function private.prune_old_data()
returns void
language sql security definer
set search_path = ''
as $$
  delete from public.user_events           where created_at < now() - interval '90 days';
  delete from public.wallpaper_daily_stats where day        < current_date - 400;
  delete from public.search_log            where created_at < now() - interval '180 days';
$$;

revoke all on function private.recompute_popularity() from public, anon, authenticated;
revoke all on function private.decay_affinity()       from public, anon, authenticated;
revoke all on function private.prune_old_data()       from public, anon, authenticated;

-- Admins can trigger a recompute from the dashboard (e.g. right after seeding content).
create or replace function public.admin_recompute_popularity()
returns int
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_admin() then
    raise exception 'admin only' using errcode = '42501';
  end if;
  return private.recompute_popularity();
end;
$$;
revoke execute on function public.admin_recompute_popularity() from public, anon;
grant execute on function public.admin_recompute_popularity() to authenticated;

-- (Re)schedule. cron.schedule with a job name upserts, so this migration is safe to re-run.
select cron.schedule('recompute-popularity', '7 * * * *',  $$select private.recompute_popularity()$$);
select cron.schedule('decay-affinity',       '15 3 * * *', $$select private.decay_affinity()$$);
select cron.schedule('prune-old-data',       '30 3 * * *', $$select private.prune_old_data()$$);

-- Fill scores right away instead of waiting for the first hourly run.
select private.recompute_popularity();
