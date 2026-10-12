-- =====================================================================
-- A2 · Core schema, RLS, search indexes
-- Contract: docs/CONTRACTS.md sections 1 + 4
-- =====================================================================

create extension if not exists pg_trgm with schema extensions;

-- Internal helpers live here. This schema is NOT exposed through the API.
create schema if not exists private;

-- ---------------------------------------------------------------------
-- Helper functions
-- ---------------------------------------------------------------------

create or replace function public.slugify(txt text)
returns text
language sql immutable
set search_path = ''
as $$
  select trim(both '-' from regexp_replace(lower(coalesce(txt, '')), '[^a-z0-9]+', '-', 'g'))
$$;

-- '#RRGGBB' → colour bucket used by the app's colour filter chips:
-- red, orange, yellow, green, teal, blue, purple, pink, brown, black, white, gray
create or replace function public.color_family(hex text)
returns text
language plpgsql immutable
set search_path = ''
as $$
declare
  r float8; g float8; b float8;
  mx float8; mn float8; d float8;
  h float8; s float8; l float8;
begin
  if hex is null or hex !~* '^#?[0-9a-f]{6}$' then
    return null;
  end if;
  hex := ltrim(hex, '#');
  r := ('x' || substr(hex, 1, 2))::bit(8)::int / 255.0;
  g := ('x' || substr(hex, 3, 2))::bit(8)::int / 255.0;
  b := ('x' || substr(hex, 5, 2))::bit(8)::int / 255.0;
  mx := greatest(r, g, b);
  mn := least(r, g, b);
  d  := mx - mn;
  l  := (mx + mn) / 2;

  if l < 0.12 then return 'black'; end if;
  if l > 0.92 then return 'white'; end if;
  s := case when d = 0 then 0 else d / (1 - abs(2 * l - 1)) end;
  if s < 0.15 then return 'gray'; end if;

  if mx = r then
    h := 60 * ((g - b) / d);
  elsif mx = g then
    h := 60 * ((b - r) / d + 2);
  else
    h := 60 * ((r - g) / d + 4);
  end if;
  if h < 0 then h := h + 360; end if;

  if h >= 15 and h < 45 and l < 0.4 then return 'brown'; end if;
  return case
    when h < 15 or h >= 345 then 'red'
    when h < 45  then 'orange'
    when h < 70  then 'yellow'
    when h < 160 then 'green'
    when h < 195 then 'teal'
    when h < 255 then 'blue'
    when h < 290 then 'purple'
    else 'pink'
  end;
end;
$$;

-- ---------------------------------------------------------------------
-- Content tables
-- ---------------------------------------------------------------------

create table public.categories (
  id          uuid primary key default gen_random_uuid(),
  name        text not null unique,
  slug        text not null unique,
  icon        text,
  sort_order  int  not null default 0,
  created_at  timestamptz not null default now()
);

create table public.tags (
  id          uuid primary key default gen_random_uuid(),
  name        text not null unique,          -- always stored lower-case, trimmed
  slug        text not null unique,
  created_at  timestamptz not null default now()
);

create table public.wallpapers (
  id               uuid primary key default gen_random_uuid(),
  title            text not null,
  storage_key      text not null unique,     -- CDN folder, e.g. 'w/7f3a9c2e'
  original_ext     text not null default 'jpg' check (original_ext in ('jpg', 'png')),
  width            int  check (width  > 0),
  height           int  check (height > 0),
  aspect_ratio     numeric generated always as (round(width::numeric / nullif(height, 0), 4)) stored,
  device_type      text not null default 'phone' check (device_type in ('phone', 'tablet', 'both')),
  is_amoled        boolean not null default false,
  is_curated       boolean not null default false,
  curated_order    int,
  is_premium       boolean not null default false,
  is_published     boolean not null default false,
  publish_at       timestamptz,
  dominant_color   text check (dominant_color ~* '^#[0-9a-f]{6}$'),
  color_family     text generated always as (public.color_family(dominant_color)) stored,
  blurhash         text,
  phash            text,
  category_id      uuid references public.categories (id) on delete set null,
  file_size_kb     int,
  author_name      text,
  license          text,
  source_url       text,
  download_count   int not null default 0,
  fav_count        int not null default 0,
  view_count       int not null default 0,
  popularity_score numeric not null default 0,
  search_tsv       tsvector,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create index wallpapers_published_idx  on public.wallpapers (is_published, publish_at);
create index wallpapers_curated_idx    on public.wallpapers (curated_order) where is_curated;
create index wallpapers_category_idx   on public.wallpapers (category_id);
create index wallpapers_popularity_idx on public.wallpapers (popularity_score desc);
create index wallpapers_downloads_idx  on public.wallpapers (download_count desc);
create index wallpapers_created_idx    on public.wallpapers (created_at desc);
create index wallpapers_color_idx      on public.wallpapers (color_family);
create index wallpapers_phash_idx      on public.wallpapers (phash);
create index wallpapers_search_idx     on public.wallpapers using gin (search_tsv);
create index wallpapers_title_trgm_idx on public.wallpapers using gin (lower(title) extensions.gin_trgm_ops);
create index tags_name_trgm_idx        on public.tags       using gin (name extensions.gin_trgm_ops);
create index categories_name_trgm_idx  on public.categories using gin (lower(name) extensions.gin_trgm_ops);

create table public.wallpaper_tags (
  wallpaper_id uuid not null references public.wallpapers (id) on delete cascade,
  tag_id       uuid not null references public.tags (id) on delete cascade,
  primary key (wallpaper_id, tag_id)
);
create index wallpaper_tags_tag_idx on public.wallpaper_tags (tag_id);

create table public.collections (
  id                 uuid primary key default gen_random_uuid(),
  title              text not null,
  cover_wallpaper_id uuid references public.wallpapers (id) on delete set null,
  is_premium         boolean not null default false,
  sort_order         int not null default 0,
  created_at         timestamptz not null default now()
);

create table public.collection_items (
  collection_id uuid not null references public.collections (id) on delete cascade,
  wallpaper_id  uuid not null references public.wallpapers (id) on delete cascade,
  position      int  not null default 0,
  primary key (collection_id, wallpaper_id)
);

-- ---------------------------------------------------------------------
-- User tables
-- ---------------------------------------------------------------------

create table public.profiles (
  id             uuid primary key references auth.users (id) on delete cascade,
  display_name   text,
  avatar_url     text,
  role           text not null default 'user' check (role in ('user', 'admin')),
  is_premium     boolean not null default false,
  premium_until  timestamptz,
  created_at     timestamptz not null default now()
);

create table public.favorites (
  user_id      uuid not null references auth.users (id) on delete cascade,
  wallpaper_id uuid not null references public.wallpapers (id) on delete cascade,
  created_at   timestamptz not null default now(),
  primary key (user_id, wallpaper_id)
);
create index favorites_wallpaper_idx on public.favorites (wallpaper_id);

create table public.unlocks (
  user_id      uuid not null references auth.users (id) on delete cascade,
  wallpaper_id uuid not null references public.wallpapers (id) on delete cascade,
  unlocked_at  timestamptz not null default now(),
  primary key (user_id, wallpaper_id)
);

create table public.user_events (
  id           bigint generated always as identity primary key,
  user_id      uuid references auth.users (id) on delete set null,
  device_id    text not null,
  wallpaper_id uuid not null references public.wallpapers (id) on delete cascade,
  type         text not null check (type in ('view', 'download', 'apply', 'fav', 'unfav')),
  created_at   timestamptz not null default now()
);
create index user_events_ratelimit_idx on public.user_events (device_id, wallpaper_id, type, created_at desc);
create index user_events_device_day_idx on public.user_events (device_id, type, created_at desc);
create index user_events_user_idx      on public.user_events (user_id, type) where user_id is not null;
create index user_events_created_idx   on public.user_events (created_at);

create table public.user_tag_affinity (
  user_id    uuid not null references auth.users (id) on delete cascade,
  tag_id     uuid not null references public.tags (id) on delete cascade,
  score      numeric not null default 0,
  updated_at timestamptz not null default now(),
  constraint user_tag_affinity_pkey primary key (user_id, tag_id)
);

-- Per-day counters. Powers Popular (week/month) without scanning raw events.
create table public.wallpaper_daily_stats (
  wallpaper_id uuid not null references public.wallpapers (id) on delete cascade,
  day          date not null default current_date,
  views        int  not null default 0,
  downloads    int  not null default 0,
  applies      int  not null default 0,
  favs         int  not null default 0,          -- net (fav - unfav)
  constraint wallpaper_daily_stats_pkey primary key (wallpaper_id, day)
);
create index wallpaper_daily_stats_day_idx on public.wallpaper_daily_stats (day);

create table public.reports (
  id           uuid primary key default gen_random_uuid(),
  wallpaper_id uuid not null references public.wallpapers (id) on delete cascade,
  user_id      uuid references auth.users (id) on delete set null,
  device_id    text,
  reason       text not null check (char_length(reason) between 1 and 500),
  status       text not null default 'open' check (status in ('open', 'resolved', 'dismissed')),
  created_at   timestamptz not null default now()
);

-- Search queries. Powers the dashboard's "zero-result searches" insight.
create table public.search_log (
  id           bigint generated always as identity primary key,
  q            text not null,
  result_count int  not null,
  created_at   timestamptz not null default now()
);
create index search_log_created_idx on public.search_log (created_at);

create table public.app_config (
  key        text primary key,
  value      jsonb not null,
  updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- Triggers
-- ---------------------------------------------------------------------

-- Tags: normalise the name, auto-slug. Categories: auto-slug.
create or replace function private.normalize_tag()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.name := lower(trim(new.name));
  new.slug := coalesce(nullif(new.slug, ''), nullif(public.slugify(new.name), ''), left(md5(new.name), 10));
  return new;
end;
$$;
create trigger tags_normalize before insert or update of name, slug on public.tags
  for each row execute function private.normalize_tag();

create or replace function private.normalize_category()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.name := trim(new.name);
  new.slug := coalesce(nullif(new.slug, ''), nullif(public.slugify(new.name), ''), left(md5(new.name), 10));
  return new;
end;
$$;
create trigger categories_normalize before insert or update of name, slug on public.categories
  for each row execute function private.normalize_category();

-- Full-text document: title + tags (weight A), category (B), author (C)
create or replace function private.wallpaper_search_tsv(p_id uuid, p_title text, p_category uuid, p_author text)
returns tsvector language sql stable set search_path = '' as $$
  select
      setweight(to_tsvector('english', coalesce(p_title, '')), 'A')
   || setweight(to_tsvector('english', coalesce((
        select string_agg(t.name, ' ')
        from public.wallpaper_tags wt join public.tags t on t.id = wt.tag_id
        where wt.wallpaper_id = p_id), '')), 'A')
   || setweight(to_tsvector('english', coalesce((
        select c.name from public.categories c where c.id = p_category), '')), 'B')
   || setweight(to_tsvector('simple', coalesce(p_author, '')), 'C')
$$;

-- Fires only on content edits (not on counter updates), so updated_at stays meaningful.
create or replace function private.wallpapers_before_write()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  new.search_tsv := private.wallpaper_search_tsv(new.id, new.title, new.category_id, new.author_name);
  new.updated_at := now();
  return new;
end;
$$;
create trigger wallpapers_before_write
  before insert or update of title, storage_key, original_ext, width, height, device_type, is_amoled,
    is_curated, curated_order, is_premium, is_published, publish_at, dominant_color, blurhash,
    category_id, author_name, license, source_url
  on public.wallpapers
  for each row execute function private.wallpapers_before_write();

create or replace function private.wallpaper_tags_changed()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.wallpapers w
     set search_tsv = private.wallpaper_search_tsv(w.id, w.title, w.category_id, w.author_name)
   where w.id = coalesce(new.wallpaper_id, old.wallpaper_id);
  return null;
end;
$$;
create trigger wallpaper_tags_changed after insert or delete on public.wallpaper_tags
  for each row execute function private.wallpaper_tags_changed();

create or replace function private.tag_renamed()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.wallpapers w
     set search_tsv = private.wallpaper_search_tsv(w.id, w.title, w.category_id, w.author_name)
   where w.id in (select wt.wallpaper_id from public.wallpaper_tags wt where wt.tag_id = new.id);
  return null;
end;
$$;
create trigger tag_renamed after update of name on public.tags
  for each row when (old.name is distinct from new.name)
  execute function private.tag_renamed();

create or replace function private.category_renamed()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.wallpapers w
     set search_tsv = private.wallpaper_search_tsv(w.id, w.title, w.category_id, w.author_name)
   where w.category_id = new.id;
  return null;
end;
$$;
create trigger category_renamed after update of name on public.categories
  for each row when (old.name is distinct from new.name)
  execute function private.category_renamed();

-- Favourites keep wallpapers.fav_count in sync
create or replace function private.favorites_count()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    update public.wallpapers set fav_count = fav_count + 1 where id = new.wallpaper_id;
  else
    update public.wallpapers set fav_count = greatest(fav_count - 1, 0) where id = old.wallpaper_id;
  end if;
  return null;
end;
$$;
create trigger favorites_count after insert or delete on public.favorites
  for each row execute function private.favorites_count();

-- New auth user → profile row (name/avatar from Google metadata when present)
create or replace function private.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name', split_part(new.email, '@', 1)),
    new.raw_user_meta_data ->> 'avatar_url'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function private.handle_new_user();

create or replace function private.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end;
$$;
create trigger app_config_touch before update on public.app_config
  for each row execute function private.touch_updated_at();

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'admin');
$$;

alter table public.categories            enable row level security;
alter table public.tags                  enable row level security;
alter table public.wallpapers            enable row level security;
alter table public.wallpaper_tags        enable row level security;
alter table public.collections           enable row level security;
alter table public.collection_items      enable row level security;
alter table public.profiles              enable row level security;
alter table public.favorites             enable row level security;
alter table public.unlocks               enable row level security;
alter table public.user_events           enable row level security;
alter table public.user_tag_affinity     enable row level security;
alter table public.wallpaper_daily_stats enable row level security;
alter table public.reports               enable row level security;
alter table public.search_log            enable row level security;
alter table public.app_config            enable row level security;

-- Public read + admin write
create policy "public read"  on public.categories       for select to anon, authenticated using (true);
create policy "admin write"  on public.categories       for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy "public read"  on public.tags             for select to anon, authenticated using (true);
create policy "admin write"  on public.tags             for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy "public read"  on public.wallpaper_tags   for select to anon, authenticated using (true);
create policy "admin write"  on public.wallpaper_tags   for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy "public read"  on public.collections      for select to anon, authenticated using (true);
create policy "admin write"  on public.collections      for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy "public read"  on public.collection_items for select to anon, authenticated using (true);
create policy "admin write"  on public.collection_items for all    to authenticated using (public.is_admin()) with check (public.is_admin());
create policy "public read"  on public.app_config       for select to anon, authenticated using (true);
create policy "admin write"  on public.app_config       for all    to authenticated using (public.is_admin()) with check (public.is_admin());

create policy "public read published" on public.wallpapers for select to anon, authenticated
  using ((is_published and (publish_at is null or publish_at <= now())) or public.is_admin());
create policy "admin write" on public.wallpapers for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- Profiles: own row (or admin). Users may only change display_name/avatar_url.
create policy "own or admin read" on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_admin());
create policy "own update" on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());
revoke insert, update, delete on public.profiles from anon, authenticated;
grant update (display_name, avatar_url) on public.profiles to authenticated;

-- Favourites / unlocks / affinity: own rows only
create policy "own read"   on public.favorites for select to authenticated using (user_id = auth.uid());
create policy "own insert" on public.favorites for insert to authenticated with check (user_id = auth.uid());
create policy "own delete" on public.favorites for delete to authenticated using (user_id = auth.uid());

create policy "own read"   on public.unlocks for select to authenticated using (user_id = auth.uid());
create policy "own insert" on public.unlocks for insert to authenticated with check (user_id = auth.uid());
create policy "own update" on public.unlocks for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "own read" on public.user_tag_affinity for select to authenticated using (user_id = auth.uid());

-- Written only through RPCs (security definer); admins can read for insights
create policy "admin read" on public.user_events           for select to authenticated using (public.is_admin());
create policy "admin read" on public.wallpaper_daily_stats for select to authenticated using (public.is_admin());
create policy "admin read" on public.search_log            for select to authenticated using (public.is_admin());

-- Reports: anyone can file one; only admins can read/manage them
create policy "anyone insert" on public.reports for insert to anon, authenticated
  with check (status = 'open' and (user_id is null or user_id = auth.uid()));
create policy "admin all" on public.reports for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------
-- View the app reads (published only, with tags + category)
-- ---------------------------------------------------------------------

create or replace view public.wallpapers_public
with (security_invoker = true) as
select
  w.id,
  w.title,
  w.storage_key,
  w.original_ext,
  w.width,
  w.height,
  w.aspect_ratio,
  w.device_type,
  w.is_amoled,
  w.is_curated,
  w.curated_order,
  w.is_premium,
  w.dominant_color,
  w.color_family,
  w.blurhash,
  w.category_id,
  c.slug  as category_slug,
  c.name  as category_name,
  w.file_size_kb,
  w.author_name,
  w.license,
  w.source_url,
  w.download_count,
  w.fav_count,
  w.view_count,
  w.popularity_score,
  coalesce(t.tags, '{}'::text[]) as tags,
  coalesce(w.publish_at, w.created_at) as published_at,
  w.created_at
from public.wallpapers w
left join public.categories c on c.id = w.category_id
left join lateral (
  select array_agg(tg.name order by tg.name) as tags
  from public.wallpaper_tags wt
  join public.tags tg on tg.id = wt.tag_id
  where wt.wallpaper_id = w.id
) t on true
where w.is_published
  and (w.publish_at is null or w.publish_at <= now());

grant select on public.wallpapers_public to anon, authenticated;

-- ---------------------------------------------------------------------
-- Default app_config (contract section 4)
-- ---------------------------------------------------------------------

insert into public.app_config (key, value) values
  ('cdn_base_url',        '""'),
  ('min_app_version',     '1'),
  ('maintenance',         '{"enabled": false, "message": ""}'),
  ('ads',                 '{"native_every": 12, "app_open_enabled": false, "app_open_min_hours": 4}'),
  ('unlock_mode',         '"permanent"'),
  ('free_max_resolution', '2560'),
  ('home_sections',       '["for_you","curated","popular","new","amoled","tablet","premium"]'),
  ('featured_banner',     'null'),
  ('daily_wallpaper_id',  'null'),
  ('paywall',             '{"highlight": "annual", "headline": "Unlock every wallpaper"}')
on conflict (key) do nothing;
