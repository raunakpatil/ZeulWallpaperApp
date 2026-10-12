import { useCallback, useEffect, useState } from 'react';
import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd';
import {
  ActionIcon, Badge, Button, Card, Grid, Group, Image, NavLink, NumberInput, Stack, Switch, Text, TextInput, Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconGripVertical, IconPhotoStar, IconPlus, IconTrash, IconX } from '../lib/icons';
import { supabase } from '../lib/supabase';
import { WallpaperPicker } from '../components/WallpaperPicker';
import type { AppData } from '../lib/useAppData';
import { PageHeader } from '../components/Help';
import { confirmAction } from '../lib/confirm';

interface Collection { id: string; title: string; cover_wallpaper_id: string | null; is_premium: boolean; sort_order: number }
interface Item { id: string; title: string; storage_key: string; dominant_color: string | null; is_published: boolean }

export default function CollectionsPage({ data }: { data: AppData }) {
  const [list, setList] = useState<Collection[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [newTitle, setNewTitle] = useState('');

  const load = useCallback(async () => {
    const { data: rows, error } = await supabase.from('collections').select('*').order('sort_order').order('created_at');
    if (error) notifications.show({ color: 'red', message: error.message });
    setList((rows ?? []) as Collection[]);
  }, []);
  useEffect(() => { load(); }, [load]);

  async function create() {
    const title = newTitle.trim();
    if (!title) return;
    const max = Math.max(0, ...list.map((c) => c.sort_order));
    const { data: row, error } = await supabase.from('collections').insert({ title, sort_order: max + 10 }).select('id').single();
    if (error) return notifications.show({ color: 'red', message: error.message });
    setNewTitle('');
    await load();
    setSelected(row.id);
  }

  const current = list.find((c) => c.id === selected) ?? null;

  return (
    <Stack>
      <PageHeader
        title="Collections"
        description='Themed packs like "Neon Tokyo" or "Minimal Mountains". The app shows a collection once it has at least one published wallpaper.'
        tips={[
          '8–15 wallpapers per pack works well: enough to browse, small enough to feel special.',
          'Use the ★ icon on an item to choose the cover. Otherwise the first item is used.',
          'Premium packs are visible to everyone; downloads follow each wallpaper\'s own Premium setting.',
          'You can feature a collection on the Home screen from App settings → Featured banner.',
        ]}
      />
      <Grid>
        <Grid.Col span={{ base: 12, md: 4 }}>
          <Card withBorder padding="xs">
            {list.map((c) => (
              <NavLink key={c.id} label={c.title} active={c.id === selected} onClick={() => setSelected(c.id)}
                rightSection={c.is_premium ? <Badge size="xs" color="yellow">premium</Badge> : undefined} />
            ))}
            {!list.length && <Text c="dimmed" size="sm" p="sm">No collections yet.</Text>}
            <Group mt="sm" gap="xs" wrap="nowrap">
              <TextInput size="xs" placeholder="New collection title" value={newTitle} style={{ flex: 1 }}
                onChange={(e) => setNewTitle(e.currentTarget.value)} onKeyDown={(e) => e.key === 'Enter' && create()} />
              <Button size="xs" leftSection={<IconPlus size={14} />} disabled={!newTitle.trim()} onClick={create}>Add</Button>
            </Group>
          </Card>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 8 }}>
          {current
            ? <CollectionEditor key={current.id} collection={current} data={data}
                onChanged={load} onDeleted={() => { setSelected(null); load(); }} />
            : <Text c="dimmed">Select or create a collection.</Text>}
        </Grid.Col>
      </Grid>
    </Stack>
  );
}

function CollectionEditor({ collection, data, onChanged, onDeleted }: {
  collection: Collection; data: AppData; onChanged: () => void; onDeleted: () => void;
}) {
  const [meta, setMeta] = useState(collection);
  const [items, setItems] = useState<Item[]>([]);
  const [adding, setAdding] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    supabase.from('collection_items')
      .select('position, wallpapers(id,title,storage_key,dominant_color,is_published)')
      .eq('collection_id', collection.id)
      .order('position')
      .then(({ data: rows }) => {
        setItems((rows ?? []).map((r) => r.wallpapers as unknown as Item).filter(Boolean));
        setDirty(false);
      });
  }, [collection.id]);

  const touch = () => setDirty(true);

  function onDragEnd(r: DropResult) {
    if (!r.destination) return;
    const next = [...items];
    const [moved] = next.splice(r.source.index, 1);
    next.splice(r.destination.index, 0, moved);
    setItems(next);
    touch();
  }

  async function addItem(id: string | null) {
    setAdding(null);
    if (!id || items.some((i) => i.id === id)) return;
    const { data: w } = await supabase.from('wallpapers').select('id,title,storage_key,dominant_color,is_published').eq('id', id).single();
    if (w) { setItems([...items, w as Item]); touch(); }
  }

  async function save() {
    setSaving(true);
    try {
      const { error: e1 } = await supabase.from('collections').update({
        title: meta.title.trim() || 'Untitled', is_premium: meta.is_premium, sort_order: meta.sort_order,
        cover_wallpaper_id: meta.cover_wallpaper_id && items.some((i) => i.id === meta.cover_wallpaper_id) ? meta.cover_wallpaper_id : null,
      }).eq('id', collection.id);
      if (e1) throw e1;
      const { error: e2 } = await supabase.from('collection_items').delete().eq('collection_id', collection.id);
      if (e2) throw e2;
      if (items.length) {
        const { error: e3 } = await supabase.from('collection_items').insert(
          items.map((w, i) => ({ collection_id: collection.id, wallpaper_id: w.id, position: i + 1 })),
        );
        if (e3) throw e3;
      }
      notifications.show({ color: 'green', message: 'Collection saved' });
      setDirty(false);
      onChanged();
    } catch (e) {
      notifications.show({ color: 'red', title: 'Save failed', message: (e as Error).message });
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!(await confirmAction({ title: `Delete collection "${collection.title}"?`, message: 'The wallpapers themselves are kept.', confirmLabel: 'Delete', danger: true }))) return;
    const { error } = await supabase.from('collections').delete().eq('id', collection.id);
    if (error) return notifications.show({ color: 'red', message: error.message });
    onDeleted();
  }

  const published = items.filter((i) => i.is_published).length;

  return (
    <Card withBorder>
      <Stack>
        <Group align="flex-end">
          <TextInput label="Title" style={{ flex: 1 }} value={meta.title} onChange={(e) => { setMeta({ ...meta, title: e.currentTarget.value }); touch(); }} />
          <NumberInput label="Order" w={100} value={meta.sort_order} onChange={(v) => { setMeta({ ...meta, sort_order: Number(v) || 0 }); touch(); }} />
          <Switch mb={8} label="Premium" checked={meta.is_premium} onChange={(e) => { setMeta({ ...meta, is_premium: e.currentTarget.checked }); touch(); }} />
        </Group>

        <Group gap="xs" wrap="nowrap" align="flex-end">
          <WallpaperPicker label="Add wallpaper" value={adding} onChange={addItem} style={{ flex: 1 }} />
        </Group>

        <Text size="sm" c="dimmed">{items.length} wallpapers ({published} published). Drag to reorder; the star sets the cover.</Text>
        <DragDropContext onDragEnd={onDragEnd}>
          <Droppable droppableId="items">
            {(drop) => (
              <Stack gap={6} ref={drop.innerRef} {...drop.droppableProps}>
                {items.map((w, i) => (
                  <Draggable key={w.id} draggableId={w.id} index={i}>
                    {(drag) => (
                      <Card withBorder padding={6} ref={drag.innerRef} {...drag.draggableProps}>
                        <Group wrap="nowrap">
                          <div {...drag.dragHandleProps}><IconGripVertical size={18} /></div>
                          <Image src={data.cdnUrl(w.storage_key)} w={32} h={52} radius="sm" fit="cover" style={{ background: w.dominant_color ?? undefined }} />
                          <Text size="sm" style={{ flex: 1 }}>{w.title}</Text>
                          {!w.is_published && <Badge color="gray" variant="light">draft</Badge>}
                          {meta.cover_wallpaper_id === w.id && <Badge variant="light">cover</Badge>}
                          <Tooltip label="Use as cover">
                            <ActionIcon variant="subtle" onClick={() => { setMeta({ ...meta, cover_wallpaper_id: w.id }); touch(); }}><IconPhotoStar size={16} /></ActionIcon>
                          </Tooltip>
                          <ActionIcon variant="subtle" color="gray" onClick={() => { setItems(items.filter((x) => x.id !== w.id)); touch(); }}><IconX size={16} /></ActionIcon>
                        </Group>
                      </Card>
                    )}
                  </Draggable>
                ))}
                {drop.placeholder}
              </Stack>
            )}
          </Droppable>
        </DragDropContext>

        <Group justify="space-between">
          <Button color="red" variant="subtle" leftSection={<IconTrash size={16} />} onClick={remove}>Delete collection</Button>
          <Button disabled={!dirty} loading={saving} onClick={save}>Save collection</Button>
        </Group>
      </Stack>
    </Card>
  );
}
