import { useCallback, useEffect, useState } from 'react';
import dayjs from 'dayjs';
import { Badge, Button, Group, Image, SegmentedControl, Stack, Table, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { supabase } from '../lib/supabase';
import { deleteWallpaper } from '../lib/api';
import type { AppData } from '../lib/useAppData';
import { PageHeader } from '../components/Help';
import { confirmAction } from '../lib/confirm';

type Status = 'open' | 'resolved' | 'dismissed';
interface Report {
  id: string;
  reason: string;
  status: Status;
  created_at: string;
  device_id: string | null;
  wallpapers: { id: string; title: string; storage_key: string; dominant_color: string | null; is_published: boolean } | null;
}

export default function ReportsPage({ data }: { data: AppData }) {
  const [status, setStatus] = useState<Status>('open');
  const [rows, setRows] = useState<Report[]>([]);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    const { data: list, error } = await supabase
      .from('reports')
      .select('id,reason,status,created_at,device_id,wallpapers(id,title,storage_key,dominant_color,is_published)')
      .eq('status', status)
      .order('created_at', { ascending: false })
      .limit(200);
    if (error) notifications.show({ color: 'red', message: error.message });
    setRows((list ?? []) as unknown as Report[]);
  }, [status]);
  useEffect(() => { load(); }, [load]);

  async function run(r: Report, action: 'resolve' | 'dismiss' | 'unpublish' | 'delete') {
    setBusy(r.id + action);
    try {
      if (action === 'delete' && r.wallpapers) {
        if (!(await confirmAction({ title: `Delete "${r.wallpapers.title}"?`, message: 'It disappears from the app and its image files are removed from the CDN. This cannot be undone.', confirmLabel: 'Delete permanently', danger: true }))) return;
        const warning = await deleteWallpaper(r.wallpapers);
        if (warning) notifications.show({ color: 'yellow', message: warning });
        // reports cascade with the wallpaper
      } else {
        if (action === 'unpublish' && r.wallpapers) {
          const { error } = await supabase.from('wallpapers').update({ is_published: false }).eq('id', r.wallpapers.id);
          if (error) throw error;
        }
        const next: Status = action === 'dismiss' ? 'dismissed' : 'resolved';
        const { error } = await supabase.from('reports').update({ status: next }).eq('id', r.id);
        if (error) throw error;
      }
      load();
    } catch (e) {
      notifications.show({ color: 'red', message: (e as Error).message });
    } finally {
      setBusy(null);
    }
  }

  return (
    <Stack>
      <PageHeader
        title="Reports"
        description="Complaints users send from the app about a wallpaper: copyright, inappropriate content, low quality…"
        tips={[
          'Copyright complaints first: unpublish right away, then check your licence notes for that wallpaper.',
          'Unpublish hides it from the app but keeps the files. Delete removes everything and can\'t be undone.',
          'Dismiss spam or mistaken reports; Resolved = you fixed the problem.',
          'Open reports are counted on the Home page so you won\'t miss them.',
        ]}
        actions={
          <SegmentedControl radius="xl" value={status} onChange={(v) => setStatus(v as Status)}
            data={[{ value: 'open', label: 'Open' }, { value: 'resolved', label: 'Resolved' }, { value: 'dismissed', label: 'Dismissed' }]} />
        }
      />
      <Table.ScrollContainer minWidth={700}>
        <Table verticalSpacing={6}>
          <Table.Thead>
            <Table.Tr><Table.Th w={50} /><Table.Th>Wallpaper</Table.Th><Table.Th>Reason</Table.Th><Table.Th>When</Table.Th><Table.Th /></Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((r) => (
              <Table.Tr key={r.id}>
                <Table.Td>
                  {r.wallpapers && <Image src={data.cdnUrl(r.wallpapers.storage_key)} w={32} h={52} radius="sm" fit="cover"
                    style={{ background: r.wallpapers.dominant_color ?? undefined }} />}
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{r.wallpapers?.title ?? '(deleted)'}</Text>
                  {r.wallpapers && !r.wallpapers.is_published && <Badge size="xs" color="gray">unpublished</Badge>}
                </Table.Td>
                <Table.Td><Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{r.reason}</Text></Table.Td>
                <Table.Td><Text size="xs" c="dimmed">{dayjs(r.created_at).format('D MMM HH:mm')}</Text></Table.Td>
                <Table.Td>
                  {status === 'open' && (
                    <Group gap={4} wrap="nowrap" justify="flex-end">
                      <Button size="xs" variant="default" loading={busy === r.id + 'dismiss'} onClick={() => run(r, 'dismiss')}>Dismiss</Button>
                      <Button size="xs" variant="light" loading={busy === r.id + 'resolve'} onClick={() => run(r, 'resolve')}>Resolved</Button>
                      {r.wallpapers?.is_published && (
                        <Button size="xs" variant="light" color="orange" loading={busy === r.id + 'unpublish'} onClick={() => run(r, 'unpublish')}>Unpublish</Button>
                      )}
                      {r.wallpapers && (
                        <Button size="xs" variant="light" color="red" loading={busy === r.id + 'delete'} onClick={() => run(r, 'delete')}>Delete</Button>
                      )}
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {!rows.length && <Table.Tr><Table.Td colSpan={5}><Text c="dimmed" ta="center" py="lg">No {status} reports.</Text></Table.Td></Table.Tr>}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
    </Stack>
  );
}
