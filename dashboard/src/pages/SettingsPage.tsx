import { useCallback, useEffect, useState, type ReactNode } from 'react';
import {
  ActionIcon, Button, Card, Group, Loader, NumberInput, SegmentedControl, Select, SimpleGrid, Stack, Switch,
  Text, TextInput, Textarea,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconArrowDown, IconArrowUp } from '../lib/icons';
import { supabase } from '../lib/supabase';
import { WallpaperPicker } from '../components/WallpaperPicker';
import { PageHeader } from '../components/Help';

// Shapes follow docs/CONTRACTS.md section 4 (the app parses them in AppConfigParser.kt).
type Config = {
  cdn_base_url: string;
  min_app_version: number;
  maintenance: { enabled: boolean; message: string };
  ads: { native_every: number; app_open_enabled: boolean; app_open_min_hours: number };
  unlock_mode: 'permanent' | '24h';
  free_max_resolution: number;
  home_sections: string[];
  featured_banner: { wallpaper_id?: string | null; collection_id?: string | null; title: string } | null;
  daily_wallpaper_id: string | null;
  paywall: { highlight: 'annual' | 'monthly'; headline: string };
  auth_email_enabled: boolean;
  user_uploads: { enabled: boolean; daily_limit: number; hide_after_reports: number };
};

const DEFAULTS: Config = {
  cdn_base_url: '',
  min_app_version: 1,
  maintenance: { enabled: false, message: '' },
  ads: { native_every: 12, app_open_enabled: false, app_open_min_hours: 4 },
  unlock_mode: '24h',
  free_max_resolution: 2560,
  home_sections: ['for_you', 'curated', 'popular', 'new', 'amoled', 'tablet', 'premium'],
  featured_banner: null,
  daily_wallpaper_id: null,
  paywall: { highlight: 'annual', headline: '' },
  auth_email_enabled: false,
  user_uploads: { enabled: true, daily_limit: 5, hide_after_reports: 3 },
};

const SECTION_LABELS: Record<string, string> = {
  for_you: 'For You', curated: 'Curated', popular: 'Popular', new: 'New',
  amoled: 'AMOLED', tablet: 'Tablet', premium: 'Premium',
};

export default function SettingsPage() {
  const [cfg, setCfg] = useState<Config | null>(null);
  const [saved, setSaved] = useState<Config | null>(null);
  const [collections, setCollections] = useState<{ value: string; label: string }[]>([]);
  const [saving, setSaving] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [{ data, error }, cols] = await Promise.all([
      supabase.from('app_config').select('key,value'),
      supabase.from('collections').select('id,title').order('sort_order'),
    ]);
    if (error) return notifications.show({ color: 'red', message: error.message });
    const merged = { ...DEFAULTS } as Record<string, unknown>;
    for (const row of data ?? []) if (row.key in DEFAULTS) merged[row.key] = row.value ?? (DEFAULTS as Record<string, unknown>)[row.key];
    const c = merged as Config;
    c.maintenance = { ...DEFAULTS.maintenance, ...(c.maintenance ?? {}) };
    c.ads = { ...DEFAULTS.ads, ...(c.ads ?? {}) };
    c.paywall = { ...DEFAULTS.paywall, ...(c.paywall ?? {}) };
    setCfg(c);
    setSaved(structuredClone(c));
    setCollections((cols.data ?? []).map((x) => ({ value: x.id, label: x.title })));
  }, []);

  useEffect(() => { load(); }, [load]);

  // Closing or reloading the tab with unsaved edits asks first.
  const anyDirty = !!cfg && !!saved && JSON.stringify(cfg) !== JSON.stringify(saved);
  useEffect(() => {
    if (!anyDirty) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [anyDirty]);

  if (!cfg || !saved) return <Loader />;

  const set = <K extends keyof Config>(k: K, v: Config[K]) => setCfg({ ...cfg, [k]: v });
  const dirty = (keys: (keyof Config)[]) => keys.some((k) => JSON.stringify(cfg[k]) !== JSON.stringify(saved[k]));

  async function save(section: string, keys: (keyof Config)[]) {
    setSaving(section);
    const rows = keys.map((key) => ({ key, value: cfg![key] }));
    const { error } = await supabase.from('app_config').upsert(rows, { onConflict: 'key' });
    setSaving(null);
    if (error) return notifications.show({ color: 'red', title: 'Save failed', message: error.message });
    notifications.show({ color: 'green', message: `${section} saved. Apps pick it up on their next launch or refresh.` });
    setSaved(structuredClone(cfg!));
  }

  const sectionProps = (title: string, keys: (keyof Config)[]) => ({
    title,
    dirty: dirty(keys),
    saving: saving === title,
    onSave: () => save(title, keys),
    onDiscard: () => setCfg({ ...cfg, ...Object.fromEntries(keys.map((k) => [k, structuredClone(saved[k])])) }),
  });

  const moveSection = (i: number, dir: -1 | 1) => {
    const list = [...cfg.home_sections];
    const j = i + dir;
    if (j < 0 || j >= list.length) return;
    [list[i], list[j]] = [list[j], list[i]];
    set('home_sections', list);
  };
  const allSections = [...cfg.home_sections, ...Object.keys(SECTION_LABELS).filter((s) => !cfg.home_sections.includes(s))];

  return (
    <Stack>
      <PageHeader
        title="App settings"
        description="Change how the app behaves without releasing an update. Users get the new settings the next time they open the app."
        tips={[
          'Each card saves on its own; the Save button lights up when something changed.',
          'Maintenance mode hides the whole app behind your message. Use it only for emergencies.',
          'Minimum app version forces anyone on an older build to update. Only raise it after the new version is live on Play.',
          'App-open ads earn more but annoy some users. Start with them off, or every 4+ hours.',
        ]}
      />

      {/* One column on laptops, two on wide screens */}
      <SimpleGrid cols={{ base: 1, xl: 2 }} spacing="lg" verticalSpacing="lg">

      <Section {...sectionProps("Ads", ['ads'])} description="Rewarded unlock ads are always on for free users. These control the extra formats.">
        <SimpleGrid cols={{ base: 1, sm: 3 }}>
          <NumberInput label="Native ad every N tiles" min={4} max={50} value={cfg.ads.native_every}
            onChange={(v) => set('ads', { ...cfg.ads, native_every: Number(v) || 12 })} />
          <Switch mt={28} label="App-open ads" checked={cfg.ads.app_open_enabled}
            onChange={(e) => set('ads', { ...cfg.ads, app_open_enabled: e.currentTarget.checked })} />
          <NumberInput label="Min hours between app-open ads" min={1} max={48} value={cfg.ads.app_open_min_hours}
            disabled={!cfg.ads.app_open_enabled}
            onChange={(v) => set('ads', { ...cfg.ads, app_open_min_hours: Number(v) || 4 })} />
        </SimpleGrid>
      </Section>

      <Section {...sectionProps("Unlocks & downloads", ['unlock_mode', 'free_max_resolution'])}>
        <Group align="flex-end" gap="xl">
          <div>
            <Text size="sm" fw={500} mb={4}>A watched ad unlocks a wallpaper</Text>
            <SegmentedControl value={cfg.unlock_mode} onChange={(v) => set('unlock_mode', v as Config['unlock_mode'])}
              data={[{ value: 'permanent', label: 'Forever' }, { value: '24h', label: 'For 24 hours' }]} />
          </div>
          <NumberInput label="Free download max size (long edge, px)" min={1080} max={8000} step={160} w={260}
            value={cfg.free_max_resolution} onChange={(v) => set('free_max_resolution', Number(v) || 2560)} />
        </Group>
      </Section>

      <Section {...sectionProps("Home screen", ['home_sections', 'featured_banner', 'daily_wallpaper_id'])}
        description="Section order (top = first chip). Switch a section off to hide it.">
        <Stack gap={4}>
          {allSections.map((s) => {
            const idx = cfg.home_sections.indexOf(s);
            const on = idx >= 0;
            return (
              <Group key={s} gap="xs">
                <Switch size="sm" checked={on} onChange={(e) =>
                  set('home_sections', e.currentTarget.checked ? [...cfg.home_sections, s] : cfg.home_sections.filter((x) => x !== s))} />
                <Text w={110} size="sm" c={on ? undefined : 'dimmed'}>{SECTION_LABELS[s] ?? s}</Text>
                <ActionIcon size="sm" variant="subtle" disabled={!on || idx === 0} onClick={() => moveSection(idx, -1)}><IconArrowUp size={14} /></ActionIcon>
                <ActionIcon size="sm" variant="subtle" disabled={!on || idx === cfg.home_sections.length - 1} onClick={() => moveSection(idx, 1)}><IconArrowDown size={14} /></ActionIcon>
              </Group>
            );
          })}
        </Stack>
        <Switch label="Show a featured banner" checked={!!cfg.featured_banner}
          onChange={(e) => set('featured_banner', e.currentTarget.checked ? { title: '', wallpaper_id: null, collection_id: null } : null)} />
        {cfg.featured_banner && (
          <SimpleGrid cols={{ base: 1, sm: 3 }}>
            <TextInput label="Banner title" required value={cfg.featured_banner.title}
              onChange={(e) => set('featured_banner', { ...cfg.featured_banner!, title: e.currentTarget.value })} />
            <WallpaperPicker label="Opens wallpaper" publishedOnly value={cfg.featured_banner.wallpaper_id ?? null}
              onChange={(id) => set('featured_banner', { ...cfg.featured_banner!, wallpaper_id: id, collection_id: id ? null : cfg.featured_banner!.collection_id })} />
            <Select label="…or opens collection" clearable data={collections} value={cfg.featured_banner.collection_id ?? null}
              onChange={(id) => set('featured_banner', { ...cfg.featured_banner!, collection_id: id, wallpaper_id: id ? null : cfg.featured_banner!.wallpaper_id })} />
          </SimpleGrid>
        )}
        <WallpaperPicker label="Daily wallpaper" description="Shown as the daily card on Home and used for the daily push." publishedOnly
          value={cfg.daily_wallpaper_id} onChange={(id) => set('daily_wallpaper_id', id)} maw={420} />
      </Section>

      <Section {...sectionProps("Paywall", ['paywall'])} description="Subscriptions are not live yet (A10 deferred); this only changes the paywall text.">
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <TextInput label="Headline" value={cfg.paywall.headline} onChange={(e) => set('paywall', { ...cfg.paywall, headline: e.currentTarget.value })} />
          <div>
            <Text size="sm" fw={500} mb={4}>Highlighted plan</Text>
            <SegmentedControl value={cfg.paywall.highlight} onChange={(v) => set('paywall', { ...cfg.paywall, highlight: v as 'annual' | 'monthly' })}
              data={[{ value: 'annual', label: 'Annual' }, { value: 'monthly', label: 'Monthly' }]} />
          </div>
        </SimpleGrid>
      </Section>

      <Section {...sectionProps("User uploads", ['user_uploads'])}
        description="Signed-in people can publish wallpapers from the app (Home → upload button). They go live straight away, credited to the uploader. Block a person on the Users page.">
        <Stack gap="sm">
          <Switch label="Allow uploads from the app" checked={cfg.user_uploads.enabled}
            onChange={(e) => set('user_uploads', { ...cfg.user_uploads, enabled: e.currentTarget.checked })} />
          <Group gap="xl" align="flex-end">
            <NumberInput label="Uploads per person per day" min={0} max={50} w={220}
              value={cfg.user_uploads.daily_limit} onChange={(v) => set('user_uploads', { ...cfg.user_uploads, daily_limit: Math.max(0, Number(v) || 0) })} />
            <NumberInput label="Hide after this many reports" description="From different people; you review them under Reports" min={1} max={50} w={260}
              value={cfg.user_uploads.hide_after_reports} onChange={(v) => set('user_uploads', { ...cfg.user_uploads, hide_after_reports: Math.max(1, Number(v) || 3) })} />
          </Group>
        </Stack>
      </Section>

      <Section {...sectionProps("Sign-in", ['auth_email_enabled'])}
        description="Google sign-in is always available. Email sign-in sends a 6-digit code and needs a custom email sender (SMTP) set up in Supabase first.">
        <Switch label="Show 'Sign in with email' in the app" checked={cfg.auth_email_enabled}
          onChange={(e) => set('auth_email_enabled', e.currentTarget.checked)} />
        {cfg.auth_email_enabled && (
          <Text size="xs" c="orange">Only turn this on after Supabase → Authentication → SMTP is configured and the Magic Link email template contains {"{{ .Token }}"}. Otherwise users never receive a code.</Text>
        )}
      </Section>

      <Section {...sectionProps("Maintenance & updates", ['maintenance', 'min_app_version'])}
        description="Maintenance mode shows a message instead of the app. Minimum version forces older installs to update.">
        <Switch label="Maintenance mode" color="red" checked={cfg.maintenance.enabled}
          onChange={(e) => set('maintenance', { ...cfg.maintenance, enabled: e.currentTarget.checked })} />
        <Textarea label="Maintenance message" autosize minRows={2} value={cfg.maintenance.message}
          onChange={(e) => set('maintenance', { ...cfg.maintenance, message: e.currentTarget.value })} />
        <NumberInput label="Minimum app version (versionCode)" min={1} w={260} value={cfg.min_app_version}
          onChange={(v) => set('min_app_version', Number(v) || 1)} />
      </Section>

      <Section {...sectionProps("Image CDN", ['cdn_base_url'])} description="Change only when moving images to another host (e.g. Cloudflare R2).">
        <TextInput label="CDN base URL" value={cfg.cdn_base_url} onChange={(e) => set('cdn_base_url', e.currentTarget.value.trim())}
          error={cfg.cdn_base_url && !/^https:\/\/[^\s]+[^/]$/.test(cfg.cdn_base_url) ? 'Must start with https:// and have no trailing slash' : undefined} />
      </Section>
      </SimpleGrid>
    </Stack>
  );
}

function Section({ title, description, dirty, saving, onSave, onDiscard, children }: {
  title: string; description?: string; dirty: boolean; saving: boolean; onSave: () => void; onDiscard: () => void; children: ReactNode;
}) {
  return (
    <Card withBorder>
      <Group justify="space-between" mb="xs">
        <div>
          <Text fw={600}>{title}</Text>
          {description && <Text size="xs" c="dimmed">{description}</Text>}
        </div>
        <Group gap="xs">
          {dirty && <Button size="xs" variant="subtle" color="gray" onClick={onDiscard}>Discard</Button>}
          <Button size="xs" disabled={!dirty} loading={saving} onClick={onSave}>Save</Button>
        </Group>
      </Group>
      <Stack gap="sm">{children}</Stack>
    </Card>
  );
}
