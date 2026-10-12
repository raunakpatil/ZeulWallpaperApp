import { useCallback, useEffect, useState } from 'react';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { Avatar, Badge, Card, Group, Pagination, SegmentedControl, Stack, Switch, Table, Text, TextInput, Tooltip } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconBrandGoogle, IconCrown, IconMail, IconSearch, IconShieldCheck, IconUsers } from '../lib/icons';
import { supabase } from '../lib/supabase';
import { EmptyState, PageHeader } from '../components/Help';

dayjs.extend(relativeTime);

interface UserRow {
  id: string; email: string; display_name: string | null; avatar_url: string | null; role: string;
  is_premium: boolean; premium_until: string | null; created_at: string; last_sign_in_at: string | null;
  provider: string; favorites: number; unlocks: number; uploads: number; upload_banned: boolean; total_count: number;
}

const PAGE_SIZE = 50;

export default function UsersPage() {
  const [q, setQ] = useState('');
  const [debounced] = useDebouncedValue(q, 300);
  const [filter, setFilter] = useState<'all' | 'premium' | 'admin'>('all');
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<UserRow[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    const { data, error } = await supabase.rpc('admin_list_users', { search: debounced || null, page: page - 1, page_size: PAGE_SIZE });
    setLoading(false);
    if (error) {
      notifications.show({
        color: 'red', title: 'Could not load users',
        message: error.message.includes('admin_list_users') ? 'Run migration 0800 in the Supabase SQL Editor first.' : error.message,
      });
      return;
    }
    setRows((data ?? []) as UserRow[]);
  }, [debounced, page]);

  useEffect(() => { load(); }, [load]);

  const setBanned = async (u: UserRow, banned: boolean) => {
    setRows((rs) => rs.map((r) => (r.id === u.id ? { ...r, upload_banned: banned } : r)));
    const { error } = await supabase.rpc('admin_set_upload_banned', { target: u.id, banned });
    if (error) {
      setRows((rs) => rs.map((r) => (r.id === u.id ? { ...r, upload_banned: !banned } : r)));
      notifications.show({ color: 'red', title: 'Could not change uploads', message: error.message });
      return;
    }
    notifications.show({ message: banned ? `${u.display_name || u.email} can no longer upload` : `${u.display_name || u.email} can upload again` });
  };
  useEffect(() => { setPage(1); }, [debounced]);

  const total = rows[0]?.total_count ?? 0;
  const shown = rows.filter((r) => filter === 'all' || (filter === 'premium' ? r.is_premium : r.role === 'admin'));

  return (
    <Stack>
      <PageHeader
        title={<>Users <Text span c="dimmed" fz={20} fw={500}>{total}</Text></>}
        description="People who created an account in the app. Guests (no account) aren't listed; they're most of your users."
        tips={[
          'Search by email or name, e.g. to find someone who asked for their account to be deleted.',
          'Deletion requests by email: ask the person to use Profile → Delete account in the app; it removes everything automatically.',
          'Premium comes from RevenueCat through the subscription webhook; "until" is when the current period ends.',
          'Favourites and unlocks count only what\'s synced to the account.',
          'Uploads: wallpapers the person published from the app. Turn off "Can upload" to block someone who breaks the rules (their existing uploads stay until you delete them on Wallpapers).',
        ]}
      />

      <Group gap="sm" wrap="wrap">
        <TextInput placeholder="Search email or name" leftSection={<IconSearch size={16} />} radius="xl" w={260}
          value={q} onChange={(e) => setQ(e.currentTarget.value)} />
        <SegmentedControl radius="xl" value={filter} onChange={(v) => setFilter(v as typeof filter)}
          data={[{ value: 'all', label: 'All' }, { value: 'premium', label: 'Premium' }, { value: 'admin', label: 'Admins' }]} />
      </Group>

      <Card withBorder className="soft-card" padding={0}>
        {!shown.length && !loading ? (
          <EmptyState icon={<IconUsers size={28} />} title={q || filter !== 'all' ? 'No users match' : 'No accounts yet'}>
            {q || filter !== 'all' ? 'Try another search or filter.' : 'Accounts appear here when people sign in from the app.'}
          </EmptyState>
        ) : (
          <Table.ScrollContainer minWidth={900}>
            <Table verticalSpacing="sm" highlightOnHover style={{ opacity: loading ? 0.5 : 1 }}>
              <Table.Thead>
                <Table.Tr><Table.Th>User</Table.Th><Table.Th>Sign-in</Table.Th><Table.Th>Plan</Table.Th>
                  <Table.Th ta="right">Favourites</Table.Th><Table.Th ta="right">Unlocks</Table.Th><Table.Th ta="right">Uploads</Table.Th><Table.Th>Can upload</Table.Th><Table.Th>Joined</Table.Th><Table.Th>Last seen</Table.Th></Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {shown.map((u) => (
                  <Table.Tr key={u.id}>
                    <Table.Td>
                      <Group gap="sm" wrap="nowrap">
                        <Avatar src={u.avatar_url} radius="xl" size={36} color="brand">{(u.display_name || u.email)[0]?.toUpperCase()}</Avatar>
                        <div style={{ minWidth: 0 }}>
                          <Group gap={6} wrap="nowrap">
                            <Text size="sm" fw={600} truncate>{u.display_name || u.email.split('@')[0]}</Text>
                            {u.role === 'admin' && <Tooltip label="Dashboard admin"><IconShieldCheck size={15} color="var(--mantine-color-brand-6)" /></Tooltip>}
                          </Group>
                          <Text size="xs" c="dimmed" truncate>{u.email}</Text>
                        </div>
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      <Badge variant="light" color={u.provider === 'google' ? 'blue' : 'gray'} radius="xl"
                        leftSection={u.provider === 'google' ? <IconBrandGoogle size={12} /> : <IconMail size={12} />}>
                        {u.provider === 'google' ? 'Google' : 'Email'}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      {u.is_premium
                        ? <Tooltip label={u.premium_until ? `Until ${dayjs(u.premium_until).format('D MMM YYYY')}` : 'No end date'}>
                            <Badge color="yellow" variant="light" radius="xl" leftSection={<IconCrown size={12} />}>Premium</Badge>
                          </Tooltip>
                        : <Text size="sm" c="dimmed">Free</Text>}
                    </Table.Td>
                    <Table.Td ta="right"><Text size="sm">{u.favorites}</Text></Table.Td>
                    <Table.Td ta="right"><Text size="sm">{u.unlocks}</Text></Table.Td>
                    <Table.Td ta="right"><Text size="sm">{u.uploads ?? 0}</Text></Table.Td>
                    <Table.Td>
                      <Switch size="sm" checked={!u.upload_banned} onChange={(e) => setBanned(u, !e.currentTarget.checked)}
                        aria-label={`Allow ${u.display_name || u.email} to upload`} />
                    </Table.Td>
                    <Table.Td><Text size="sm" title={dayjs(u.created_at).format('D MMM YYYY HH:mm')}>{dayjs(u.created_at).format('D MMM YYYY')}</Text></Table.Td>
                    <Table.Td><Text size="sm" c="dimmed">{u.last_sign_in_at ? dayjs(u.last_sign_in_at).fromNow() : '–'}</Text></Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
      {total > PAGE_SIZE && <Pagination total={Math.ceil(total / PAGE_SIZE)} value={page} onChange={setPage} radius="xl" />}
    </Stack>
  );
}
