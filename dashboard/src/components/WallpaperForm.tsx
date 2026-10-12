import {
  Group, SegmentedControl, Select, SimpleGrid, Stack, Switch, TagsInput, Text, TextInput,
} from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { IconCrown, IconDeviceMobile, IconDeviceTablet, IconDevices, IconMoon, IconStar } from '../lib/icons';
import type { Category, DeviceType, PublishStatus, WallpaperMeta } from '../lib/types';
import { LICENSES } from '../lib/types';
import { FIELD_TIPS } from '../lib/tips';
import { LabelWithTip } from './Help';

interface Props {
  value: WallpaperMeta;
  onChange: (v: WallpaperMeta) => void;
  categories: Category[];
  tagNames: string[];
  compact?: boolean;
}

const STATUS_HINT: Record<PublishStatus, string> = {
  draft: 'Hidden from the app until you publish.',
  published: 'Live in the app right away.',
  scheduled: 'Goes live automatically at the time you pick.',
};

export function WallpaperForm({ value: v, onChange, categories, tagNames, compact }: Props) {
  const set = <K extends keyof WallpaperMeta>(k: K, val: WallpaperMeta[K]) => onChange({ ...v, [k]: val });

  return (
    <Stack gap="sm">
      <SimpleGrid cols={compact ? 1 : 2} spacing="sm">
        <TextInput label={<LabelWithTip label="Title" tip={FIELD_TIPS.title} />} required value={v.title}
          onChange={(e) => set('title', e.currentTarget.value)} />
        <Select
          label={<LabelWithTip label="Category" tip={FIELD_TIPS.category} />}
          placeholder="None"
          clearable
          searchable
          data={categories.map((c) => ({ value: c.id, label: c.name }))}
          value={v.category_id}
          onChange={(val) => set('category_id', val)}
        />
      </SimpleGrid>

      <TagsInput
        label={<LabelWithTip label="Tags" tip={FIELD_TIPS.tags} />}
        description="What would someone type to find this? Press Enter or comma after each tag."
        placeholder="e.g. galaxy, neon, minimal"
        data={tagNames}
        value={v.tags}
        onChange={(tags) => set('tags', tags.map((t) => t.toLowerCase()))}
        splitChars={[',']}
        clearable
        maxTags={25}
        limit={12}
      />

      <Group gap="lg" wrap="wrap" align="center">
        <Stack gap={4}>
          <Text size="sm" fw={500}><LabelWithTip label="Fits on" tip={FIELD_TIPS.device} /></Text>
          <SegmentedControl
            size="xs"
            radius="xl"
            data={[
              { value: 'phone', label: <Group gap={4} wrap="nowrap"><IconDeviceMobile size={14} />Phone</Group> },
              { value: 'tablet', label: <Group gap={4} wrap="nowrap"><IconDeviceTablet size={14} />Tablet</Group> },
              { value: 'both', label: <Group gap={4} wrap="nowrap"><IconDevices size={14} />Both</Group> },
            ]}
            value={v.device_type}
            onChange={(val) => set('device_type', val as DeviceType)}
          />
          {v.device_type === 'tablet' && (
            <Text size="xs" c="orange" maw={260}>Tablet only: hidden from phones, appears just in the Tablet section. Pick Both to show it everywhere.</Text>
          )}
        </Stack>
        <Switch mt={18} thumbIcon={<IconMoon size={10} />} label={<LabelWithTip label="AMOLED" tip={FIELD_TIPS.amoled} />}
          checked={v.is_amoled} onChange={(e) => set('is_amoled', e.currentTarget.checked)} />
        <Switch mt={18} thumbIcon={<IconStar size={10} />} label={<LabelWithTip label="Curated" tip={FIELD_TIPS.curated} />}
          checked={v.is_curated} onChange={(e) => set('is_curated', e.currentTarget.checked)} />
        <Switch mt={18} thumbIcon={<IconCrown size={10} />} label={<LabelWithTip label="Premium" tip={FIELD_TIPS.premium} />}
          checked={v.is_premium} onChange={(e) => set('is_premium', e.currentTarget.checked)} />
      </Group>

      <Stack gap={4}>
        <Text size="sm" fw={500}><LabelWithTip label="Visibility" tip={FIELD_TIPS.status} /></Text>
        <Group align="center" gap="sm" wrap="wrap">
          <SegmentedControl
            size="xs"
            radius="xl"
            data={[
              { label: 'Draft', value: 'draft' },
              { label: 'Publish now', value: 'published' },
              { label: 'Schedule', value: 'scheduled' },
            ]}
            value={v.status}
            onChange={(val) => set('status', val as PublishStatus)}
          />
          {v.status === 'scheduled' ? (
            <DateTimePicker size="xs" placeholder="Pick date & time" value={v.publish_at}
              onChange={(val) => set('publish_at', val)} minDate={new Date()} w={200}
              error={!v.publish_at ? 'Pick a time' : undefined} />
          ) : (
            <Text size="xs" c="dimmed">{STATUS_HINT[v.status]}</Text>
          )}
        </Group>
      </Stack>

      <SimpleGrid cols={compact ? 1 : 3} spacing="sm">
        <TextInput label={<LabelWithTip label="Author / artist" tip={FIELD_TIPS.author} />} placeholder="Your own work? Leave empty"
          value={v.author_name} onChange={(e) => set('author_name', e.currentTarget.value)} />
        <Select
          label={<LabelWithTip label="Licence" tip={FIELD_TIPS.license} />}
          required
          placeholder="Who owns the rights?"
          data={LICENSES}
          value={v.license || null}
          onChange={(val) => set('license', val ?? '')}
          error={!v.license ? 'Required for every wallpaper' : undefined}
        />
        <TextInput label={<LabelWithTip label="Source URL" tip={FIELD_TIPS.source} />} placeholder="https://…"
          value={v.source_url} onChange={(e) => set('source_url', e.currentTarget.value)} />
      </SimpleGrid>
    </Stack>
  );
}
