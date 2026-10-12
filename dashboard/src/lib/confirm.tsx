import { Text } from '@mantine/core';
import { modals } from '@mantine/modals';
import type { ReactNode } from 'react';

/** Promise-based confirmation dialog (replaces window.confirm). Resolves true when confirmed. */
export function confirmAction({ title, message, confirmLabel = 'Confirm', danger = false }: {
  title: string; message: ReactNode; confirmLabel?: string; danger?: boolean;
}): Promise<boolean> {
  return new Promise((resolve) => {
    modals.openConfirmModal({
      title: <Text fw={700}>{title}</Text>,
      children: typeof message === 'string' ? <Text size="sm">{message}</Text> : message,
      labels: { confirm: confirmLabel, cancel: 'Cancel' },
      confirmProps: danger ? { color: 'red' } : { variant: 'gradient' },
      centered: true,
      radius: 'lg',
      onConfirm: () => resolve(true),
      onCancel: () => resolve(false),
      onClose: () => resolve(false),
    });
  });
}
