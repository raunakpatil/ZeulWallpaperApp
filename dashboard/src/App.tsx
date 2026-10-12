import { lazy, Suspense, useEffect, useState } from 'react';
import { NavLink as RouterLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import {
  ActionIcon, AppShell, Avatar, Burger, Button, Center, Group, Kbd, Loader, Modal, NavLink, Progress, ScrollArea, Stack, Text, TextInput, Tooltip,
  UnstyledButton, useComputedColorScheme, useMantineColorScheme,
} from '@mantine/core';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import {
  IconBell, IconCategory, IconChartBar, IconFlag, IconHome, IconLogout, IconMoon, IconPhoto, IconPlus, IconSearch, IconSend, IconSettings, IconUsers,
  IconStack2, IconStar, IconSun, IconUpload,
} from './lib/icons';
import { AuthGate } from './auth/AuthGate';
import { supabase } from './lib/supabase';
import { cdnUsage } from './lib/api';
import { useAppData } from './lib/useAppData';
import { avatarFor, useDisplayName } from './lib/avatars';
import { notifications } from '@mantine/notifications';
import { CommandPalette } from './components/CommandPalette';
import { BrandLockup } from './components/BrandMark';
import { spotlight } from '@mantine/spotlight';

type NavItem = { to: string; label: string; hint: string; icon: typeof IconHome };
// Pages load on demand, so the first screen appears faster.
const HomePage = lazy(() => import('./pages/HomePage'));
const WallpapersPage = lazy(() => import('./pages/WallpapersPage'));
const UploadPage = lazy(() => import('./pages/UploadPage'));
const CuratedPage = lazy(() => import('./pages/CuratedPage'));
const CategoriesPage = lazy(() => import('./pages/CategoriesPage'));
const CollectionsPage = lazy(() => import('./pages/CollectionsPage'));
const ReportsPage = lazy(() => import('./pages/ReportsPage'));
const InsightsPage = lazy(() => import('./pages/InsightsPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'));
const UsersPage = lazy(() => import('./pages/UsersPage'));

const NAV: { group: string | null; items: NavItem[] }[] = [
  { group: null, items: [{ to: '/home', label: 'Home', hint: 'Overview, checklist and tips', icon: IconHome }] },
  {
    group: 'Content',
    items: [
      { to: '/wallpapers', label: 'Wallpapers', hint: 'Browse, edit, publish, delete', icon: IconPhoto },
      { to: '/upload', label: 'Upload', hint: 'Add new wallpapers', icon: IconUpload },
      { to: '/collections', label: 'Collections', hint: 'Themed packs like "Neon Nights"', icon: IconStack2 },
      { to: '/curated', label: 'Curated order', hint: 'Order of the Curated section', icon: IconStar },
    ],
  },
  { group: 'Organize', items: [{ to: '/categories', label: 'Categories', hint: 'Groups in the Explore tab', icon: IconCategory }] },
  {
    group: 'Grow',
    items: [
      { to: '/insights', label: 'Insights', hint: 'Downloads, searches, trends', icon: IconChartBar },
      { to: '/notifications', label: 'Notifications', hint: 'Push messages + daily wallpaper', icon: IconBell },
      { to: '/users', label: 'Users', hint: 'Accounts, premium, activity', icon: IconUsers },
      { to: '/reports', label: 'Reports', hint: 'Complaints from users', icon: IconFlag },
    ],
  },
  { group: 'Settings', items: [{ to: '/settings', label: 'App settings', hint: 'Ads, home screen, maintenance', icon: IconSettings }] },
];

/** Shown first in the Ctrl+K palette. */
const QUICK_ACTIONS: NavItem[] = [
  { to: '/upload', label: 'Upload wallpapers', hint: 'Drop new images', icon: IconPlus },
  { to: '/notifications', label: 'Send a notification', hint: 'Push to app users', icon: IconSend },
  { to: '/collections', label: 'New collection', hint: 'Create a themed pack', icon: IconPlus },
  { to: '/wallpapers?status=draft', label: 'Review drafts', hint: 'Wallpapers not live yet', icon: IconPhoto },
];

const CDN_BUDGET_MB = 1000;


function CdnMeter() {
  const [mb, setMb] = useState<number | null>(null);
  useEffect(() => { cdnUsage().then((u) => setMb(u?.repo_size_mb ?? null)); }, []);
  if (mb === null || mb < 0) return null;
  const pct = Math.min(100, (mb / CDN_BUDGET_MB) * 100);
  return (
    <Tooltip label="Image storage on free GitHub Pages (~1 GB). Plan the move to Cloudflare R2 before 80%." multiline w={240}>
      <div>
        <Group justify="space-between" mb={4}>
          <Text size="xs" c="dimmed">Image storage</Text>
          <Text size="xs" c="dimmed">{mb} / {CDN_BUDGET_MB} MB</Text>
        </Group>
        <Progress value={Math.max(pct, 1.5)} color={pct > 80 ? 'red' : pct > 60 ? 'yellow' : 'brand'} size="sm" radius="xl" />
      </div>
    </Tooltip>
  );
}

function Logo() {
  return (
    <BrandLockup />
  );
}

function ProfileModal({ opened, onClose, name, email, onSave }: {
  opened: boolean; onClose: () => void; name: string; email: string; onSave: (n: string) => Promise<void>;
}) {
  const [value, setValue] = useState(name);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (opened) setValue(name); }, [opened, name]);

  async function save() {
    setBusy(true);
    try {
      await onSave(value);
      notifications.show({ color: 'green', message: 'Display name updated' });
      onClose();
    } catch (e) {
      notifications.show({ color: 'red', title: 'Could not save', message: (e as Error).message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal opened={opened} onClose={onClose} title={<Text fw={700}>Your profile</Text>} radius="lg" centered>
      <Stack>
        <Group gap="md" wrap="nowrap">
          <span className="ig-ring">
            <Avatar src={avatarFor(email)} alt="" radius="xl" size={64} color="brand" style={{ border: '3px solid var(--mantine-color-body)' }}>
              {value[0]?.toUpperCase()}
            </Avatar>
          </span>
          <div style={{ minWidth: 0 }}>
            <Text fw={700} truncate>{value || email}</Text>
            <Text size="sm" c="dimmed" truncate>{email}</Text>
          </div>
        </Group>
        <TextInput label="Display name" description="Shown in the dashboard sidebar and greeting." value={value} maxLength={60}
          onChange={(e) => setValue(e.currentTarget.value)} onKeyDown={(e) => e.key === 'Enter' && save()} data-autofocus />
        <Text size="xs" c="dimmed">To change your photo, replace <code>dashboard/public/avatars/{email.split('@')[0]}.jpg</code> with a square image.</Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>Cancel</Button>
          <Button variant="gradient" loading={busy} disabled={!value.trim()} onClick={save}>Save</Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function Shell({ email, userId }: { email: string; userId: string }) {
  const [opened, { toggle, close }] = useDisclosure();
  // The top bar only exists on phones; on desktop it must not reserve space (that was the empty strip).
  const isMobile = useMediaQuery('(max-width: 48em)', false, { getInitialValueInEffect: false });
  const [profileOpened, { open: openProfile, close: closeProfile }] = useDisclosure();
  const location = useLocation();
  const data = useAppData();
  const { setColorScheme } = useMantineColorScheme();
  const dark = useComputedColorScheme('light') === 'dark';
  const { name: displayName, save: saveName } = useDisplayName(userId, email);

  return (
    <>
    <CommandPalette pages={NAV.flatMap((g) => g.items)} quickActions={QUICK_ACTIONS} data={data} />
    <ProfileModal opened={profileOpened} onClose={closeProfile} name={displayName} email={email} onSave={saveName} />
    <AppShell
      header={{ height: 60, collapsed: !isMobile }}
      navbar={{ width: 264, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding={{ base: 'md', md: 'xl' }}
    >
      <AppShell.Header hiddenFrom="sm">
        <Group h="100%" px="md" justify="space-between">
          <Logo />
          <Burger opened={opened} onClick={toggle} size="sm" aria-label="Toggle menu" />
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md" className="app-sidebar">
        <AppShell.Section visibleFrom="sm" mb="lg" mt={4}><Logo /></AppShell.Section>
        <AppShell.Section mb="sm">
          <UnstyledButton onClick={() => spotlight.open()} aria-label="Search (Ctrl+K)" className="palette-trigger">
            <Group gap={8} wrap="nowrap">
              <IconSearch size={16} stroke={1.8} />
              <Text size="sm" c="dimmed" style={{ flex: 1 }}>Search…</Text>
              <Kbd size="xs">Ctrl K</Kbd>
            </Group>
          </UnstyledButton>
        </AppShell.Section>
        <AppShell.Section grow component={ScrollArea} scrollbarSize={6} className="nav-section">
          {NAV.map((section) => (
            <Stack key={section.group ?? 'top'} gap={2} mb="sm">
              {section.group && (
                <Text size="xs" fw={700} tt="uppercase" px="sm" mt={6} mb={2} className="nav-group-label" style={{ letterSpacing: 0.8 }}>{section.group}</Text>
              )}
              {section.items.map((n) => {
                const active = location.pathname.startsWith(n.to);
                return (
                  <NavLink
                    key={n.to}
                    component={RouterLink}
                    to={n.to}
                    label={n.label}
                    description={n.hint}
                    leftSection={<n.icon size={22} stroke={active ? 2.2 : 1.6} />}
                    active={active}
                    className="nav-item"
                    onClick={close}
                    styles={{ label: { fontWeight: active ? 700 : 500, fontSize: 15 }, description: { fontSize: 11 } }}
                  />
                );
              })}
            </Stack>
          ))}
        </AppShell.Section>
        <AppShell.Section>
          <Stack gap="sm" pt="sm" className="sidebar-footer" style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
            <CdnMeter />
            <Group gap="sm" wrap="nowrap">
              <Tooltip label="Edit your display name">
                <UnstyledButton onClick={openProfile} aria-label="Edit your display name"
                  style={{ display: 'flex', alignItems: 'center', gap: 'var(--mantine-spacing-sm)', flex: 1, minWidth: 0, borderRadius: 12 }}>
                  <span className="ig-ring" style={{ padding: 2 }}>
                    <Avatar src={avatarFor(email)} alt="" radius="xl" size={34} color="brand" style={{ border: '2px solid var(--mantine-color-body)' }}>
                      {displayName[0]?.toUpperCase()}
                    </Avatar>
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <Text size="sm" fw={600} truncate>{displayName}</Text>
                    <Text size="xs" c="dimmed" truncate>{email}</Text>
                  </div>
                </UnstyledButton>
              </Tooltip>
              <Tooltip label={dark ? 'Light mode' : 'Dark mode'}>
                <ActionIcon variant="subtle" color="gray" radius="xl" onClick={() => setColorScheme(dark ? 'light' : 'dark')} aria-label="Toggle colour scheme">
                  {dark ? <IconSun size={18} /> : <IconMoon size={18} />}
                </ActionIcon>
              </Tooltip>
              <Tooltip label="Sign out">
                <ActionIcon variant="subtle" color="gray" radius="xl" onClick={() => supabase.auth.signOut()} aria-label="Sign out">
                  <IconLogout size={18} />
                </ActionIcon>
              </Tooltip>
            </Group>
          </Stack>
        </AppShell.Section>
      </AppShell.Navbar>

      <AppShell.Main>
        {/* Fills the screen; the ceiling only kicks in on ultra-wide monitors */}
        <div key={location.pathname} className="page-enter" style={{ width: '100%', maxWidth: 2000, margin: '0 auto' }}>
          <Suspense fallback={<Center py="xl"><Loader /></Center>}>
          <Routes>
            <Route path="/home" element={<HomePage data={data} name={displayName} />} />
            <Route path="/wallpapers" element={<WallpapersPage data={data} />} />
            <Route path="/upload" element={<UploadPage data={data} />} />
            <Route path="/curated" element={<CuratedPage data={data} />} />
            <Route path="/categories" element={<CategoriesPage data={data} />} />
            <Route path="/collections" element={<CollectionsPage data={data} />} />
            <Route path="/reports" element={<ReportsPage data={data} />} />
            <Route path="/insights" element={<InsightsPage data={data} />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/notifications" element={<NotificationsPage data={data} />} />
            <Route path="/users" element={<UsersPage />} />
            <Route path="*" element={<Navigate to="/home" replace />} />
          </Routes>
          </Suspense>
        </div>
      </AppShell.Main>
    </AppShell>
    </>
  );
}

export default function App() {
  return <AuthGate>{(session) => <Shell email={session.user.email ?? ''} userId={session.user.id} />}</AuthGate>;
}
