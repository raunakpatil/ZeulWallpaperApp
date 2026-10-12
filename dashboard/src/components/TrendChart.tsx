import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { Box, Group, Text } from '@mantine/core';
import { smoothPath } from './chartPath';

export interface TrendPoint { day: string; value: number }

/**
 * Small dependency-free area chart. Hover (or tap) shows the day and value.
 * `compact` drops the axis labels for use as a sparkline inside stat cards.
 */
export function TrendChart({ points, height = 160, compact = false, label = 'value', color = '#d62976' }: {
  points: TrendPoint[]; height?: number; compact?: boolean; label?: string; color?: string;
}) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 600;
  const H = height;
  const pad = compact ? 2 : 24;
  const max = Math.max(1, ...points.map((p) => p.value));
  const step = points.length > 1 ? (W - pad * 2) / (points.length - 1) : 0;
  const xy = useMemo(() => points.map((p, i) => [pad + i * step, H - pad - (p.value / max) * (H - pad * 2)] as const), [points, pad, step, H, max]);
  if (!points.length) return null;

  const line = smoothPath(xy);
  const area = `${line} L${xy[xy.length - 1][0].toFixed(1)},${H - pad} L${xy[0][0].toFixed(1)},${H - pad} Z`;
  const total = points.reduce((s, p) => s + p.value, 0);
  const gid = `g-${label.replace(/\W/g, '')}-${compact ? 'c' : 'f'}`;

  function onMove(clientX: number, rect: DOMRect) {
    const x = ((clientX - rect.left) / rect.width) * W;
    setHover(Math.max(0, Math.min(points.length - 1, Math.round((x - pad) / (step || 1)))));
  }

  const h = hover !== null ? points[hover] : null;
  return (
    <Box>
      {!compact && (
        <Group justify="space-between" mb={4}>
          <Text size="xs" c="dimmed">{h ? dayjs(h.day).format('ddd D MMM') : `Last ${points.length} days`}</Text>
          <Text size="xs" fw={700}>{h ? `${h.value.toLocaleString('en-IN')} ${label}` : `${total.toLocaleString('en-IN')} ${label} total`}</Text>
        </Group>
      )}
      <svg
        viewBox={`0 0 ${W} ${H}`}
        width="100%"
        height={H}
        preserveAspectRatio="none"
        role="img"
        aria-label={`${label} over the last ${points.length} days, ${total} in total`}
        onMouseMove={(e) => onMove(e.clientX, e.currentTarget.getBoundingClientRect())}
        onTouchStart={(e) => onMove(e.touches[0].clientX, e.currentTarget.getBoundingClientRect())}
        onMouseLeave={() => setHover(null)}
        style={{ display: 'block', cursor: compact ? 'default' : 'crosshair' }}
      >
        <defs>
          <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.35" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        {!compact && [0.25, 0.5, 0.75].map((f) => (
          <line key={f} x1={pad} x2={W - pad} y1={pad + f * (H - pad * 2)} y2={pad + f * (H - pad * 2)} stroke="currentColor" strokeOpacity="0.08" />
        ))}
        <g className="draw-in">
        <path d={area} fill={`url(#${gid})`} />
        <path d={line} fill="none" stroke={color} strokeWidth={compact ? 2 : 2.5} strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
        </g>
        {hover !== null && !compact && (
          <>
            <line x1={xy[hover][0]} x2={xy[hover][0]} y1={pad} y2={H - pad} stroke="currentColor" strokeOpacity="0.25" vectorEffect="non-scaling-stroke" />
            <circle cx={xy[hover][0]} cy={xy[hover][1]} r={4} fill={color} />
          </>
        )}
      </svg>
      {!compact && (
        <Group justify="space-between" mt={2}>
          <Text size="xs" c="dimmed">{dayjs(points[0].day).format('D MMM')}</Text>
          <Text size="xs" c="dimmed">{dayjs(points[points.length - 1].day).format('D MMM')}</Text>
        </Group>
      )}
    </Box>
  );
}
