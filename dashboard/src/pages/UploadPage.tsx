import { useEffect, useRef, useState } from 'react';
import {
  Alert, Badge, Button, Card, Checkbox, CloseButton, Group, Image, Loader, Select, SimpleGrid, Stack, Stepper, Text, TextInput,
  ThemeIcon,
} from '@mantine/core';
import { Dropzone, IMAGE_MIME_TYPE } from '@mantine/dropzone';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle, IconCheck, IconChecklist, IconCloudUpload, IconPhoto, IconPhotoPlus, IconRocket, IconShieldCheck, IconUpload, IconX,
} from '../lib/icons';
import { HelpTip, LabelWithTip, PageHeader } from '../components/Help';
import { FIELD_TIPS } from '../lib/tips';
import { hamming, isLowInfoHash, processImage, type Processed } from '../lib/imageProcessing';
import { createWallpaper, loadPhashes, type PhashRow } from '../lib/api';
import { WallpaperForm } from '../components/WallpaperForm';
import { isMetaValid } from '../lib/meta';
import { LICENSES, type WallpaperMeta } from '../lib/types';
import type { AppData } from '../lib/useAppData';

type ItemState = 'processing' | 'ready' | 'uploading' | 'done' | 'error';

interface Item {
  id: string;
  file: File;
  state: ItemState;
  error?: string;
  processed?: Processed;
  previewUrl?: string;
  meta?: WallpaperMeta;
  includeOriginal: boolean;
  duplicates: string[];
}

const DUPLICATE_DISTANCE = 6;
const mb = (n: number) => (n / 1048576).toFixed(2) + ' MB';

export default function UploadPage({ data }: { data: AppData }) {
  const [items, setItems] = useState<Item[]>([]);
  const [defaults, setDefaults] = useState({ license: '', author_name: '', category_id: null as string | null });
  const phashes = useRef<PhashRow[] | null>(null);

  const patch = (id: string, p: Partial<Item>) =>
    setItems((list) => list.map((it) => (it.id === id ? { ...it, ...p } : it)));

  useEffect(() => () => items.forEach((i) => i.previewUrl && URL.revokeObjectURL(i.previewUrl)), []); // eslint-disable-line react-hooks/exhaustive-deps

  async function addFiles(files: File[]) {
    if (!phashes.current) phashes.current = await loadPhashes();
    const fresh: Item[] = files.map((file) => ({
      id: crypto.randomUUID(), file, state: 'processing', includeOriginal: true, duplicates: [],
    }));
    setItems((list) => [...list, ...fresh]);

    for (const it of fresh) {
      try {
        const p = await processImage(it.file);
        const dupes = (phashes.current ?? [])
          .filter((x) => hamming(x.phash, p.phash) <= DUPLICATE_DISTANCE &&
            (!isLowInfoHash(p.phash) || x.dominant_color === p.dominantColor))
          .map((x) => x.title);
        patch(it.id, {
          state: 'ready',
          processed: p,
          previewUrl: URL.createObjectURL(p.thumb),
          duplicates: dupes,
          meta: {
            title: p.suggestedTitle,
            category_id: defaults.category_id,
            tags: p.suggestedTags,
            device_type: p.suggestedDevice,
            is_amoled: p.amoledRatio >= 0.4,
            is_curated: false,
            is_premium: false,
            status: 'draft',
            publish_at: null,
            author_name: defaults.author_name,
            license: defaults.license,
            source_url: '',
          },
        });
      } catch (e) {
        patch(it.id, { state: 'error', error: (e as Error).message });
      }
    }
  }

  async function uploadOne(it: Item) {
    if (!it.processed || !it.meta) return;
    patch(it.id, { state: 'uploading', error: undefined });
    try {
      const res = await createWallpaper(it.processed, it.meta, it.includeOriginal);
      patch(it.id, { state: 'done' });
      phashes.current?.push({ id: res.id, title: it.meta.title, phash: it.processed.phash, dominant_color: it.processed.dominantColor });
    } catch (e) {
      patch(it.id, { state: 'error', error: (e as Error).message });
    }
  }

  async function uploadAll() {
    const ready = items.filter((i) => i.state === 'ready' && i.meta && isMetaValid(i.meta));
    for (const it of ready) await uploadOne(it); // sequential: one CDN commit at a time
    await data.reload();
    notifications.show({ color: 'green', title: 'Upload finished', message: `${ready.length} wallpaper(s) processed` });
  }

  const readyCount = items.filter((i) => i.state === 'ready').length;
  const invalidCount = items.filter((i) => i.state === 'ready' && i.meta && !isMetaValid(i.meta)).length;

  return (
    <Stack>
      <PageHeader
        title="Upload"
        description="Drop images, check the details the uploader filled in, then publish. Everything is processed in your browser, for free."
        tips={[
          'Set Category, Author and Licence in "Defaults" first; every file you drop afterwards gets them.',
          'Titles and tags are guessed from the file name, so "neon-city-night.jpg" becomes "Neon City Night" with tags neon, city, night.',
          'AMOLED is switched on automatically when 40%+ of pixels are pure black.',
          'An orange "duplicate" warning means a very similar image is already uploaded.',
          'Use Chrome or Edge; they can create the WebP files the app needs.',
        ]}
        actions={
          <>
            <Button variant="default" disabled={!items.some((i) => i.state === 'done')}
              onClick={() => setItems((l) => l.filter((i) => i.state !== 'done'))}>
              Clear finished
            </Button>
            <Button variant="gradient" leftSection={<IconUpload size={16} />} disabled={!readyCount || readyCount === invalidCount} onClick={uploadAll}>
              Upload {readyCount - invalidCount} ready
            </Button>
          </>
        }
      />

      <Stepper active={items.length === 0 ? 0 : items.some((i) => i.state === 'done') ? 2 : 1} size="sm" iconSize={30} allowNextStepsSelect={false}>
        <Stepper.Step label="Drop" description="Add images" icon={<IconPhoto size={16} />} />
        <Stepper.Step label="Review" description="Check title, tags, licence" icon={<IconChecklist size={16} />} />
        <Stepper.Step label="Publish" description="Upload & go live" icon={<IconRocket size={16} />} />
      </Stepper>

      <Alert variant="light" color="orange" radius="lg" icon={<IconShieldCheck />} title="Only upload wallpapers you have the rights to">
        Your own work, commissioned art, licensed images, or an artist's written permission. No Pexels/Unsplash/Pinterest images,
        characters, celebrities or brand logos. Copyright complaints are the #1 reason wallpaper apps get removed from Play.
      </Alert>

      <Card withBorder className="soft-card" padding="lg">
        <Group gap={6} mb={2}>
          <Text fw={700}>Defaults for new files</Text>
          <HelpTip>Saves typing: these three values are pre-filled on every file you drop from now on. You can still change each one.</HelpTip>
        </Group>
        <Text size="xs" c="dimmed" mb="sm">Title, tags, device, AMOLED and colour are detected per image.</Text>
        <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="sm">
          <Select label={<LabelWithTip label="Category" tip={FIELD_TIPS.category} />} placeholder="None" clearable searchable
            data={data.categories.map((c) => ({ value: c.id, label: c.name }))}
            value={defaults.category_id} onChange={(v) => setDefaults((d) => ({ ...d, category_id: v }))} />
          <TextInput label={<LabelWithTip label="Author / artist" tip={FIELD_TIPS.author} />} placeholder="Your own work? Leave empty" value={defaults.author_name}
            onChange={(e) => { const v = e.currentTarget.value; setDefaults((d) => ({ ...d, author_name: v })); }} />
          <Select label={<LabelWithTip label="Licence" tip={FIELD_TIPS.license} />} placeholder="Who owns the rights?" data={LICENSES} value={defaults.license || null}
            onChange={(v) => setDefaults((d) => ({ ...d, license: v ?? '' }))} />
        </SimpleGrid>
      </Card>

      <Dropzone onDrop={addFiles} accept={IMAGE_MIME_TYPE} maxSize={60 * 1024 * 1024} multiple className="ig-dropzone" p="xl">
        <Stack align="center" gap={8} mih={150} justify="center" style={{ pointerEvents: 'none' }} ta="center">
          <Dropzone.Accept><ThemeIcon variant="gradient" size={64} radius="xl"><IconCloudUpload size={34} /></ThemeIcon></Dropzone.Accept>
          <Dropzone.Reject><ThemeIcon color="red" size={64} radius="xl"><IconX size={34} /></ThemeIcon></Dropzone.Reject>
          <Dropzone.Idle><ThemeIcon variant="gradient" size={64} radius="xl"><IconPhotoPlus size={34} /></ThemeIcon></Dropzone.Idle>
          <Text fw={700} size="lg">Drag wallpapers here</Text>
          <Text size="sm" c="dimmed">or click to choose files · JPG, PNG or WebP · up to 60 MB each · as many as you like</Text>
        </Stack>
      </Dropzone>

      {items.map((it) => (
        <Card key={it.id} withBorder className="soft-card" padding="md">
          <Group align="flex-start" wrap="nowrap" gap="md">
            <div style={{ width: 120, flexShrink: 0 }}>
              {it.previewUrl ? (
                <Image src={it.previewUrl} radius="sm" fit="contain" h={200}
                  style={{ background: it.processed?.dominantColor }} />
              ) : (
                <Group h={200} justify="center">{it.state === 'error' ? <IconAlertTriangle /> : <Loader size="sm" />}</Group>
              )}
            </div>
            <Stack style={{ flex: 1 }} gap="xs">
              <Group justify="space-between" wrap="nowrap">
                <Group gap={6}>
                  <Text fw={600} size="sm" lineClamp={1}>{it.file.name}</Text>
                  <StateBadge state={it.state} />
                </Group>
                {it.state !== 'uploading' && (
                  <CloseButton onClick={() => setItems((l) => l.filter((x) => x.id !== it.id))} />
                )}
              </Group>

              {it.processed && (
                <Group gap={6}>
                  <Badge variant="light" color="gray">{it.processed.width}×{it.processed.height}</Badge>
                  <Badge variant="light" color="gray">full {mb(it.processed.full.size)}</Badge>
                  <Badge variant="light" color="gray">thumb {Math.round(it.processed.thumb.size / 1024)} KB</Badge>
                  <Badge variant="light" leftSection={<span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 5, background: it.processed.dominantColor }} />} color="gray">
                    {it.processed.dominantColor}
                  </Badge>
                  <Badge variant="light" color={it.processed.amoledRatio >= 0.4 ? 'dark' : 'gray'}>
                    {Math.round(it.processed.amoledRatio * 100)}% true black
                  </Badge>
                </Group>
              )}

              {it.duplicates.length > 0 && (
                <Alert color="orange" p="xs" icon={<IconAlertTriangle size={16} />}>
                  Looks like a duplicate of: {it.duplicates.slice(0, 3).join(', ')}
                </Alert>
              )}
              {it.error && <Alert color="red" p="xs">{it.error}</Alert>}

              {it.meta && it.state !== 'done' && (
                <>
                  <WallpaperForm
                    value={it.meta}
                    onChange={(meta) => patch(it.id, { meta })}
                    categories={data.categories}
                    tagNames={data.tagNames}
                  />
                  <Group justify="space-between">
                    <Checkbox
                      size="xs"
                      label={it.processed?.original
                        ? `Also upload original (${mb(it.processed.original.size)}, premium download)`
                        : 'Original too large (>15 MB), skipped'}
                      disabled={!it.processed?.original}
                      checked={it.includeOriginal && !!it.processed?.original}
                      onChange={(e) => patch(it.id, { includeOriginal: e.currentTarget.checked })}
                    />
                    <Button size="xs" variant="light" loading={it.state === 'uploading'}
                      disabled={!isMetaValid(it.meta)} onClick={() => uploadOne(it)}>
                      {it.state === 'error' ? 'Retry' : 'Upload this one'}
                    </Button>
                  </Group>
                </>
              )}
            </Stack>
          </Group>
        </Card>
      ))}
    </Stack>
  );
}

function StateBadge({ state }: { state: ItemState }) {
  const map: Record<ItemState, [string, string]> = {
    processing: ['gray', 'Processing…'],
    ready: ['blue', 'Ready'],
    uploading: ['violet', 'Uploading…'],
    done: ['green', 'Uploaded'],
    error: ['red', 'Error'],
  };
  const [color, label] = map[state];
  return <Badge color={color} leftSection={state === 'done' ? <IconCheck size={12} /> : undefined}>{label}</Badge>;
}
