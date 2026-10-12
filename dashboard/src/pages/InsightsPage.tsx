import { useCallback, useEffect, useState } from 'react';
import dayjs from 'dayjs';
import { Button, Card, Image, useComputedColorScheme, Loader, SegmentedControl, SimpleGrid, Stack, Table, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconRefresh } from '../lib/icons';
import { supabase } from '../lib/supabase';
import type { AppData } from '../lib/useAppData';
import { MultiTrendChart } from '../components/MultiTrendChart';
import { StatCard, type Tone } from '../components/StatCard';
import { IconDownload, IconEye, IconFlag, IconHeart, IconPhoto, IconUsers } from '../lib/icons';

const TONES: Tone[] = ['indigo', 'sky', 'green', 'rose', 'gold', 'lime'];
const STAT_ICONS = [<IconPhoto size={24} />, <IconDownload size={24} />, <IconEye size={24} />, <IconHeart size={24} />, <IconUsers size={24} />, <IconFlag size={24} />];

interface DayRow { day: string; views: number; downloads: number; applies: number; favs: number }
import { PageHeader } from '../components/Help';

interface Insights {
  days: number;
  totals: { published: number; scheduled: number; drafts: number; downloads: number; favs: number };
  users: number;
  premium_users: number;
  open_reports: number;
  window: { downloads: number; views: number; applies: number };
  top_wallpapers: { id: string; title: string; storage_key: string; dominant_color: string | null; downloads: number; views: number; applies: number }[];
  top_searches: { q: string; searches: number; avg_results: number }[];
  zero_result_searches: { q: string; searches: number; last_searched: string }[];
}

const fmt = (n: number) => n.toLocaleString('en-IN');

export default function InsightsPage({ data }: { data: AppData }) {
  const dark = useComputedColorScheme('light') === 'dark';
  const [days, setDays] = useState('30');
  const [ins, setIns] = useState<Insights | null>(null);
  const [recomputing, setRecomputing] = useState(false);
  const [series, setSeries] = useState<DayRow[]>([]);

  const load = useCallback(async () => {
    supabase.rpc('admin_daily_totals', { days: Number(days) }).then(({ data: d }) => setSeries((d ?? []) as DayRow[]));
    const { data: res, error } = await supabase.rpc('admin_insights', { days: Number(days) });
    if (error) return notifications.show({ color: 'red', title: 'Insights failed', message: error.message });
    setIns(res as Insights);
  }, [days]);
  useEffect(() => { load(); }, [load]);

  async function recompute() {
    setRecomputing(true);
    const { data: n, error } = await supabase.rpc('admin_recompute_popularity');
    setRecomputing(false);
    if (error) return notifications.show({ color: 'red', message: error.message });
    notifications.show({ color: 'green', message: `Popularity recomputed (${n} wallpapers changed). It also runs automatically every hour.` });
  }

  if (!ins) return <Loader />;

  const stats: [string, string, string?][] = [
    ['Published', fmt(ins.totals.published), `${ins.totals.drafts} drafts · ${ins.totals.scheduled} scheduled`],
    [`Downloads (${ins.days}d)`, fmt(ins.window.downloads), `${fmt(ins.totals.downloads)} all time`],
    [`Views (${ins.days}d)`, fmt(ins.window.views), `${fmt(ins.window.applies)} applied`],
    ['Favourites', fmt(ins.totals.favs), 'all time'],
    ['Accounts', fmt(ins.users), `${ins.premium_users} premium`],
    ['Open reports', fmt(ins.open_reports), ins.open_reports ? 'check the Reports page' : 'all clear'],
  ];

  return (
    <Stack>
      <PageHeader
        title="Insights"
        description="How the app is doing: downloads, what people search for, and what they can't find."
        tips={[
          '"Searches with no results" is your content to-do list. Upload wallpapers for those words.',
          'Top wallpapers show what people love; upload more in the same style.',
          'Popularity (used for AMOLED order and For You) updates automatically every hour. Recompute forces it now.',
          'Views are counted once per device every 30 minutes, and downloads once per hour, so numbers can\'t be inflated.',
        ]}
        actions={
          <>
            <SegmentedControl radius="xl" value={days} onChange={setDays} data={[{ value: '7', label: '7 days' }, { value: '30', label: '30 days' }, { value: '90', label: '90 days' }]} />
            <Button variant="default" leftSection={<IconRefresh size={16} />} loading={recomputing} onClick={recompute}>Recompute popularity</Button>
          </>
        }
      />

<SimpleGrid cols={{ base: 1, xs: 2, lg: 3 }}>
        {stats.map(([label, value, sub], i) => (
          <StatCard key={label} tone={TONES[i % TONES.length]} label={label} value={value} sub={sub} icon={STAT_ICONS[i]} />
        ))}
      </SimpleGrid>

      <Card withBorder className="soft-card" padding="lg">
        <Text fw={700} mb={4}>Activity</Text>
        {series.length
          ? <MultiTrendChart days={series.map((d) => d.day)} initiallyOn={['downloads', 'views']} series={[
              { key: 'downloads', label: 'Downloads', color: dark ? '#ff6a2b' : '#2a6159', values: series.map((d) => d.downloads) },
              { key: 'views', label: 'Views', color: dark ? '#ffcda0' : '#3a65a0', values: series.map((d) => d.views) },
              { key: 'applies', label: 'Set as wallpaper', color: dark ? '#ffb020' : '#c18f27', values: series.map((d) => d.applies) },
              { key: 'favs', label: 'Favourites', color: dark ? '#ff5c7a' : '#b0503a', values: series.map((d) => d.favs) },
            ]} />
          : <Text size="sm" c="dimmed">No data yet (or run migration 0800 in the Supabase SQL Editor).</Text>}
      </Card>

      <SimpleGrid cols={{ base: 1, lg: 3 }}>
        <Card withBorder>
          <Text fw={600} mb="xs">Top wallpapers ({ins.days}d)</Text>
          <Table verticalSpacing={4}>
            <Table.Tbody>
              {ins.top_wallpapers.map((w) => (
                <Table.Tr key={w.id}>
                  <Table.Td w={36}><Image src={data.cdnUrl(w.storage_key)} w={24} h={40} radius="xs" fit="cover" style={{ background: w.dominant_color ?? undefined }} /></Table.Td>
                  <Table.Td><Text size="sm" lineClamp={1}>{w.title}</Text></Table.Td>
                  <Table.Td ta="right"><Text size="sm">{fmt(w.downloads)}</Text><Text size="xs" c="dimmed">{fmt(w.views)} views</Text></Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          {!ins.top_wallpapers.length && <Text size="sm" c="dimmed">No activity yet.</Text>}
        </Card>

        <Card withBorder>
          <Text fw={600} mb="xs">Top searches</Text>
          <Table verticalSpacing={4}>
            <Table.Tbody>
              {ins.top_searches.map((s) => (
                <Table.Tr key={s.q}>
                  <Table.Td><Text size="sm">{s.q}</Text></Table.Td>
                  <Table.Td ta="right"><Text size="sm">{fmt(s.searches)}</Text><Text size="xs" c="dimmed">~{s.avg_results} results</Text></Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          {!ins.top_searches.length && <Text size="sm" c="dimmed">No searches yet.</Text>}
        </Card>

        <Card withBorder>
          <Text fw={600}>Searches with no results</Text>
          <Text size="xs" c="dimmed" mb="xs">What users want that you don't have yet. Upload these next.</Text>
          <Table verticalSpacing={4}>
            <Table.Tbody>
              {ins.zero_result_searches.map((s) => (
                <Table.Tr key={s.q}>
                  <Table.Td><Text size="sm" fw={500}>{s.q}</Text></Table.Td>
                  <Table.Td ta="right"><Text size="sm">{fmt(s.searches)}×</Text><Text size="xs" c="dimmed">{dayjs(s.last_searched).format('D MMM')}</Text></Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          {!ins.zero_result_searches.length && <Text size="sm" c="dimmed">None. Nice.</Text>}
        </Card>
      </SimpleGrid>
    </Stack>
  );
}
