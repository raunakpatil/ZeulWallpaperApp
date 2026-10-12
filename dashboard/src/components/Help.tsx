import type { ReactNode } from 'react';
import { ActionIcon, Group, List, Popover, Stack, Text, ThemeIcon, Title, Tooltip } from '@mantine/core';
import { IconBulb, IconHelp, IconInfoCircle } from '../lib/icons';

/** Small ⓘ icon that explains a field on hover or tap. */
export function HelpTip({ children, size = 15 }: { children: ReactNode; size?: number }) {
  return (
    <Tooltip label={children} multiline w={280} events={{ hover: true, focus: true, touch: true }}>
      <span tabIndex={0} aria-label="More info" style={{ display: 'inline-flex', color: 'var(--mantine-color-dimmed)', cursor: 'help', verticalAlign: 'middle' }}>
        <IconInfoCircle size={size} />
      </span>
    </Tooltip>
  );
}

/** Field label with an attached help tip. */
export function LabelWithTip({ label, tip }: { label: ReactNode; tip: ReactNode }) {
  return (
    <Group gap={4} wrap="nowrap" component="span" style={{ display: 'inline-flex' }}>
      <span>{label}</span>
      <HelpTip size={14}>{tip}</HelpTip>
    </Group>
  );
}

/** Page title + one-line description + optional "Tips" popover and actions. */
export function PageHeader({ title, description, tips, actions }: {
  title: ReactNode;
  description?: ReactNode;
  tips?: ReactNode[];
  actions?: ReactNode;
}) {
  return (
    <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm" mb={4}>
      <Stack gap={2} style={{ minWidth: 0, flex: 1 }}>
        <Group gap={8} wrap="nowrap">
          <Title order={2} fz={26}>{title}</Title>
          {tips && tips.length > 0 && (
            <Popover width={340} position="bottom-start" shadow="md" withArrow>
              <Popover.Target>
                <Tooltip label="Tips for this page">
                  <ActionIcon variant="subtle" color="gray" radius="xl" aria-label="Tips for this page"><IconHelp size={20} /></ActionIcon>
                </Tooltip>
              </Popover.Target>
              <Popover.Dropdown>
                <Group gap={8} mb={6}>
                  <ThemeIcon variant="gradient" radius="xl" size="sm"><IconBulb size={14} /></ThemeIcon>
                  <Text fw={700} size="sm">Tips</Text>
                </Group>
                <List size="sm" spacing={6}>
                  {tips.map((t, i) => <List.Item key={i}>{t}</List.Item>)}
                </List>
              </Popover.Dropdown>
            </Popover>
          )}
        </Group>
        {description && <Text c="dimmed" size="sm" maw={720}>{description}</Text>}
      </Stack>
      {actions && <Group gap="xs">{actions}</Group>}
    </Group>
  );
}

/** Friendly empty state with an optional call to action. */
export function EmptyState({ icon, title, children, action }: { icon: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <Stack align="center" gap={6} py={48} px="md" ta="center">
      <ThemeIcon variant="gradient" size={56} radius="xl">{icon}</ThemeIcon>
      <Text fw={700} mt={6}>{title}</Text>
      {children && <Text c="dimmed" size="sm" maw={420}>{children}</Text>}
      {action && <div style={{ marginTop: 8 }}>{action}</div>}
    </Stack>
  );
}
