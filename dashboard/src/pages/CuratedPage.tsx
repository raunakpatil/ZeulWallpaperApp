import { useCallback, useEffect, useState } from 'react';
import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd';
import { ActionIcon, Badge, Button, Card, Group, Image, Stack, Text, Tooltip } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconGripVertical, IconStarOff } from '../lib/icons';
import { supabase } from '../lib/supabase';
import type { AppData } from '../lib/useAppData';
import { PageHeader } from '../components/Help';

interface Row {
  id: string;
  title: string;
  storage_key: string;
  dominant_color: string | null;
  curated_order: number | null;
  is_published: boolean;
}

export default function CuratedPage({ data }: { data: AppData }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    const { data: list, error } = await supabase
      .from('wallpapers')
      .select('id,title,storage_key,dominant_color,curated_order,is_published')
      .eq('is_curated', true)
      .order('curated_order', { ascending: true, nullsFirst: false })
      .order('created_at', { ascending: false });
    if (error) notifications.show({ color: 'red', message: error.message });
    setRows((list ?? []) as Row[]);
    setDirty(false);
  }, []);

  useEffect(() => { load(); }, [load]);

  function onDragEnd(r: DropResult) {
    if (!r.destination || r.destination.index === r.source.index) return;
    const next = [...rows];
    const [moved] = next.splice(r.source.index, 1);
    next.splice(r.destination.index, 0, moved);
    setRows(next);
    setDirty(true);
  }

  async function save() {
    setSaving(true);
    const results = await Promise.all(
      rows.map((row, i) => supabase.from('wallpapers').update({ curated_order: i + 1 }).eq('id', row.id)),
    );
    setSaving(false);
    const failed = results.find((x) => x.error);
    if (failed?.error) notifications.show({ color: 'red', title: 'Save failed', message: failed.error.message });
    else notifications.show({ color: 'green', message: 'Curated order saved' });
    load();
  }

  async function uncurate(id: string) {
    const { error } = await supabase.from('wallpapers').update({ is_curated: false, curated_order: null }).eq('id', id);
    if (error) notifications.show({ color: 'red', message: error.message });
    else setRows((r) => r.filter((x) => x.id !== id));
  }

  return (
    <Stack maw={1100}>
      <PageHeader
        title="Curated order"
        description="Your hand-picked best wallpapers, in the order the app shows them. Drag to reorder: top = first."
        tips={[
          'Curated is the first section new users see, so keep it small (20–40) and excellent.',
          'To add a wallpaper, open it on the Wallpapers page and switch on "Curated". New picks go to the bottom.',
          'Mix colours and styles in the first 6. They decide whether someone keeps scrolling.',
          'Drafts can be curated in advance; they appear once published.',
        ]}
        actions={<Button variant="gradient" disabled={!dirty} loading={saving} onClick={save}>Save order</Button>}
      />

      <DragDropContext onDragEnd={onDragEnd}>
        <Droppable droppableId="curated">
          {(drop) => (
            <Stack gap={6} ref={drop.innerRef} {...drop.droppableProps}>
              {rows.map((row, i) => (
                <Draggable key={row.id} draggableId={row.id} index={i}>
                  {(drag) => (
                    <Card withBorder padding={6} ref={drag.innerRef} {...drag.draggableProps}>
                      <Group wrap="nowrap">
                        <div {...drag.dragHandleProps}><IconGripVertical size={18} /></div>
                        <Text w={28} c="dimmed" size="sm">#{i + 1}</Text>
                        <Image src={data.cdnUrl(row.storage_key)} w={32} h={52} radius="sm" fit="cover"
                          style={{ background: row.dominant_color ?? undefined }} />
                        <Text style={{ flex: 1 }} size="sm">{row.title}</Text>
                        {!row.is_published && <Badge color="gray" variant="light">draft</Badge>}
                        <Tooltip label="Remove from curated">
                          <ActionIcon variant="subtle" color="gray" onClick={() => uncurate(row.id)}><IconStarOff size={16} /></ActionIcon>
                        </Tooltip>
                      </Group>
                    </Card>
                  )}
                </Draggable>
              ))}
              {drop.placeholder}
              {!rows.length && <Text c="dimmed" ta="center" py="xl">No curated wallpapers yet.</Text>}
            </Stack>
          )}
        </Droppable>
      </DragDropContext>
    </Stack>
  );
}
