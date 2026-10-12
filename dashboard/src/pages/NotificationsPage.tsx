import { useCallback, useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import {
  Alert, Badge, Button, Card, Chip, Grid, Group, List, Modal, SegmentedControl, Select, SimpleGrid, Stack, Switch, Table,
  Text, Textarea, TextInput, ThemeIcon,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconBell, IconBellRinging, IconCalendarTime, IconFlask, IconSend, IconUsers,
} from '../lib/icons';
import { supabase } from '../lib/supabase';
import { pushSetupStatus, sendDailyNow, sendPush, type PushEnv, type Topic } from '../lib/push';
import { EmptyState, HelpTip, LabelWithTip, PageHeader } from '../components/Help';
import { WallpaperPicker } from '../components/WallpaperPicker';
import { BrandMark } from '../components/BrandMark';
import type { AppData } from '../lib/useAppData';

dayjs.extend(relativeTime);

const TITLE_MAX = 65;
const BODY_MAX = 240;

const AUDIENCES: { value: Topic; label: string; hint: string }[] = [
  { value: 'new_drops', label: 'New-wallpaper fans', hint: 'Users who kept "New wallpapers" on (the default). Best for drops and collections.' },
  { value: 'daily_wallpaper', label: 'Daily fans', hint: 'Users who kept "Daily wallpaper" on.' },
  { value: 'all', label: 'Everyone', hint: 'Every install, including people who turned the other two off. Use only for important announcements.' },
];
const SECTION_OPTIONS = [
  { value: 'new', label: 'New' }, { value: 'curated', label: 'Curated' }, { value: 'popular', label: 'Popular' },
  { value: 'amoled', label: 'AMOLED' }, { value: 'tablet', label: 'Tablet' }, { value: 'premium', label: 'Premium' }, { value: 'for_you', label: 'For You' },
];

type OpenKind = 'home' | 'wallpaper' | 'collection' | 'section';

interface LogRow {
  id: string; created_at: string; kind: string; env: PushEnv; topic: string; title: string; body: string;
  image_url: string | null; status: 'sent' | 'failed'; error: string | null; opens: number;
}
interface DailyCfg { enabled: boolean; hour_utc: number; auto_pick: boolean; title: string }

/** Labels for each UTC hour shown in the admin's own time zone (e.g. 04:00 UTC → "9:30 AM" in India). */
const hourOptions = () => Array.from({ length: 24 }, (_, h) => {
  const d = new Date(Date.UTC(2026, 0, 1, h, 0));
  return { value: String(h), label: d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) };
});

export default function NotificationsPage({ data }: { data: AppData }) {
  const [setup, setSetup] = useState<Awaited<ReturnType<typeof pushSetupStatus>> | 'loading'>('loading');
  const [env, setEnv] = useState<PushEnv>('debug');
  const [topic, setTopic] = useState<Topic>('new_drops');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [open, setOpen] = useState<OpenKind>('home');
  const [wallpaperId, setWallpaperId] = useState<string | null>(null);
  const [collectionId, setCollectionId] = useState<string | null>(null);
  const [section, setSection] = useState<string | null>('new');
  const [collections, setCollections] = useState<{ value: string; label: string; cover: string | null }[]>([]);
  const [preview, setPreview] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [sending, setSending] = useState(false);
  const [log, setLog] = useState<LogRow[]>([]);
  const [daily, setDaily] = useState<DailyCfg | null>(null);
  const [dailySaved, setDailySaved] = useState<string>('');
  const [busyDaily, setBusyDaily] = useState<string | null>(null);

  const loadLog = useCallback(async () => {
    const { data: rows } = await supabase.from('push_log')
      .select('id,created_at,kind,env,topic,title,body,image_url,status,error,opens')
      .order('created_at', { ascending: false }).limit(50);
    setLog((rows ?? []) as LogRow[]);
  }, []);

  useEffect(() => {
    pushSetupStatus().then(setSetup);
    loadLog();
    supabase.rpc('get_collections').then(({ data: cols }) =>
      setCollections(((cols ?? []) as { id: string; title: string; cover_storage_key: string | null }[])
        .map((c) => ({ value: c.id, label: c.title, cover: c.cover_storage_key }))));
    supabase.from('app_config').select('value').eq('key', 'daily_push').maybeSingle().then(({ data: row }) => {
      const v = { enabled: false, hour_utc: 4, auto_pick: true, title: "Today's wallpaper ✨", ...(row?.value ?? {}) } as DailyCfg;
      setDaily(v);
      setDailySaved(JSON.stringify(v));
    });
  }, [loadLog]);

  // Image for the preview, same rule as the server: wallpaper preview or collection cover.
  useEffect(() => {
    if (open === 'wallpaper' && wallpaperId) {
      supabase.from('wallpapers').select('storage_key').eq('id', wallpaperId).maybeSingle()
        .then(({ data: w }) => setPreview(w ? data.cdnUrl(w.storage_key, 'preview.webp') : null));
    } else if (open === 'collection' && collectionId) {
      const c = collections.find((x) => x.value === collectionId);
      setPreview(c?.cover ? data.cdnUrl(c.cover, 'preview.webp') : null);
    } else {
      setPreview(null);
    }
  }, [open, wallpaperId, collectionId, collections, data]);

  const audience = AUDIENCES.find((a) => a.value === topic)!;
  const targetMissing = (open === 'wallpaper' && !wallpaperId) || (open === 'collection' && !collectionId) || (open === 'section' && !section);
  const canSend = !!title.trim() && title.length <= TITLE_MAX && body.length <= BODY_MAX && !targetMissing && setup !== 'loading' && !!setup?.configured;
  const prodToday = useMemo(() => log.filter((r) => r.env === 'prod' && r.kind === 'manual' && r.status === 'sent'
    && dayjs(r.created_at).isAfter(dayjs().subtract(24, 'hour'))).length, [log]);

  function template(kind: 'drop' | 'collection' | 'announce') {
    if (kind === 'drop') {
      setTitle('🔥 New wallpapers just dropped'); setBody('Fresh picks are waiting for you. Tap to take a look.');
      setTopic('new_drops'); setOpen('section'); setSection('new');
    } else if (kind === 'collection') {
      const c = collections[0];
      setTitle(`✨ New collection${c ? `: ${c.label}` : ''}`); setBody('A brand-new themed pack, hand-picked for you.');
      setTopic('new_drops'); setOpen('collection'); setCollectionId(c?.value ?? null);
    } else {
      setTitle('📣 '); setBody(''); setTopic('all'); setOpen('home');
    }
  }

  async function send() {
    setConfirming(false);
    setSending(true);
    try {
      await sendPush({
        title: title.trim(), body: body.trim(), topic, env,
        wallpaper_id: open === 'wallpaper' ? wallpaperId : null,
        collection_id: open === 'collection' ? collectionId : null,
        section: open === 'section' ? section : null,
      });
      notifications.show({
        color: 'green', title: env === 'debug' ? 'Test sent' : 'Notification sent',
        message: env === 'debug' ? 'Phones running a debug build should get it within a few seconds.' : `On its way to "${audience.label}".`,
      });
      if (env === 'prod') { setTitle(''); setBody(''); }
    } catch (e) {
      notifications.show({ color: 'red', title: 'Not sent', message: (e as Error).message, autoClose: 10000 });
    } finally {
      setSending(false);
      loadLog();
    }
  }

  async function saveDaily() {
    if (!daily) return;
    setBusyDaily('save');
    const { error } = await supabase.from('app_config').upsert({ key: 'daily_push', value: daily }, { onConflict: 'key' });
    setBusyDaily(null);
    if (error) return notifications.show({ color: 'red', message: error.message });
    setDailySaved(JSON.stringify(daily));
    notifications.show({ color: 'green', message: daily.enabled ? 'Daily push scheduled' : 'Daily push turned off' });
  }

  async function dailyNow(e: PushEnv) {
    setBusyDaily(e);
    try {
      const r = await sendDailyNow(e);
      notifications.show(r.skipped
        ? { color: 'yellow', message: r.skipped }
        : { color: 'green', message: e === 'debug' ? 'Daily test sent to debug builds' : "Today's wallpaper sent" });
    } catch (err) {
      notifications.show({ color: 'red', title: 'Not sent', message: (err as Error).message, autoClose: 10000 });
    } finally {
      setBusyDaily(null);
      loadLog();
    }
  }

  return (
    <Stack>
      <PageHeader
        title="Notifications"
        description="Send push notifications to people who installed the app, and schedule the automatic daily wallpaper."
        tips={[
          'Keep Test mode on while you try things. Test pushes only reach phones running a debug build (yours and Ghanshyam\'s).',
          '1–3 notifications a week is the sweet spot. More than that and people turn notifications off or uninstall.',
          'Pushes with a picture get noticeably more taps. Pick a wallpaper or a collection to add one automatically.',
          'Short titles win: about 40 characters shows fully on most phones.',
          '"Opens" counts how many devices tapped the notification (each device once).',
          `Safety limit: at most 6 notifications to real users per 24 hours.`,
        ]}
      />

      {setup !== 'loading' && !setup?.configured && <SetupGuide deployed={setup !== null} />}

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 7 }}>
          <Card withBorder className="soft-card" padding="lg">
            <Group justify="space-between" mb="sm" wrap="wrap">
              <Text fw={700} size="lg">Compose</Text>
              <Group gap={6}>
                <Text size="xs" c="dimmed">Templates:</Text>
                <Chip size="xs" checked={false} onChange={() => template('drop')}>New drop</Chip>
                <Chip size="xs" checked={false} onChange={() => template('collection')}>Collection</Chip>
                <Chip size="xs" checked={false} onChange={() => template('announce')}>Announcement</Chip>
              </Group>
            </Group>
            <Stack gap="sm">
              <Card withBorder radius="md" padding="sm" bg={env === 'debug' ? 'var(--mantine-color-yellow-light)' : 'var(--mantine-color-red-light)'}>
                <Group justify="space-between" wrap="nowrap">
                  <Group gap="sm" wrap="nowrap">
                    <ThemeIcon variant="light" color={env === 'debug' ? 'yellow' : 'red'} radius="xl">
                      {env === 'debug' ? <IconFlask size={18} /> : <IconUsers size={18} />}
                    </ThemeIcon>
                    <div>
                      <Text fw={600} size="sm">{env === 'debug' ? 'Test mode: only your debug phones' : 'Live: real users will get this'}</Text>
                      <Text size="xs" c="dimmed">{env === 'debug' ? 'Turn off when you\'re happy with how it looks.' : `${prodToday} of 6 live sends used in the last 24 h.`}</Text>
                    </div>
                  </Group>
                  <Switch checked={env === 'debug'} onChange={(e) => setEnv(e.currentTarget.checked ? 'debug' : 'prod')} color="yellow" aria-label="Test mode" />
                </Group>
              </Card>

              <div>
                <Text size="sm" fw={500} mb={4}><LabelWithTip label="Who gets it" tip={audience.hint} /></Text>
                <SegmentedControl fullWidth radius="xl" value={topic} onChange={(v) => setTopic(v as Topic)}
                  data={AUDIENCES.map((a) => ({ value: a.value, label: a.label }))} />
              </div>

              <TextInput label="Title" placeholder="🔥 New AMOLED wallpapers" value={title} maxLength={TITLE_MAX + 20}
                onChange={(e) => setTitle(e.currentTarget.value)}
                rightSection={<Text size="xs" c={title.length > TITLE_MAX ? 'red' : 'dimmed'}>{title.length}/{TITLE_MAX}</Text>} rightSectionWidth={56}
                error={title.length > TITLE_MAX ? 'Too long' : undefined} />
              <Textarea label="Message" placeholder="Optional. One short sentence works best." autosize minRows={2} maxRows={5}
                value={body} onChange={(e) => setBody(e.currentTarget.value)}
                description={`${body.length}/${BODY_MAX}`} error={body.length > BODY_MAX ? 'Too long' : undefined} />

              <div>
                <Text size="sm" fw={500} mb={4}><LabelWithTip label="When tapped, open" tip="Where the app takes people when they tap the notification. A wallpaper or collection also adds its picture to the notification." /></Text>
                <SegmentedControl radius="xl" value={open} onChange={(v) => setOpen(v as OpenKind)}
                  data={[{ value: 'home', label: 'Home' }, { value: 'wallpaper', label: 'Wallpaper' }, { value: 'collection', label: 'Collection' }, { value: 'section', label: 'Section' }]} />
              </div>
              {open === 'wallpaper' && <WallpaperPicker value={wallpaperId} onChange={setWallpaperId} publishedOnly={env === 'prod'} placeholder="Search a wallpaper by title" />}
              {open === 'collection' && (
                <Select data={collections} value={collectionId} onChange={setCollectionId} placeholder="Pick a collection"
                  nothingFoundMessage="No collections with published wallpapers yet" searchable />
              )}
              {open === 'section' && <Select data={SECTION_OPTIONS} value={section} onChange={setSection} allowDeselect={false} />}

              <Group justify="flex-end" mt="xs">
                <Button variant="gradient" size="md" leftSection={env === 'debug' ? <IconFlask size={18} /> : <IconSend size={18} />}
                  disabled={!canSend} loading={sending} onClick={() => (env === 'prod' ? setConfirming(true) : send())}>
                  {env === 'debug' ? 'Send test' : 'Send to users'}
                </Button>
              </Group>
            </Stack>
          </Card>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 5 }}>
          <Stack gap="lg">
            <Card withBorder className="soft-card" padding="lg">
              <Text fw={700} mb={4}>Preview</Text>
              <Text size="xs" c="dimmed" mb="md">Roughly how it looks on an Android phone (expanded).</Text>
              <PhonePreview title={title} body={body} image={preview} />
            </Card>

            {daily && (
              <Card withBorder className="soft-card" padding="lg">
                <Group gap="sm" mb="xs" wrap="nowrap">
                  <ThemeIcon variant="gradient" radius="xl"><IconCalendarTime size={18} /></ThemeIcon>
                  <div style={{ flex: 1 }}>
                    <Text fw={700}>Daily wallpaper push</Text>
                    <Text size="xs" c="dimmed">One automatic notification a day to "Daily fans".</Text>
                  </div>
                  <Switch checked={daily.enabled} onChange={(e) => setDaily({ ...daily, enabled: e.currentTarget.checked })} aria-label="Daily push on" />
                </Group>
                <Stack gap="sm">
                  <SimpleGrid cols={2} spacing="sm">
                    <Select label="Send at (your time)" data={hourOptions()} value={String(daily.hour_utc)} allowDeselect={false}
                      onChange={(v) => setDaily({ ...daily, hour_utc: Number(v) })} />
                    <TextInput label="Title" value={daily.title} maxLength={TITLE_MAX} onChange={(e) => setDaily({ ...daily, title: e.currentTarget.value })} />
                  </SimpleGrid>
                  <Switch label={<LabelWithTip label="Pick a fresh wallpaper every day" tip="Chooses a popular published wallpaper that wasn't featured in the last 30 days, and also sets it as the Daily wallpaper card on Home. Off = always push the Daily wallpaper set in App settings." />}
                    checked={daily.auto_pick} onChange={(e) => setDaily({ ...daily, auto_pick: e.currentTarget.checked })} />
                  <Text size="xs" c="dimmed">The message is the wallpaper's title, with its picture.</Text>
                  <Group justify="space-between" wrap="wrap">
                    <Group gap="xs">
                      <Button size="xs" variant="default" leftSection={<IconFlask size={14} />} loading={busyDaily === 'debug'}
                        disabled={!setup || setup === 'loading' || !setup.configured} onClick={() => dailyNow('debug')}>Test now</Button>
                    </Group>
                    <Button size="xs" variant="gradient" disabled={JSON.stringify(daily) === dailySaved} loading={busyDaily === 'save'} onClick={saveDaily}>Save</Button>
                  </Group>
                </Stack>
              </Card>
            )}
          </Stack>
        </Grid.Col>
      </Grid>

      <Card withBorder className="soft-card" padding="lg">
        <Group justify="space-between" mb="sm">
          <Text fw={700}>History</Text>
          <Text size="xs" c="dimmed">Last 50 sends</Text>
        </Group>
        {log.length ? (
          <Table.ScrollContainer minWidth={640}>
            <Table verticalSpacing={6}>
              <Table.Thead>
                <Table.Tr><Table.Th>When</Table.Th><Table.Th>Notification</Table.Th><Table.Th>Audience</Table.Th><Table.Th>Status</Table.Th><Table.Th ta="right">Opens</Table.Th></Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {log.map((r) => (
                  <Table.Tr key={r.id}>
                    <Table.Td><Text size="xs" title={dayjs(r.created_at).format('D MMM YYYY HH:mm')}>{dayjs(r.created_at).fromNow()}</Text></Table.Td>
                    <Table.Td>
                      <Text size="sm" fw={600} lineClamp={1}>{r.title}</Text>
                      {r.body && <Text size="xs" c="dimmed" lineClamp={1}>{r.body}</Text>}
                    </Table.Td>
                    <Table.Td>
                      <Group gap={4}>
                        <Badge size="xs" variant="light" color={r.env === 'prod' ? 'brand' : 'yellow'}>{r.env === 'prod' ? 'live' : 'test'}</Badge>
                        <Badge size="xs" variant="outline" color="gray">{r.kind === 'daily' ? 'daily' : r.topic.replace(/^(prod|debug)_/, '')}</Badge>
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      {r.status === 'sent'
                        ? <Badge size="sm" color="green" variant="light">sent</Badge>
                        : <Group gap={4} wrap="nowrap"><Badge size="sm" color="red" variant="light">failed</Badge>{r.error && <HelpTip>{r.error}</HelpTip>}</Group>}
                    </Table.Td>
                    <Table.Td ta="right"><Text size="sm" fw={600}>{r.opens}</Text></Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        ) : (
          <EmptyState icon={<IconBell size={28} />} title="No notifications sent yet">
            Your sends (tests and live) will show up here with how many people opened them.
          </EmptyState>
        )}
      </Card>

      <Modal opened={confirming} onClose={() => setConfirming(false)} centered radius="lg"
        title={<Group gap={8}><IconBellRinging size={20} /><Text fw={700}>Send to real users?</Text></Group>}>
        <Stack>
          <Text size="sm">This goes to <b>{audience.label}</b> on every phone with the app installed and notifications on. It can't be unsent.</Text>
          <PhonePreview title={title} body={body} image={preview} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirming(false)}>Cancel</Button>
            <Button variant="gradient" leftSection={<IconSend size={16} />} onClick={send}>Send now</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

function PhonePreview({ title, body, image }: { title: string; body: string; image: string | null }) {
  return (
    <div style={{ background: 'var(--mantine-color-default-hover)', borderRadius: 20, padding: 12 }}>
      <Card radius="lg" padding="sm" withBorder>
        <Group gap={6} mb={6} wrap="nowrap">
          <BrandMark size={18} />
          <Text size="xs" c="dimmed">Zeul · now</Text>
        </Group>
        <Text size="sm" fw={700} lineClamp={1}>{title || 'Your title'}</Text>
        {(body || !title) && <Text size="sm" c="dimmed" lineClamp={3}>{body || 'Your message'}</Text>}
        {image && <img src={image} alt="" style={{ width: '100%', height: 150, objectFit: 'cover', borderRadius: 10, marginTop: 8, display: 'block' }} />}
      </Card>
    </div>
  );
}

function SetupGuide({ deployed }: { deployed: boolean }) {
  return (
    <Alert variant="light" color="brand" radius="lg" icon={<IconBell />} title="One-time setup needed before you can send">
      <List size="sm" spacing={4} type="ordered" mt={4}>
        {!deployed && <List.Item>Deploy the <code>send-push</code> function (Claude does this).</List.Item>}
        <List.Item>Create a free Firebase project at console.firebase.google.com and add an Android app with package <code>com.zeul.wallpapers</code>.</List.Item>
        <List.Item>Download <code>google-services.json</code> and send it to Ghanshyam for the app.</List.Item>
        <List.Item>Project settings → Service accounts → <b>Generate new private key</b>, then run <code>supabase\set-firebase-key.ps1</code> and choose that file.</List.Item>
      </List>
      <Text size="xs" c="dimmed" mt="xs">The private key never goes into the dashboard or into git; it's stored only as a server secret.</Text>
    </Alert>
  );
}
