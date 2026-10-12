export type DeviceType = 'phone' | 'tablet' | 'both';
export type PublishStatus = 'draft' | 'published' | 'scheduled';

export interface Category {
  id: string;
  name: string;
  name_hi: string | null;   // optional Hindi name (migration 1100); the app falls back to name
  slug: string;
  icon: string | null;
  sort_order: number;
}

export interface Wallpaper {
  id: string;
  title: string;
  storage_key: string;
  original_ext: 'jpg' | 'png';
  width: number | null;
  height: number | null;
  aspect_ratio: number | null;
  device_type: DeviceType;
  is_amoled: boolean;
  is_curated: boolean;
  curated_order: number | null;
  is_premium: boolean;
  is_published: boolean;
  publish_at: string | null;
  dominant_color: string | null;
  color_family: string | null;
  blurhash: string | null;
  phash: string | null;
  category_id: string | null;
  file_size_kb: number | null;
  author_name: string | null;
  license: string | null;
  source_url: string | null;
  download_count: number;
  fav_count: number;
  view_count: number;
  created_at: string;
  updated_at: string;
  wallpaper_tags?: { tags: { name: string } | null }[];
}

/** Editable metadata shared by the upload and edit forms. */
export interface WallpaperMeta {
  title: string;
  category_id: string | null;
  tags: string[];
  device_type: DeviceType;
  is_amoled: boolean;
  is_curated: boolean;
  is_premium: boolean;
  status: PublishStatus;
  publish_at: string | null; // 'YYYY-MM-DD HH:mm:ss' local (Mantine dates format)
  author_name: string;
  license: string;
  source_url: string;
}

export const WALLPAPER_COLUMNS =
  'id,title,storage_key,original_ext,width,height,aspect_ratio,device_type,is_amoled,is_curated,curated_order,' +
  'is_premium,is_published,publish_at,dominant_color,color_family,blurhash,phash,category_id,file_size_kb,' +
  'author_name,license,source_url,download_count,fav_count,view_count,created_at,updated_at,wallpaper_tags(tags(name))';

export const LICENSES = [
  'Own work',
  'Commissioned (full rights)',
  'Licensed – commercial redistribution',
  'Artist permission (written)',
  'CC0 / Public domain',
  'Other (see source)',
];
