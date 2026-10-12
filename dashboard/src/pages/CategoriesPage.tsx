import { useState } from 'react';
import { ActionIcon, Button, Group, NumberInput, Stack, Table, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconTrash } from '../lib/icons';
import { supabase } from '../lib/supabase';
import type { Category } from '../lib/types';
import type { AppData } from '../lib/useAppData';
import { PageHeader } from '../components/Help';
import { confirmAction } from '../lib/confirm';

export default function CategoriesPage({ data }: { data: AppData }) {
  const [draft, setDraft] = useState<Record<string, Partial<Category>>>({});
  const [newName, setNewName] = useState('');

  const edit = (id: string, p: Partial<Category>) => setDraft((d) => ({ ...d, [id]: { ...d[id], ...p } }));

  async function save(c: Category) {
    const changes = draft[c.id];
    if (!changes) return;
    const { error } = await supabase.from('categories').update(changes).eq('id', c.id);
    if (error) return notifications.show({ color: 'red', message: error.message });
    setDraft((d) => { const n = { ...d }; delete n[c.id]; return n; });
    data.reload();
  }

  async function add() {
    const name = newName.trim();
    if (!name) return;
    const max = Math.max(0, ...data.categories.map((c) => c.sort_order));
    const { error } = await supabase.from('categories').insert({ name, sort_order: max + 10 });
    if (error) return notifications.show({ color: 'red', message: error.message });
    setNewName('');
    data.reload();
  }

  async function remove(c: Category) {
    if (!(await confirmAction({ title: `Delete category "${c.name}"?`, confirmLabel: 'Delete', danger: true, message: `Its wallpapers keep existing but lose this category.` }))) return;
    const { error } = await supabase.from('categories').delete().eq('id', c.id);
    if (error) return notifications.show({ color: 'red', message: error.message });
    data.reload();
  }

  return (
    <Stack>
      <PageHeader
        title="Categories"
        description="The main groups in the app's Explore tab. Each wallpaper belongs to one category; use tags for everything else."
        tips={[
          'Keep it to 8–15 broad categories (Nature, Space, Minimal…). Too many makes browsing harder.',
          '"Order" controls the position in the app: lower numbers first. Steps of 10 leave room to insert later.',
          'Icon = a Material Symbols name such as forest, rocket or palette (fonts.google.com/icons).',
          'Hindi name is optional: the app shows it when someone uses Zeul in Hindi, otherwise the English name.',
          'Deleting a category keeps its wallpapers; they just become uncategorised.',
          'Edit a row, then press the ✓ button to save it.',
        ]}
      />
      <Table>
        <Table.Thead>
          <Table.Tr><Table.Th>Name</Table.Th><Table.Th>Hindi name</Table.Th><Table.Th>Slug</Table.Th><Table.Th>Icon</Table.Th><Table.Th w={110}>Order</Table.Th><Table.Th w={80} /></Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {data.categories.map((c) => {
            const d = { ...c, ...draft[c.id] };
            return (
              <Table.Tr key={c.id}>
                <Table.Td><TextInput size="xs" value={d.name} onChange={(e) => edit(c.id, { name: e.currentTarget.value })} /></Table.Td>
                <Table.Td><TextInput size="xs" lang="hi" placeholder="optional" value={d.name_hi ?? ''} onChange={(e) => edit(c.id, { name_hi: e.currentTarget.value.trim() ? e.currentTarget.value : null })} /></Table.Td>
                <Table.Td><Text size="xs" c="dimmed">{c.slug}</Text></Table.Td>
                <Table.Td><TextInput size="xs" value={d.icon ?? ''} onChange={(e) => edit(c.id, { icon: e.currentTarget.value || null })} /></Table.Td>
                <Table.Td><NumberInput size="xs" value={d.sort_order} onChange={(v) => edit(c.id, { sort_order: Number(v) || 0 })} /></Table.Td>
                <Table.Td>
                  <Group gap={4} wrap="nowrap">
                    <ActionIcon variant="light" disabled={!draft[c.id]} onClick={() => save(c)}><IconCheck size={16} /></ActionIcon>
                    <ActionIcon variant="subtle" color="red" onClick={() => remove(c)}><IconTrash size={16} /></ActionIcon>
                  </Group>
                </Table.Td>
              </Table.Tr>
            );
          })}
        </Table.Tbody>
      </Table>
      <Group>
        <TextInput placeholder="New category name" value={newName} onChange={(e) => setNewName(e.currentTarget.value)}
          onKeyDown={(e) => e.key === 'Enter' && add()} />
        <Button onClick={add} disabled={!newName.trim()}>Add category</Button>
      </Group>
    </Stack>
  );
}
