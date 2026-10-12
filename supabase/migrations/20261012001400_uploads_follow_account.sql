-- =====================================================================
-- Deleting an account deletes that person's uploads (privacy policy, 2026-10-12).
--   * wallpapers.uploader_id now cascades, so the rows go with auth.users
--   * the image files can't be deleted from SQL (they live in the CDN repo), so every deleted
--     user upload queues its storage_key; the user-upload edge function empties the queue
-- =====================================================================

alter table public.wallpapers drop constraint if exists wallpapers_uploader_id_fkey;
alter table public.wallpapers
  add constraint wallpapers_uploader_id_fkey
  foreign key (uploader_id) references auth.users (id) on delete cascade;

create table if not exists private.cdn_delete_queue (
  storage_key text primary key,
  queued_at   timestamptz not null default now()
);
revoke all on private.cdn_delete_queue from public, anon, authenticated;

create or replace function private.queue_user_upload_files()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if old.uploader_id is not null then
    insert into private.cdn_delete_queue (storage_key) values (old.storage_key) on conflict do nothing;
  end if;
  return old;
end;
$$;

drop trigger if exists wallpapers_queue_user_upload_files on public.wallpapers;
create trigger wallpapers_queue_user_upload_files after delete on public.wallpapers
  for each row execute function private.queue_user_upload_files();

-- Service-role helpers for the edge function (private schema isn't exposed over the API).
create or replace function public.cdn_delete_queue_take(max_items int default 5)
returns setof text language sql volatile security definer set search_path = '' as $$
  select q.storage_key from private.cdn_delete_queue q order by q.queued_at limit greatest(least(max_items, 20), 1);
$$;
create or replace function public.cdn_delete_queue_done(key text)
returns void language sql volatile security definer set search_path = '' as $$
  delete from private.cdn_delete_queue where storage_key = key;
$$;
revoke all on function public.cdn_delete_queue_take(int) from public, anon, authenticated;
revoke all on function public.cdn_delete_queue_done(text) from public, anon, authenticated;
grant execute on function public.cdn_delete_queue_take(int) to service_role;
grant execute on function public.cdn_delete_queue_done(text) to service_role;
