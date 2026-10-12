import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import dayjs from 'dayjs';
import {
  ActionIcon, Anchor, Badge, Button, Card, Center, Checkbox, Chip, Drawer, Group, Image, Menu, Pagination, SegmentedControl, Select, Stack,
  Table, Text, TextInput, Tooltip, VisuallyHidden,
} from '@mantine/core';
import { Link, useSearchParams } from 'react-router-dom';
import { useDebouncedValue } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import {
  IconChecks, IconChevronDown, IconChevronLeft, IconChevronRight, IconCrown, IconDownload, IconEye, IconEyeOff, IconFilterOff,
  IconHeartFilled, IconLayoutGrid, IconList, IconPhoto, IconSearch, IconStar, IconStarFilled, IconTrash, IconUpload, IconX,
} from '../lib/icons';
import { EmptyState, PageHeader } from '../components/Help';
import { supabase } from '../lib/supabase';
import { deleteWallpaper, rowToMeta, updateWallpaper } from '../lib/api';
import { applyBulk, type BulkAction } from '../lib/bulk';
import { confirmAction } from '../lib/confirm';
import { WallpaperForm } from '../components/WallpaperForm';
import { isMetaValid } from '../lib/meta';
import { WALLPAPER_COLUMNS, type Wallpaper, type WallpaperMeta } from '../lib/types';
import type { AppData } from '../lib/useAppData';

const PAGE_SIZE = 40;
type Status = 'all' | 'published' | 'draft' | 'scheduled';

function statusOf(w: Wallpaper): Exclude<Status, 'all'> {
  if (!w.is_published) return 'draft';
  return w.publish_at && dayjs(w.publish_at).isAfter(dayjs()) ? 'scheduled' : 'published';
}
const STATUS_COLOR = { published: 'green', draft: 'gray', scheduled: 'yellow' } as const;

export default function WallpapersPage({ data }: { data: AppData }) {
  const [params, setParams] = useSearchParams();
  const [rows, setRows] = useState<Wallpaper[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [q, setQ] = useState('');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const [status, setStatus] = useState<Status>((params.get('status') as Status) || 'all');
  const [category, setCategory] = useState<string | null>(null);
  const [flags, setFlags] = useState<string[]>(params.getAll('flag'));
  const [sort, setSort] = useState('created_at');
  const [editing, setEditing] = useState<Wallpaper | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [bulkBusy, setBulkBusy] = useState(false);
  const lastClicked = useRef<number | null>(null);
  const editId = useRef<string | null>(params.get('edit'));
  const [view, setView] = useState<'grid' | 'list'>(() => {
    try { return localStorage.getItem('wallpapers-view') === 'list' ? 'list' : 'grid'; } catch { return 'grid'; }
  });

  // Links from Home (e.g. ?flag=is_amoled) set the filters once, then the URL is cleaned up.
  useEffect(() => { if ([...params.keys()].length) setParams({}, { replace: true }); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const id = editId.current;
    if (!id) return;
    editId.current = null;
    supabase.from('wallpapers').select(WALLPAPER_COLUMNS).eq('id', id).maybeSingle()
      .then(({ data: w }) => { if (w) setEditing(w as unknown as Wallpaper); });
  }, []);

  const changeView = (v: string) => {
    setView(v as 'grid' | 'list');
    try { localStorage.setItem('wallpapers-view', v); } catch { /* storage unavailable */ }
  };

  const load = useCallback(async () => {
    setLoading(true);
    let query = supabase.from('wallpapers').select(WALLPAPER_COLUMNS, { count: 'exact' });
    if (debouncedQ) query = query.ilike('title', `%${debouncedQ}%`);
    if (category) query = query.eq('category_id', category);
    if (status === 'draft') query = query.eq('is_published', false);
    if (status === 'published') query = query.eq('is_published', true).or(`publish_at.is.null,publish_at.lte.${new Date().toISOString()}`);
    if (status === 'scheduled') query = query.eq('is_published', true).gt('publish_at', new Date().toISOString());
    for (const f of flags) {
      if (f === 'tablet') query = query.in('device_type', ['tablet', 'both']);
      else query = query.eq(f, true);
    }
    const from = (page - 1) * PAGE_SIZE;
    const { data: list, count, error } = await query
      .order(sort, { ascending: false })
      .range(from, from + PAGE_SIZE - 1);
    setLoading(false);
    if (error) {
      notifications.show({ color: 'red', title: 'Load failed', message: error.message });
      return;
    }
    setRows((list ?? []) as unknown as Wallpaper[]);
    setTotal(count ?? 0);
  }, [debouncedQ, category, status, flags, page, sort]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { setPage(1); }, [debouncedQ, category, status, flags, sort]);

  const selecting = selected.size > 0;
  const selectedRows = useMemo(() => rows.filter((r) => selected.has(r.id)), [rows, selected]);
  useEffect(() => { setSelected(new Set()); lastClicked.current = null; }, [debouncedQ, category, status, flags, sort, page]);

  /** Click toggles; Shift+click selects the whole range since the last click. */
  function toggleSelect(index: number, shift: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (shift && lastClicked.current !== null) {
        const [a, b] = [lastClicked.current, index].sort((x, y) => x - y);
        for (let i = a; i <= b; i++) next.add(rows[i].id);
      } else if (next.has(rows[index].id)) next.delete(rows[index].id);
      else next.add(rows[index].id);
      return next;
    });
    lastClicked.current = index;
  }

  async function runBulk(action: BulkAction) {
    if (!selectedRows.length) return;
    if (action.kind === 'delete' && !(await confirmAction({
      title: `Delete ${selectedRows.length} wallpaper${selectedRows.length > 1 ? 's' : ''}?`,
      message: 'They disappear from the app and their image files are removed from the CDN. This cannot be undone.',
      confirmLabel: 'Delete permanently', danger: true,
    }))) return;
    setBulkBusy(true);
    try {
      const summary = await applyBulk(selectedRows, action);
      notifications.show({ color: 'green', message: summary });
      setSelected(new Set());
      await load();
      data.reload();
    } catch (e) {
      notifications.show({ color: 'red', title: 'Bulk action failed', message: (e as Error).message });
    } finally {
      setBulkBusy(false);
    }
  }

  /** One-click toggles on a tile. */
  async function quick(w: Wallpaper, action: BulkAction) {
    try {
      notifications.show({ color: 'green', message: await applyBulk([w], action) });
      await load();
    } catch (e) {
      notifications.show({ color: 'red', message: (e as Error).message });
    }
  }

  const editIndex = editing ? rows.findIndex((r) => r.id === editing.id) : -1;
  const catName = (id: string | null) => data.categories.find((c) => c.id === id)?.name ?? '-';
  const filtered = !!(debouncedQ || category || status !== 'all' || flags.length);

  return (
    <Stack>
      <PageHeader
        title={<>Wallpapers <Text span c="dimmed" fz={20} fw={500}>{total}</Text></>}
        description="Everything you've uploaded. Click any wallpaper to edit its details, tags and visibility, or to delete it."
        tips={[
          'Hover a tile to see favourites and downloads, like likes and comments on Instagram.',
          'Tick the circle on a tile (or Select all) for bulk actions. Shift+click selects a range.',
          'The eye and star buttons on a tile publish/unpublish and curate in one click.',
          'Badges on tiles: draft / scheduled (not live yet), ★ Curated (with its position), AMOLED, crown = Premium.',
          'Sort by "Most downloaded" to find what users love, then upload more like it.',
          'Deleting removes the wallpaper from the app and deletes its image files. It cannot be undone.',
        ]}
        actions={
          <>
            <SegmentedControl radius="xl" size="xs" value={view} onChange={changeView} data={[
              { value: 'grid', label: <Center><IconLayoutGrid size={16} /><VisuallyHidden>Grid view</VisuallyHidden></Center> },
              { value: 'list', label: <Center><IconList size={16} /><VisuallyHidden>List view</VisuallyHidden></Center> },
            ]} />
            <Button variant="default" leftSection={<IconChecks size={16} />} disabled={!rows.length}
              onClick={() => setSelected(selecting ? new Set() : new Set(rows.map((r) => r.id)))}>
              {selecting ? 'Clear selection' : 'Select all'}
            </Button>
            <Button component={Link} to="/upload" variant="gradient" leftSection={<IconUpload size={16} />}>Upload</Button>
          </>
        }
      />

      <Group gap="sm" wrap="wrap">
        <TextInput placeholder="Search by title" leftSection={<IconSearch size={16} />} value={q} radius="xl"
          onChange={(e) => setQ(e.currentTarget.value)} w={220} />
        <Select w={140} value={status} onChange={(v) => setStatus((v as Status) ?? 'all')} allowDeselect={false}
          data={[{ value: 'all', label: 'All statuses' }, { value: 'published', label: 'Published' },
            { value: 'draft', label: 'Drafts' }, { value: 'scheduled', label: 'Scheduled' }]} />
        <Select w={170} placeholder="All categories" clearable value={category} onChange={setCategory}
          data={data.categories.map((c) => ({ value: c.id, label: c.name }))} />
        <Select w={170} value={sort} onChange={(v) => setSort(v ?? 'created_at')} allowDeselect={false}
          data={[{ value: 'created_at', label: 'Newest' }, { value: 'download_count', label: 'Most downloaded' },
            { value: 'fav_count', label: 'Most favourited' }, { value: 'updated_at', label: 'Recently edited' }]} />
        <Chip.Group multiple value={flags} onChange={setFlags}>
          <Group gap={6}>
            <Chip size="xs" value="is_curated">Curated</Chip>
            <Chip size="xs" value="is_amoled">AMOLED</Chip>
            <Chip size="xs" value="is_premium">Premium</Chip>
            <Chip size="xs" value="tablet">Tablet</Chip>
          </Group>
        </Chip.Group>
      </Group>

      {!rows.length && !loading ? (
        <Card withBorder className="soft-card">
          {filtered ? (
            <EmptyState icon={<IconFilterOff size={28} />} title="Nothing matches these filters">
              Try clearing the search, or switch status to "All statuses".
            </EmptyState>
          ) : (
            <EmptyState icon={<IconPhoto size={28} />} title="No wallpapers yet"
              action={<Button component={Link} to="/upload" variant="gradient" leftSection={<IconUpload size={16} />}>Upload your first wallpapers</Button>}>
              Drop your images on the Upload page. Resizing, colours and AMOLED detection happen automatically.
            </EmptyState>
          )}
        </Card>
      ) : view === 'grid' ? (
        <div className="ig-grid" style={{ opacity: loading ? 0.5 : 1 }}>
          {rows.map((w, i) => {
            const st = statusOf(w);
            const isSel = selected.has(w.id);
            return (
              <div key={w.id} className="ig-tile-wrap" data-selected={isSel || undefined} data-selecting={selecting || undefined}>
              <button type="button" className="ig-tile"
                onClick={(e) => (selecting ? toggleSelect(i, e.shiftKey) : setEditing(w))}
                style={{ background: w.dominant_color ?? undefined }} aria-label={selecting ? `Select ${w.title}` : `Edit ${w.title}`}>
                <img src={data.cdnUrl(w.storage_key)} alt="" loading="lazy" />
                <div className="ig-badges">
                  {st !== 'published' && <Badge size="xs" color={STATUS_COLOR[st]} variant="filled">{st}</Badge>}
                  {w.is_curated && <Badge size="xs" color="brand" variant="filled" leftSection={<IconStarFilled size={9} />}>{w.curated_order}</Badge>}
                  {w.is_amoled && <Badge size="xs" color="dark" variant="filled">AMOLED</Badge>}
                  {w.is_premium && <Badge size="xs" color="yellow" variant="filled"><IconCrown size={10} /></Badge>}
                </div>
                <div className="ig-overlay">
                  <Group gap={14} wrap="nowrap">
                    <Group gap={4} wrap="nowrap"><IconHeartFilled size={18} />{w.fav_count}</Group>
                    <Group gap={4} wrap="nowrap"><IconDownload size={18} />{w.download_count}</Group>
                  </Group>
                  <Text size="xs" fw={600} lineClamp={2} c="white">{w.title}</Text>
                </div>
              </button>
              <Checkbox className="ig-select" radius="xl" size="md" checked={isSel} aria-label={`Select ${w.title}`}
                onChange={() => undefined} onClick={(e) => toggleSelect(i, e.shiftKey)} />
              {!selecting && (
                <div className="ig-tools">
                  <Tooltip label={w.is_published ? 'Move to drafts' : 'Publish now'}>
                    <ActionIcon size="md" radius="xl" variant="white" color="dark" aria-label={w.is_published ? 'Unpublish' : 'Publish'}
                      onClick={() => quick(w, { kind: w.is_published ? 'unpublish' : 'publish' })}>
                      {w.is_published ? <IconEyeOff size={16} /> : <IconEye size={16} />}
                    </ActionIcon>
                  </Tooltip>
                  <Tooltip label={w.is_curated ? 'Remove from Curated' : 'Add to Curated'}>
                    <ActionIcon size="md" radius="xl" variant="white" color={w.is_curated ? 'brand' : 'dark'} aria-label="Toggle curated"
                      onClick={() => quick(w, { kind: w.is_curated ? 'uncurate' : 'curate' })}>
                      {w.is_curated ? <IconStarFilled size={16} /> : <IconStar size={16} />}
                    </ActionIcon>
                  </Tooltip>
                </div>
              )}
              </div>
            );
          })}
        </div>
      ) : (
        <Table.ScrollContainer minWidth={800}>
          <Table highlightOnHover verticalSpacing={6}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th w={36}>
                  <Checkbox aria-label="Select all" checked={selecting && selected.size === rows.length} indeterminate={selecting && selected.size < rows.length}
                    onChange={() => setSelected(selecting ? new Set() : new Set(rows.map((r) => r.id)))} />
                </Table.Th>
                <Table.Th w={60} /><Table.Th>Title</Table.Th><Table.Th>Status</Table.Th><Table.Th>Category</Table.Th>
                <Table.Th>Flags</Table.Th><Table.Th>Tags</Table.Th><Table.Th ta="right">Downloads</Table.Th><Table.Th ta="right">Favs</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody style={{ opacity: loading ? 0.5 : 1 }}>
              {rows.map((w, i) => {
                const st = statusOf(w);
                return (
                  <Table.Tr key={w.id} onClick={(e) => (selecting ? toggleSelect(i, e.shiftKey) : setEditing(w))} style={{ cursor: 'pointer' }}
                    bg={selected.has(w.id) ? 'var(--mantine-primary-color-light)' : undefined}>
                    <Table.Td onClick={(e) => { e.stopPropagation(); toggleSelect(i, e.shiftKey); }}>
                      <Checkbox checked={selected.has(w.id)} onChange={() => undefined} aria-label={`Select ${w.title}`} />
                    </Table.Td>
                    <Table.Td>
                      <Image src={data.cdnUrl(w.storage_key)} w={40} h={64} radius="sm" fit="cover"
                        style={{ background: w.dominant_color ?? undefined }} fallbackSrc="data:image/gif;base64,R0lGODlhAQABAAAAACw=" />
                    </Table.Td>
                    <Table.Td><Text size="sm" fw={500}>{w.title}</Text><Text size="xs" c="dimmed">{w.width}×{w.height} · {w.storage_key}</Text></Table.Td>
                    <Table.Td>
                      <Badge color={STATUS_COLOR[st]} variant="light">{st}</Badge>
                      {st === 'scheduled' && <Text size="xs" c="dimmed">{dayjs(w.publish_at).format('D MMM HH:mm')}</Text>}
                    </Table.Td>
                    <Table.Td><Text size="sm">{catName(w.category_id)}</Text></Table.Td>
                    <Table.Td>
                      <Group gap={4}>
                        {w.is_curated && <Badge size="xs" color="brand">curated #{w.curated_order}</Badge>}
                        {w.is_amoled && <Badge size="xs" color="dark">amoled</Badge>}
                        {w.is_premium && <Badge size="xs" color="yellow">premium</Badge>}
                        <Badge size="xs" variant="outline" color="gray">{w.device_type}</Badge>
                      </Group>
                    </Table.Td>
                    <Table.Td><Text size="xs" c="dimmed" lineClamp={2}>{(w.wallpaper_tags ?? []).map((t) => t.tags?.name).join(', ')}</Text></Table.Td>
                    <Table.Td ta="right">{w.download_count}</Table.Td>
                    <Table.Td ta="right">{w.fav_count}</Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}

      {total > PAGE_SIZE && <Pagination total={Math.ceil(total / PAGE_SIZE)} value={page} onChange={setPage} radius="xl" />}

      {selecting && (
        <BulkBar count={selected.size} busy={bulkBusy} categories={data.categories} onAction={runBulk} onClear={() => setSelected(new Set())} />
      )}

      <EditDrawer wallpaper={editing} data={data} onClose={() => setEditing(null)}
        position={editIndex >= 0 ? `${editIndex + 1} of ${rows.length}` : undefined}
        onPrev={editIndex > 0 ? () => setEditing(rows[editIndex - 1]) : undefined}
        onNext={editIndex >= 0 && editIndex < rows.length - 1 ? () => setEditing(rows[editIndex + 1]) : undefined}
        onSaved={(goNext) => {
          load(); data.reload();
          if (goNext && editIndex >= 0 && editIndex < rows.length - 1) setEditing(rows[editIndex + 1]);
          else setEditing(null);
        }} />
    </Stack>
  );
}

function EditDrawer({ wallpaper, data, onClose, onSaved, onPrev, onNext, position }: {
  wallpaper: Wallpaper | null; data: AppData; onClose: () => void; onSaved: (goNext?: boolean) => void;
  onPrev?: () => void; onNext?: () => void; position?: string;
}) {
  const [meta, setMeta] = useState<WallpaperMeta | null>(null);
  const [busy, setBusy] = useState<'save' | 'delete' | null>(null);
  useEffect(() => { setMeta(wallpaper ? rowToMeta(wallpaper) : null); }, [wallpaper]);
  const dirty = !!wallpaper && !!meta && JSON.stringify(meta) !== JSON.stringify(rowToMeta(wallpaper));

  // ←/→ move between wallpapers when you're not typing (unsaved edits ask first).
  useEffect(() => {
    if (!wallpaper) return;
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (t.closest('input, textarea, [contenteditable], [role="combobox"], [role="listbox"]')) return;
      if (e.key === 'ArrowRight' && onNext) { e.preventDefault(); void go(onNext); }
      if (e.key === 'ArrowLeft' && onPrev) { e.preventDefault(); void go(onPrev); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  async function go(move: () => void) {
    if (dirty && !(await confirmAction({ title: 'Discard changes?', message: 'You have unsaved edits on this wallpaper.', confirmLabel: 'Discard', danger: true }))) return;
    move();
  }

  if (!wallpaper || !meta) return <Drawer opened={false} onClose={onClose} />;

  async function save(goNext = false) {
    setBusy('save');
    try {
      await updateWallpaper(wallpaper!, meta!);
      notifications.show({ color: 'green', message: 'Saved' });
      onSaved(goNext);
    } catch (e) {
      notifications.show({ color: 'red', title: 'Save failed', message: (e as Error).message });
    } finally {
      setBusy(null);
    }
  }

  async function remove() {
    if (!(await confirmAction({
      title: `Delete "${wallpaper!.title}"?`,
      message: 'It disappears from the app and its image files are removed from the CDN. This cannot be undone.',
      confirmLabel: 'Delete permanently', danger: true,
    }))) return;
    setBusy('delete');
    try {
      const warning = await deleteWallpaper(wallpaper!);
      notifications.show(warning ? { color: 'yellow', message: warning } : { color: 'green', message: 'Deleted' });
      onSaved();
    } catch (e) {
      notifications.show({ color: 'red', title: 'Delete failed', message: (e as Error).message });
    } finally {
      setBusy(null);
    }
  }

  const files = ['thumb.webp', 'preview.webp', 'full.webp', `original.${wallpaper.original_ext}`];

  return (
    <Drawer opened onClose={() => void go(onClose)} position="right" size="lg"
      title={
        <Group gap="xs" wrap="nowrap">
          <Tooltip label="Previous (←)"><ActionIcon variant="default" radius="xl" disabled={!onPrev} onClick={() => onPrev && void go(onPrev)} aria-label="Previous wallpaper"><IconChevronLeft size={16} /></ActionIcon></Tooltip>
          <Tooltip label="Next (→)"><ActionIcon variant="default" radius="xl" disabled={!onNext} onClick={() => onNext && void go(onNext)} aria-label="Next wallpaper"><IconChevronRight size={16} /></ActionIcon></Tooltip>
          <div style={{ minWidth: 0 }}>
            <Text fw={700} truncate>{wallpaper.title}</Text>
            {position && <Text size="xs" c="dimmed">{position}{dirty ? ' · unsaved changes' : ''}</Text>}
          </div>
        </Group>
      }>
      <Stack>
        <Group align="flex-start" wrap="nowrap">
          <Image src={data.cdnUrl(wallpaper.storage_key, 'preview.webp')} w={200} radius="lg" component="a" href={data.cdnUrl(wallpaper.storage_key, 'full.webp')} target="_blank"
            style={{ background: wallpaper.dominant_color ?? undefined }} />
          <Stack gap={4}>
            <Text size="sm">{wallpaper.width}×{wallpaper.height} · {wallpaper.file_size_kb} KB · colour {wallpaper.dominant_color} ({wallpaper.color_family})</Text>
            <Text size="sm">{wallpaper.download_count} downloads · {wallpaper.fav_count} favs · {wallpaper.view_count} views</Text>
            <Text size="xs" c="dimmed">Added {dayjs(wallpaper.created_at).format('D MMM YYYY')} · edited {dayjs(wallpaper.updated_at).format('D MMM YYYY HH:mm')}</Text>
            <Group gap={8}>
              {files.map((f) => (
                <Anchor key={f} size="xs" href={data.cdnUrl(wallpaper.storage_key, f)} target="_blank">{f.split('.')[0]}</Anchor>
              ))}
            </Group>
          </Stack>
        </Group>

        <WallpaperForm value={meta} onChange={setMeta} categories={data.categories} tagNames={data.tagNames} compact />

        <Group justify="space-between" mt="md">
          <Button color="red" variant="subtle" leftSection={<IconTrash size={16} />} loading={busy === 'delete'} onClick={remove}>Delete</Button>
          <Group gap="xs">
            {onNext && <Button variant="default" disabled={!isMetaValid(meta) || !dirty} loading={busy === 'save'} onClick={() => save(true)}>Save &amp; next</Button>}
            <Button variant="gradient" loading={busy === 'save'} disabled={!isMetaValid(meta) || !dirty} onClick={() => save(false)}>Save changes</Button>
          </Group>
        </Group>
      </Stack>
    </Drawer>
  );
}

function BulkBar({ count, busy, categories, onAction, onClear }: {
  count: number; busy: boolean; categories: AppData['categories']; onAction: (a: BulkAction) => void; onClear: () => void;
}) {
  return (
    <Card withBorder shadow="lg" radius="xl" padding="sm" className="bulk-bar">
      <Group gap="xs" wrap="wrap" justify="center">
        <Badge size="lg" variant="gradient" radius="xl">{count} selected</Badge>
        <Button size="xs" variant="light" leftSection={<IconEye size={14} />} loading={busy} onClick={() => onAction({ kind: 'publish' })}>Publish</Button>
        <Button size="xs" variant="light" color="gray" leftSection={<IconEyeOff size={14} />} disabled={busy} onClick={() => onAction({ kind: 'unpublish' })}>To drafts</Button>
        <Menu shadow="md" radius="md" withinPortal>
          <Menu.Target><Button size="xs" variant="light" color="gray" disabled={busy} rightSection={<IconChevronDown size={14} />}>Flags</Button></Menu.Target>
          <Menu.Dropdown>
            <Menu.Item leftSection={<IconStarFilled size={14} />} onClick={() => onAction({ kind: 'curate' })}>Add to Curated</Menu.Item>
            <Menu.Item leftSection={<IconStar size={14} />} onClick={() => onAction({ kind: 'uncurate' })}>Remove from Curated</Menu.Item>
            <Menu.Divider />
            <Menu.Item leftSection={<IconCrown size={14} />} onClick={() => onAction({ kind: 'premium', on: true })}>Mark Premium</Menu.Item>
            <Menu.Item onClick={() => onAction({ kind: 'premium', on: false })}>Remove Premium</Menu.Item>
            <Menu.Divider />
            <Menu.Item onClick={() => onAction({ kind: 'amoled', on: true })}>Mark AMOLED</Menu.Item>
            <Menu.Item onClick={() => onAction({ kind: 'amoled', on: false })}>Remove AMOLED</Menu.Item>
          </Menu.Dropdown>
        </Menu>
        <Menu shadow="md" radius="md" withinPortal>
          <Menu.Target><Button size="xs" variant="light" color="gray" disabled={busy} rightSection={<IconChevronDown size={14} />}>Category</Button></Menu.Target>
          <Menu.Dropdown>
            {categories.map((c) => <Menu.Item key={c.id} onClick={() => onAction({ kind: 'category', categoryId: c.id })}>{c.name}</Menu.Item>)}
            <Menu.Divider />
            <Menu.Item c="dimmed" onClick={() => onAction({ kind: 'category', categoryId: null })}>No category</Menu.Item>
          </Menu.Dropdown>
        </Menu>
        <Button size="xs" variant="light" color="red" leftSection={<IconTrash size={14} />} disabled={busy} onClick={() => onAction({ kind: 'delete' })}>Delete</Button>
        <ActionIcon variant="subtle" color="gray" radius="xl" onClick={onClear} aria-label="Clear selection"><IconX size={16} /></ActionIcon>
      </Group>
    </Card>
  );
}
