import { useEffect, useState } from 'react';
import { Select, type SelectProps } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { supabase } from '../lib/supabase';

interface Option { value: string; label: string }

/** Searchable wallpaper selector (by title). Value = wallpaper id. */
export function WallpaperPicker({
  value, onChange, publishedOnly, ...rest
}: { value: string | null; onChange: (id: string | null) => void; publishedOnly?: boolean } & Omit<SelectProps, 'value' | 'onChange' | 'data'>) {
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 250);
  const [options, setOptions] = useState<Option[]>([]);

  // Make sure the current value has a label even if it isn't in the search results.
  useEffect(() => {
    if (!value || options.some((o) => o.value === value)) return;
    supabase.from('wallpapers').select('id,title').eq('id', value).maybeSingle().then(({ data }) => {
      if (data) setOptions((o) => [{ value: data.id, label: data.title }, ...o.filter((x) => x.value !== data.id)]);
    });
  }, [value]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    let q = supabase.from('wallpapers').select('id,title,is_published').order('created_at', { ascending: false }).limit(25);
    if (debounced) q = q.ilike('title', `%${debounced}%`);
    if (publishedOnly) q = q.eq('is_published', true);
    q.then(({ data }) => {
      const found = (data ?? []).map((w) => ({ value: w.id, label: w.is_published ? w.title : `${w.title} (draft)` }));
      setOptions((prev) => {
        const current = prev.find((o) => o.value === value);
        return current && !found.some((f) => f.value === current.value) ? [current, ...found] : found;
      });
    });
  }, [debounced, publishedOnly]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Select
      searchable
      clearable
      nothingFoundMessage="No wallpapers match"
      placeholder="Search by title"
      {...rest}
      data={options}
      value={value}
      onChange={onChange}
      searchValue={search}
      onSearchChange={setSearch}
      filter={({ options: o }) => o}
    />
  );
}
