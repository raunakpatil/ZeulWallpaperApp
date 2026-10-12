import { useCallback, useEffect, useState } from 'react';
import { supabase } from './supabase';
import { loadAllTagNames } from './api';
import type { Category } from './types';

/** Categories, tag names and the CDN base URL, needed on most pages. */
export function useAppData() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [tagNames, setTagNames] = useState<string[]>([]);
  const [cdnBase, setCdnBase] = useState('');

  const reload = useCallback(async () => {
    const [cats, tags, cfg] = await Promise.all([
      supabase.from('categories').select('*').order('sort_order'),
      loadAllTagNames(),
      supabase.from('app_config').select('value').eq('key', 'cdn_base_url').maybeSingle(),
    ]);
    setCategories((cats.data ?? []) as Category[]);
    setTagNames(tags);
    setCdnBase(String(cfg.data?.value ?? '').replace(/\/+$/, ''));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const cdnUrl = useCallback(
    (storageKey: string, file = 'thumb.webp') => (cdnBase ? `${cdnBase}/${storageKey}/${file}` : ''),
    [cdnBase],
  );

  return { categories, tagNames, cdnBase, cdnUrl, reload };
}

export type AppData = ReturnType<typeof useAppData>;
