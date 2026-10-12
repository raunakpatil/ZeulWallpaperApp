-- =====================================================================
-- User uploads (Raunak, 2026-10-11): signed-in users publish wallpapers from the app.
-- They go live straight away (no review queue), so the guardrails live here:
--   * uploader_id credits the user and lets them list/delete their own uploads
--   * profiles.upload_banned blocks abusers (admins set it in the dashboard)
--   * app_config.user_uploads: on/off switch, uploads per user per day, reports before auto-hide
--   * a user upload is hidden automatically once enough different people report it
-- The upload itself goes through the `user-upload` edge function (service role), never PostgREST.
-- =====================================================================

alter table public.wallpapers
  add column if not exists uploader_id uuid references auth.users (id) on delete set null;
create index if not exists wallpapers_uploader_idx
  on public.wallpapers (uploader_id, created_at desc) where uploader_id is not null;

-- Not in the column grant for users (only display_name, avatar_url are), so only admins/service role write it.
alter table public.profiles
  add column if not exists upload_banned boolean not null default false;

insert into public.app_config (key, value) values
  ('user_uploads', '{"enabled": true, "daily_limit": 5, "hide_after_reports": 3}')
on conflict (key) do nothing;

-- ---------------------------------------------------------------------
-- Auto-hide: once `hide_after_reports` different reporters have open reports on a user upload,
-- unpublish it until an admin looks at it (Reports page). Admin uploads are never auto-hidden.
-- ---------------------------------------------------------------------
create or replace function private.user_upload_auto_hide()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  v_uploader  uuid;
  v_threshold int;
  v_reporters int;
begin
  select w.uploader_id into v_uploader from public.wallpapers w where w.id = new.wallpaper_id;
  if v_uploader is null then return new; end if;

  select coalesce((c.value ->> 'hide_after_reports')::int, 3) into v_threshold
  from public.app_config c where c.key = 'user_uploads';
  v_threshold := greatest(coalesce(v_threshold, 3), 1);

  select count(distinct coalesce(r.user_id::text, r.device_id, r.id::text)) into v_reporters
  from public.reports r
  where r.wallpaper_id = new.wallpaper_id and r.status = 'open';

  if v_reporters >= v_threshold then
    update public.wallpapers set is_published = false
    where id = new.wallpaper_id and is_published;
  end if;
  return new;
end;
$$;

drop trigger if exists reports_user_upload_auto_hide on public.reports;
create trigger reports_user_upload_auto_hide after insert on public.reports
  for each row execute function private.user_upload_auto_hide();

-- ---------------------------------------------------------------------
-- The signed-in user's own uploads, including hidden ones (so they can see why one vanished).
-- ---------------------------------------------------------------------
create or replace function public.my_uploads()
returns table (
  id uuid,
  title text,
  storage_key text,
  width int,
  height int,
  blurhash text,
  dominant_color text,
  is_live boolean,
  download_count int,
  fav_count int,
  created_at timestamptz
)
language sql stable security definer set search_path = '' as $$
  select w.id, w.title, w.storage_key, w.width, w.height, w.blurhash, w.dominant_color,
         (w.is_published and (w.publish_at is null or w.publish_at <= now())) as is_live,
         w.download_count, w.fav_count, w.created_at
  from public.wallpapers w
  where auth.uid() is not null and w.uploader_id = auth.uid()
  order by w.created_at desc
  limit 200;
$$;
revoke all on function public.my_uploads() from public, anon;
grant execute on function public.my_uploads() to authenticated;

-- ---------------------------------------------------------------------
-- Dashboard: Users page shows upload counts and a "can upload" switch.
-- ---------------------------------------------------------------------
drop function if exists public.admin_list_users(text, int, int);
create function public.admin_list_users(search text default null, page int default 0, page_size int default 50)
returns table (
  id uuid, email text, display_name text, avatar_url text, role text,
  is_premium boolean, premium_until timestamptz, created_at timestamptz, last_sign_in_at timestamptz,
  provider text, favorites int, unlocks int, uploads int, upload_banned boolean, total_count bigint
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
           (select count(*)::int from wallpapers w where w.uploader_id = p.id),
           p.upload_banned,
           count(*) over ()
    from profiles p
    join auth.users u on u.id = p.id
    where q is null or u.email ilike '%' || q || '%' or p.display_name ilike '%' || q || '%'
    order by p.created_at desc
    limit least(greatest(coalesce($3, 50), 1), 200)
    offset greatest(coalesce($2, 0), 0) * least(greatest(coalesce($3, 50), 1), 200);
end;
$$;
revoke execute on function public.admin_list_users(text, int, int) from public, anon;
grant  execute on function public.admin_list_users(text, int, int) to authenticated;

create or replace function public.admin_set_upload_banned(target uuid, banned boolean)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
  if not is_admin() then
    raise exception 'admin only' using errcode = '42501';
  end if;
  update profiles set upload_banned = coalesce(banned, false) where id = target;
end;
$$;
revoke execute on function public.admin_set_upload_banned(uuid, boolean) from public, anon;
grant  execute on function public.admin_set_upload_banned(uuid, boolean) to authenticated;
