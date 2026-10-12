import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  useComputedColorScheme, Anchor, Avatar, Button, Card, Collapse, Divider, Grid, Group, Progress, RingProgress, SimpleGrid, Skeleton, Stack, Text, ThemeIcon, Title, Tooltip, UnstyledButton,
} from '@mantine/core';
import {
  IconBulb, IconChevronDown, IconCircleCheckFilled, IconCircleDashed, IconCrown, IconDeviceTablet, IconDownload, IconFlag, IconMoon,
  IconPencil, IconPhoto, IconSparkles, IconStar, IconUpload, IconUsers,
} from '../lib/icons';
import { supabase } from '../lib/supabase';
import { HOME_TIPS } from '../lib/tips';
import { TEAM, avatarFor } from '../lib/avatars';
import type { AppData } from '../lib/useAppData';
import { TrendChart } from '../components/TrendChart';
import { StatCard } from '../components/StatCard';
import { MultiTrendChart } from '../components/MultiTrendChart';

interface Story { key: string; label: string; icon: React.ReactNode; count: number; thumb: string | null; to: string }
interface DayRow { day: string; views: number; downloads: number; applies: number; favs: number }
interface Recent { id: string; title: string; storage_key: string; dominant_color: string | null; is_published: boolean }

const PUBLISH_GOAL = 20;

async function countWhere(build: (q: ReturnType<typeof base>) => ReturnType<typeof base>) {
  const { count } = await build(base());
  return count ?? 0;
}
function base() {
  return supabase.from('wallpapers').select('id', { count: 'exact', head: true });
}
async function latestThumb(build: (q: ReturnType<typeof latestBase>) => ReturnType<typeof latestBase>) {
  const { data } = await build(latestBase()).limit(1);
  return (data?.[0]?.storage_key as string | undefined) ?? null;
}
function latestBase() {
  return supabase.from('wallpapers').select('storage_key').order('created_at', { ascending: false });
}

function greeting() {
  const h = new Date().getHours();
  return h < 5 ? 'Working late' : h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

export default function HomePage({ data, name }: { data: AppData; name: string }) {
  const dark = useComputedColorScheme('light') === 'dark';
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [stories, setStories] = useState<Story[]>([]);
  const [recent, setRecent] = useState<Recent[]>([]);
  const [counts, setCounts] = useState({ total: 0, published: 0, drafts: 0, curated: 0, amoled: 0, noLicence: 0, collections: 0 });
  const [dailySet, setDailySet] = useState(false);
  const [insights, setInsights] = useState<{ downloads: number; users: number; reports: number } | null>(null);
  const [series, setSeries] = useState<DayRow[]>([]);
  const [showDone, setShowDone] = useState(false);
  const [tip, setTip] = useState(() => Math.floor(Math.random() * HOME_TIPS.length));

  const load = useCallback(async () => {
    const now = new Date().toISOString();
    const live = (q: ReturnType<typeof base>) => q.eq('is_published', true).or(`publish_at.is.null,publish_at.lte.${now}`);
    const liveL = (q: ReturnType<typeof latestBase>) => q.eq('is_published', true).or(`publish_at.is.null,publish_at.lte.${now}`);
    const weekAgo = new Date(Date.now() - 7 * 864e5).toISOString();

    const [total, published, drafts, curated, amoled, tablet, premium, fresh, noLicence, cols, cfg, rec, ins,
      tCur, tAmo, tTab, tPre, tNew, tDraft] = await Promise.all([
      countWhere((q) => q),
      countWhere(live),
      countWhere((q) => q.eq('is_published', false)),
      countWhere((q) => live(q).eq('is_curated', true)),
      countWhere((q) => live(q).eq('is_amoled', true)),
      countWhere((q) => live(q).in('device_type', ['tablet', 'both'])),
      countWhere((q) => live(q).eq('is_premium', true)),
      countWhere((q) => live(q).gte('created_at', weekAgo)),
      countWhere((q) => q.is('license', null)),
      supabase.from('collections').select('id', { count: 'exact', head: true }),
      supabase.from('app_config').select('value').eq('key', 'daily_wallpaper_id').maybeSingle(),
      supabase.from('wallpapers').select('id,title,storage_key,dominant_color,is_published').order('created_at', { ascending: false }).limit(12),
      supabase.rpc('admin_insights', { days: 30 }),
      latestThumb((q) => liveL(q).eq('is_curated', true)),
      latestThumb((q) => liveL(q).eq('is_amoled', true)),
      latestThumb((q) => liveL(q).in('device_type', ['tablet', 'both'])),
      latestThumb((q) => liveL(q).eq('is_premium', true)),
      latestThumb((q) => liveL(q)),
      latestThumb((q) => q.eq('is_published', false)),
    ]);

    setCounts({ total, published, drafts, curated, amoled, noLicence, collections: cols.count ?? 0 });
    setDailySet(!!cfg.data?.value);
    setRecent((rec.data ?? []) as Recent[]);
    if (ins.data) {
      const d = ins.data as { window: { downloads: number }; users: number; open_reports: number };
      setInsights({ downloads: d.window.downloads, users: d.users, reports: d.open_reports });
    }
    setStories([
      { key: 'new', label: 'New', icon: <IconSparkles size={26} />, count: fresh, thumb: tNew, to: '/wallpapers?status=published' },
      { key: 'curated', label: 'Curated', icon: <IconStar size={26} />, count: curated, thumb: tCur, to: '/wallpapers?flag=is_curated' },
      { key: 'amoled', label: 'AMOLED', icon: <IconMoon size={26} />, count: amoled, thumb: tAmo, to: '/wallpapers?flag=is_amoled' },
      { key: 'tablet', label: 'Tablet', icon: <IconDeviceTablet size={26} />, count: tablet, thumb: tTab, to: '/wallpapers?flag=tablet' },
      { key: 'premium', label: 'Premium', icon: <IconCrown size={26} />, count: premium, thumb: tPre, to: '/wallpapers?flag=is_premium' },
      { key: 'drafts', label: 'Drafts', icon: <IconPencil size={26} />, count: drafts, thumb: tDraft, to: '/wallpapers?status=draft' },
    ]);
    setLoading(false);
  }, []);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    supabase.rpc('admin_daily_totals', { days: 30 }).then(({ data: d }) => setSeries((d ?? []) as DayRow[]));
  }, []);

  const checklist = useMemo(() => [
    { done: true, label: 'Become an admin', hint: 'You\'re in!' },
    { done: counts.total > 0, label: 'Upload your first wallpaper', hint: 'Drag images onto the Upload page.', to: '/upload' },
    { done: counts.published >= PUBLISH_GOAL, label: `Publish ${PUBLISH_GOAL} wallpapers`, hint: `${Math.min(counts.published, PUBLISH_GOAL)} of ${PUBLISH_GOAL} live. The app looks empty with fewer.`, to: '/wallpapers?status=draft', progress: Math.min(100, (counts.published / PUBLISH_GOAL) * 100) },
    { done: counts.curated >= 5, label: 'Pick 5+ Curated favourites', hint: 'Turn on "Curated" for your best work, then order them.', to: '/curated' },
    { done: counts.amoled >= 3, label: 'Add 3+ AMOLED wallpapers', hint: 'Mostly pure-black images; the uploader detects them.', to: '/upload' },
    { done: counts.collections > 0, label: 'Create a collection', hint: 'A themed pack like "Neon Nights".', to: '/collections' },
    { done: dailySet, label: 'Set a daily wallpaper', hint: 'App settings → Home screen.', to: '/settings' },
    { done: counts.total > 0 && counts.noLicence === 0, label: 'Record a licence for every wallpaper', hint: counts.noLicence ? `${counts.noLicence} missing a licence.` : 'Protects you from copyright complaints.', to: '/wallpapers' },
  ], [counts, dailySet]);

  const doneCount = checklist.filter((c) => c.done).length;
  const spark = series.map((r) => ({ day: r.day, value: r.downloads }));
  const todo = checklist.filter((c) => !c.done);
  const done = checklist.filter((c) => c.done);
  const views30 = series.reduce((n, r) => n + r.views, 0);

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Text c="dimmed" size="sm">{greeting()},</Text>
          <Title order={1} fz={30}><span className="ig-text">{name}</span><span className="retro-only retro-cursor" aria-hidden>_</span></Title>
          <Text c="dimmed" size="sm" mt={2}>Here's what's happening in Zeul.</Text>
        </div>
        <Group gap="md">
          <Tooltip.Group openDelay={100}>
            <Avatar.Group spacing="sm">
              {TEAM.map((m) => (
                <Tooltip key={m.email} label={`${m.name} · ${m.role}`}>
                  <span className="ig-ring" style={{ padding: 2 }}>
                    <Avatar src={avatarFor(m.email)} alt={m.name} radius="xl" size={40} color="brand"
                      style={{ border: '2px solid var(--mantine-color-body)' }}>{m.name[0]}</Avatar>
                  </span>
                </Tooltip>
              ))}
            </Avatar.Group>
          </Tooltip.Group>
          <Button component={Link} to="/upload" variant="gradient" size="md" leftSection={<IconUpload size={18} />}>Upload wallpapers</Button>
        </Group>
      </Group>

      {/* Stats */}
      <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
        <StatCard tone="indigo" label="Live wallpapers" icon={<IconPhoto size={20} />} to="/wallpapers"
          value={loading ? '–' : counts.published.toLocaleString('en-IN')} sub={`${counts.drafts} drafts`} />
        <StatCard tone="sky" label="Downloads · 30 days" icon={<IconDownload size={20} />} to="/insights"
          value={loading ? '–' : (insights?.downloads ?? 0).toLocaleString('en-IN')}>
          {spark.length > 1 && <TrendChart points={spark} compact height={34} label="downloads" color={dark ? '#ff6a2b' : '#2a6159'} />}
        </StatCard>
        <StatCard tone="green" label="Accounts" icon={<IconUsers size={20} />} to="/users"
          value={loading ? '–' : (insights?.users ?? 0).toLocaleString('en-IN')} sub="signed-up users" />
        <StatCard tone={insights?.reports ? 'rose' : 'lime'} label="Open reports" icon={<IconFlag size={20} />} to="/reports"
          value={loading ? '–' : (insights?.reports ?? 0).toLocaleString('en-IN')} sub={insights?.reports ? 'needs a look' : 'all clear'} />
      </SimpleGrid>

      {/* Library: sections + recent uploads */}
      <Card withBorder className="soft-card" padding="lg">
        <Group justify="space-between" mb="md">
          <div>
            <Text fw={700} size="lg">Library</Text>
            <Text size="sm" c="dimmed">{counts.total.toLocaleString('en-IN')} wallpapers in total</Text>
          </div>
          <Anchor component={Link} to="/wallpapers" size="sm">See all</Anchor>
        </Group>
        <Group gap="lg" wrap="nowrap" style={{ overflowX: 'auto', paddingBottom: 4 }}>
          {(loading ? Array.from({ length: 6 }) : stories).map((s, i) => {
            const st = s as Story | undefined;
            if (!st) return <Stack key={i} align="center" gap={6}><Skeleton circle height={72} /><Skeleton height={10} width={50} /></Stack>;
            return (
              <button key={st.key} type="button" className="ig-story" onClick={() => navigate(st.to)} aria-label={`${st.label}: ${st.count}`}>
                <Stack align="center" gap={6}>
                  <span className={`ig-ring${st.count ? '' : ' muted'}`}>
                    <span className="ig-ring-inner">
                      {st.thumb ? <img src={data.cdnUrl(st.thumb)} alt="" /> : <span style={{ color: 'var(--mantine-color-dimmed)' }}>{st.icon}</span>}
                    </span>
                  </span>
                  <Text size="xs" fw={600}>{st.label}</Text>
                  <Text size="xs" c="dimmed" mt={-6}>{st.count}</Text>
                </Stack>
              </button>
            );
          })}
        </Group>
        <Divider my="md" label="Recent uploads" labelPosition="left" />
        {recent.length ? (
          <div className="ig-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(110px, 1fr))' }}>
            {recent.map((w) => (
              <button key={w.id} type="button" className="ig-tile" onClick={() => navigate(`/wallpapers?edit=${w.id}`)} aria-label={w.title}
                style={{ background: w.dominant_color ?? undefined }}>
                <img src={data.cdnUrl(w.storage_key)} alt="" loading="lazy" />
                <div className="ig-overlay"><Text size="xs" fw={600} c="white" lineClamp={3}>{w.title}{w.is_published ? '' : ' (draft)'}</Text></div>
              </button>
            ))}
          </div>
        ) : (
          <Stack align="center" gap={6} py="lg">
            <Text size="sm" c="dimmed">Nothing yet. Your uploads will appear here.</Text>
            <Button component={Link} to="/upload" variant="default" size="xs" leftSection={<IconUpload size={14} />}>Upload</Button>
          </Stack>
        )}
      </Card>

      <Grid gutter="lg" align="stretch">
        {/* Activity */}
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Card withBorder className="soft-card" padding="lg" h="100%">
            <Group justify="space-between" align="flex-start" mb="xs" wrap="wrap">
              <div>
                <Text fw={700} size="lg">Activity</Text>
                <Text size="sm" c="dimmed">Last 30 days in the app</Text>
              </div>
              <Group gap="xl" align="flex-start">
                <div className="kpi-mini">
                  <Text size="xs" c="dimmed" fw={600}>Downloads</Text>
                  <Text fw={700} fz={22} lh={1.2}>{(insights?.downloads ?? 0).toLocaleString('en-IN')}</Text>
                </div>
                <div className="kpi-mini">
                  <Text size="xs" c="dimmed" fw={600}>Views</Text>
                  <Text fw={700} fz={22} lh={1.2}>{views30.toLocaleString('en-IN')}</Text>
                </div>
                <Anchor component={Link} to="/insights" size="sm">Insights →</Anchor>
              </Group>
            </Group>
            {series.length > 1
              ? <MultiTrendChart days={series.map((d) => d.day)} height={240} initiallyOn={['downloads', 'views']} series={[
                  { key: 'downloads', label: 'Downloads', color: dark ? '#ff6a2b' : '#2a6159', values: series.map((d) => d.downloads) },
                  { key: 'views', label: 'Views', color: dark ? '#ffcda0' : '#3a65a0', values: series.map((d) => d.views) },
                  { key: 'applies', label: 'Set as wallpaper', color: dark ? '#ffb020' : '#c18f27', values: series.map((d) => d.applies) },
                ]} />
              : <Stack align="center" justify="center" gap={4} h={240}>
                  <Text fw={600}>No activity yet</Text>
                  <Text size="sm" c="dimmed" ta="center" maw={320}>Downloads and views show up here once people start using the app.</Text>
                </Stack>}
          </Card>
        </Grid.Col>

        {/* Checklist + tip */}
        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Card withBorder className="soft-card" padding="lg" h="100%" style={{ display: 'flex', flexDirection: 'column' }}>
            <Group justify="space-between" mb="md" wrap="nowrap">
              <div>
                <Text fw={700} size="lg">Getting started</Text>
                <Text size="sm" c="dimmed">{todo.length ? `${todo.length} step${todo.length === 1 ? '' : 's'} left` : 'All done. Nice work.'}</Text>
              </div>
              <div className="modern-only">
                <RingProgress size={56} thickness={5} roundCaps sections={[{ value: (doneCount / checklist.length) * 100, color: 'brand' }]}
                  label={<Text ta="center" size="xs" fw={700}>{doneCount}/{checklist.length}</Text>} />
              </div>
              <div className="retro-only" style={{ width: 130 }}>
                <Text size="xs" fw={700} ta="right" mb={4}>{doneCount} of {checklist.length} done</Text>
                <Progress value={(doneCount / checklist.length) * 100} />
              </div>
            </Group>
            <Stack gap={12}>
              {todo.map((c) => (
              <Group key={c.label} gap="sm" wrap="nowrap" align="flex-start" className="task-row">
                <span className="check-mark" data-done={c.done || undefined} aria-label={c.done ? 'Done' : 'Not done yet'}>
                  {c.done
                    ? <IconCircleCheckFilled size={20} style={{ color: 'var(--mantine-color-green-6)', flexShrink: 0 }} />
                    : <IconCircleDashed size={20} style={{ color: 'var(--mantine-color-dimmed)', flexShrink: 0 }} />}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  {c.to && !c.done
                    ? <Anchor component={Link} to={c.to} fw={600} size="sm">{c.label}</Anchor>
                    : <Text fw={600} size="sm" td={c.done ? 'line-through' : undefined} c={c.done ? 'dimmed' : undefined}>{c.label}</Text>}
                  {!c.done && <Text size="xs" c="dimmed">{c.hint}</Text>}
                  {!c.done && c.progress !== undefined && <Progress value={c.progress} size="sm" radius="xl" mt={4} />}
                </div>
              </Group>
            ))}
            </Stack>
            {done.length > 0 && (
              <>
                <UnstyledButton className="done-toggle" mt={todo.length ? 'sm' : 0} onClick={() => setShowDone((v) => !v)} aria-expanded={showDone}>
                  <Group gap={6}>
                    <IconChevronDown size={14} style={{ transform: showDone ? 'rotate(180deg)' : undefined, transition: 'transform 150ms' }} />
                    <Text size="xs" fw={600} c="dimmed">{showDone ? 'Hide' : 'Show'} {done.length} completed</Text>
                  </Group>
                </UnstyledButton>
                <Collapse in={showDone}>
                  <Stack gap={8} mt="xs">
                    {done.map((c) => (
              <Group key={c.label} gap="sm" wrap="nowrap" align="flex-start" className="task-row">
                <span className="check-mark" data-done={c.done || undefined} aria-label={c.done ? 'Done' : 'Not done yet'}>
                  {c.done
                    ? <IconCircleCheckFilled size={20} style={{ color: 'var(--mantine-color-green-6)', flexShrink: 0 }} />
                    : <IconCircleDashed size={20} style={{ color: 'var(--mantine-color-dimmed)', flexShrink: 0 }} />}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  {c.to && !c.done
                    ? <Anchor component={Link} to={c.to} fw={600} size="sm">{c.label}</Anchor>
                    : <Text fw={600} size="sm" td={c.done ? 'line-through' : undefined} c={c.done ? 'dimmed' : undefined}>{c.label}</Text>}
                  {!c.done && <Text size="xs" c="dimmed">{c.hint}</Text>}
                  {!c.done && c.progress !== undefined && <Progress value={c.progress} size="sm" radius="xl" mt={4} />}
                </div>
              </Group>
            ))}
                  </Stack>
                </Collapse>
              </>
            )}
            <div style={{ marginTop: 'auto' }}>
              <Divider my="md" />
              <Group gap="sm" wrap="nowrap" align="flex-start">
                <ThemeIcon variant="light" radius="md" size="md"><IconBulb size={16} /></ThemeIcon>
                <div style={{ flex: 1 }}>
                  <Text size="sm">{HOME_TIPS[tip]}</Text>
                  <Anchor size="xs" component="button" type="button" mt={4} onClick={() => setTip((t) => (t + 1) % HOME_TIPS.length)}>Next tip →</Anchor>
                </div>
              </Group>
            </div>
          </Card>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
