import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Card, Group, Text } from '@mantine/core';
import { IconChevronRight } from '../lib/icons';

export type Tone = 'indigo' | 'sky' | 'green' | 'lime' | 'gold' | 'rose';

/** Counts a plain number ("1,840" or 1840) up from its previous value; anything else is shown as is. */
function useCountUp(value: ReactNode): ReactNode {
  const target = typeof value === 'number' ? value
    : typeof value === 'string' && /^[\d,]+$/.test(value) ? Number(value.replace(/,/g, '')) : null;
  const [shown, setShown] = useState(0);
  const from = useRef(0);
  useEffect(() => {
    if (target === null) return;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const start = performance.now(), a = from.current, dur = reduce ? 0 : 700;
    let raf = 0;
    const tick = (t: number) => {
      const p = dur ? Math.min(1, (t - start) / dur) : 1;
      setShown(Math.round(a + (target - a) * (1 - Math.pow(1 - p, 3)))); // ease-out cubic
      if (p < 1) raf = requestAnimationFrame(tick);
      else from.current = target;
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target]);
  return target === null ? value : shown.toLocaleString('en-IN');
}

/** KPI card (Apple Health style): icon + label header, big left-aligned number, optional note or sparkline. */
export function StatCard({ tone, label, value, sub, icon, to, children }: {
  tone: Tone; label: string; value: ReactNode; sub?: ReactNode; icon: ReactNode; to?: string; children?: ReactNode;
}) {
  const counted = useCountUp(value);
  const body = (
    <div style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Group gap={10} wrap="nowrap">
        <div className="stat-icon">{icon}</div>
        <Text size="sm" fw={600} c="dimmed" truncate className="stat-label" style={{ flex: 1 }}>{label}</Text>
        {to && <IconChevronRight size={16} className="stat-chevron" aria-hidden />}
      </Group>
      <Text fw={700} fz={34} lh={1.1} mt="md" className="stat-value">{counted}</Text>
      {sub && <Text size="xs" c="dimmed" mt={4} className="stat-sub">{sub}</Text>}
      {children && <div style={{ marginTop: 'auto', paddingTop: 8 }}>{children}</div>}
    </div>
  );
  return to
    ? <Card className="stat-card" data-tone={tone} padding="lg" radius="lg" component={Link} to={to}>{body}</Card>
    : <Card className="stat-card" data-tone={tone} padding="lg" radius="lg">{body}</Card>;
}
