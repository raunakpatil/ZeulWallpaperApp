import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Spotlight, type SpotlightActionData, type SpotlightActionGroupData } from '@mantine/spotlight';
import { useDebouncedValue } from '@mantine/hooks';
import { IconPhoto, IconSearch } from '../lib/icons';
import { supabase } from '../lib/supabase';
import type { AppData } from '../lib/useAppData';

export interface PaletteEntry { to: string; label: string; hint: string; icon: React.ComponentType<{ size?: number; stroke?: number }> }

/** Ctrl/Cmd+K: jump to any page, run a quick action, or open a wallpaper by title. */
export function CommandPalette({ pages, quickActions, data }: { pages: PaletteEntry[]; quickActions: PaletteEntry[]; data: AppData }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [debounced] = useDebouncedValue(query, 200);
  const [found, setFound] = useState<{ id: string; title: string; storage_key: string; is_published: boolean }[]>([]);

  useEffect(() => {
    const q = debounced.trim();
    if (q.length < 2) { setFound([]); return; }
    supabase.from('wallpapers').select('id,title,storage_key,is_published').ilike('title', `%${q}%`)
      .order('created_at', { ascending: false }).limit(8)
      .then(({ data: rows }) => setFound(rows ?? []));
  }, [debounced]);

  const actions = useMemo<(SpotlightActionGroupData | SpotlightActionData)[]>(() => {
    const toAction = (p: PaletteEntry, prefix: string): SpotlightActionData => ({
      id: `${prefix}${p.to}`, label: p.label, description: p.hint, onClick: () => navigate(p.to),
      leftSection: <p.icon size={20} stroke={1.6} />,
    });
    const groups: SpotlightActionGroupData[] = [];
    if (found.length) {
      groups.push({
        group: 'Wallpapers',
        actions: found.map((w) => ({
          id: `w-${w.id}`,
          label: w.title,
          description: w.is_published ? 'Open in editor' : 'Draft · open in editor',
          keywords: [query],
          onClick: () => navigate(`/wallpapers?edit=${w.id}`),
          leftSection: data.cdnBase
            ? <img src={data.cdnUrl(w.storage_key)} alt="" style={{ width: 28, height: 40, objectFit: 'cover', borderRadius: 6 }} />
            : <IconPhoto size={20} />,
        })),
      });
    }
    groups.push({ group: 'Quick actions', actions: quickActions.map((a) => toAction(a, 'qa')) });
    groups.push({ group: 'Go to', actions: pages.map((p) => toAction(p, 'go')) });
    return groups;
  }, [found, pages, quickActions, navigate, data, query]);

  return (
    <Spotlight
      actions={actions}
      query={query}
      onQueryChange={setQuery}
      nothingFound="Nothing found. Try a page name or a wallpaper title."
      highlightQuery
      scrollable
      maxHeight={460}
      radius="lg"
      shortcut={['mod + K', '/']}
      searchProps={{ leftSection: <IconSearch size={18} stroke={1.6} />, placeholder: 'Search pages, actions or wallpapers…' }}
    />
  );
}
