import { supabase } from './supabase';
import { deleteWallpaper, nextCuratedOrder } from './api';
import type { Wallpaper } from './types';

export type BulkAction =
  | { kind: 'publish' } | { kind: 'unpublish' }
  | { kind: 'curate' } | { kind: 'uncurate' }
  | { kind: 'premium'; on: boolean } | { kind: 'amoled'; on: boolean }
  | { kind: 'category'; categoryId: string | null }
  | { kind: 'delete' };

/** Applies one action to many wallpapers. Returns a short summary for the toast. */
export async function applyBulk(rows: Wallpaper[], action: BulkAction): Promise<string> {
  const ids = rows.map((r) => r.id);
  const update = async (patch: Record<string, unknown>, only = ids) => {
    if (!only.length) return;
    const { error } = await supabase.from('wallpapers').update(patch).in('id', only);
    if (error) throw new Error(error.message);
  };
  switch (action.kind) {
    case 'publish': {
      // Licence is required for anything users can see (same rule as the edit form).
      const ok = rows.filter((r) => !!r.license).map((r) => r.id);
      await update({ is_published: true, publish_at: null }, ok);
      const skipped = ids.length - ok.length;
      return `Published ${ok.length}` + (skipped ? ` · ${skipped} skipped (no licence recorded)` : '');
    }
    case 'unpublish': await update({ is_published: false }); return `Moved ${ids.length} to drafts`;
    case 'curate': {
      // New picks go to the end of the Curated order, in the order shown.
      let next = await nextCuratedOrder();
      for (const r of rows.filter((x) => !x.is_curated)) {
        const { error } = await supabase.from('wallpapers').update({ is_curated: true, curated_order: next++ }).eq('id', r.id);
        if (error) throw new Error(error.message);
      }
      return `Added ${rows.filter((x) => !x.is_curated).length} to Curated`;
    }
    case 'uncurate': await update({ is_curated: false, curated_order: null }); return `Removed ${ids.length} from Curated`;
    case 'premium': await update({ is_premium: action.on }); return `${action.on ? 'Marked' : 'Unmarked'} ${ids.length} as Premium`;
    case 'amoled': await update({ is_amoled: action.on }); return `${action.on ? 'Marked' : 'Unmarked'} ${ids.length} as AMOLED`;
    case 'category': await update({ category_id: action.categoryId }); return `Moved ${ids.length} to a new category`;
    case 'delete': {
      const warnings: string[] = [];
      for (const r of rows) {
        const w = await deleteWallpaper(r);
        if (w) warnings.push(w);
      }
      return `Deleted ${ids.length}` + (warnings.length ? ` · ${warnings.length} CDN warning(s)` : '');
    }
  }
}
