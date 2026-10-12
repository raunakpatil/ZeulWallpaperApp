import { useMemo, useRef, useState } from 'react';
import dayjs from 'dayjs';
import { Box, Group, Switch, Text } from '@mantine/core';
import { smoothPath } from './chartPath';

export interface Series { key: string; label: string; color: string; values: number[] }

/**
 * Multi-series smooth line chart with legend toggles and a floating tooltip card
 * (layout from the Jobie "Vacancy stats" reference). Days are the shared x axis.
 */
export function MultiTrendChart({ days, series, height = 260, initiallyOn }: {
  days: string[]; series: Series[]; height?: number; initiallyOn?: string[];
}) {
  const [on, setOn] = useState<Set<string>>(new Set(initiallyOn ?? series.map((s) => s.key)));
  const [hover, setHover] = useState<number | null>(null);
  const wrap = useRef<HTMLDivElement>(null);
  const W = 800, H = height, padL = 36, padR = 12, padT = 16, padB = 26;
  const visible = series.filter((s) => on.has(s.key));
  const max = Math.max(1, ...visible.flatMap((s) => s.values));
  const niceMax = useMemo(() => {
    const p = Math.pow(10, Math.floor(Math.log10(max)));
    return Math.ceil(max / p) * p;
  }, [max]);
  const step = days.length > 1 ? (W - padL - padR) / (days.length - 1) : 0;
  const x = (i: number) => padL + i * step;
  const y = (v: number) => padT + (1 - v / niceMax) * (H - padT - padB);
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(niceMax * f));
  const labelEvery = Math.max(1, Math.ceil(days.length / 8));

  function onMove(clientX: number) {
    const r = wrap.current?.getBoundingClientRect();
    if (!r || !days.length) return;
    const px = ((clientX - r.left) / r.width) * W;
    setHover(Math.max(0, Math.min(days.length - 1, Math.round((px - padL) / (step || 1)))));
  }

  const tipLeftPct = hover !== null ? (x(hover) / W) * 100 : 0;

  return (
    <Box>
      <Group gap="lg" mb="sm" wrap="wrap">
        {series.map((s) => (
          <Switch key={s.key} size="sm" checked={on.has(s.key)} label={s.label}
            color={s.color} styles={{ label: { fontWeight: 600 } }}
            onChange={(e) => {
              const next = new Set(on);
              if (e.currentTarget.checked) next.add(s.key); else if (next.size > 1) next.delete(s.key);
              setOn(next);
            }} />
        ))}
      </Group>
      <div ref={wrap} style={{ position: 'relative' }}
        onMouseMove={(e) => onMove(e.clientX)} onMouseLeave={() => setHover(null)}
        onTouchStart={(e) => onMove(e.touches[0].clientX)} onTouchMove={(e) => onMove(e.touches[0].clientX)}>
        <svg viewBox={`0 0 ${W} ${H}`} width="100%" height={H} preserveAspectRatio="none" role="img"
          aria-label={`Trend of ${visible.map((s) => s.label).join(', ')} over ${days.length} days`} style={{ display: 'block' }}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={padL} x2={W - padR} y1={y(t)} y2={y(t)} stroke="currentColor" strokeOpacity="0.08" strokeDasharray="3 5" vectorEffect="non-scaling-stroke" />
            </g>
          ))}
          {visible.map((s) => {
            const pts = s.values.map((v, i) => [x(i), y(v)] as const);
            return (
              <g key={s.key} className="draw-in">
                <path d={smoothPath(pts)} fill="none" stroke={s.color} strokeWidth={3} strokeLinecap="round" vectorEffect="non-scaling-stroke"
                  style={{ filter: `drop-shadow(0 4px 6px ${s.color}55)` }} />
              </g>
            );
          })}
          {hover !== null && (
            <line x1={x(hover)} x2={x(hover)} y1={padT} y2={H - padB} stroke="currentColor" strokeOpacity="0.25" strokeDasharray="4 4" vectorEffect="non-scaling-stroke" />
          )}
        </svg>
        {/* y labels and x labels as HTML so they don't stretch with the SVG */}
        {ticks.map((t) => (
          <Text key={t} size="10px" c="dimmed" style={{ position: 'absolute', left: 0, top: `${(y(t) / H) * 100}%`, transform: 'translateY(-50%)' }}>{t.toLocaleString('en-IN')}</Text>
        ))}
        {days.map((d, i) => ((i % labelEvery === 0 && days.length - 1 - i >= labelEvery / 2) || i === days.length - 1) && (
          <Text key={d} size="10px" c="dimmed" style={{ position: 'absolute', bottom: 0, left: `${(x(i) / W) * 100}%`, transform: 'translateX(-50%)', whiteSpace: 'nowrap' }}>
            {dayjs(d).format('D MMM')}
          </Text>
        ))}
        {hover !== null && visible.map((s) => (
          <span key={s.key} style={{
            position: 'absolute', left: `${tipLeftPct}%`, top: `${(y(s.values[hover]) / H) * 100}%`, width: 12, height: 12,
            borderRadius: '50%', background: s.color, border: '2px solid var(--mantine-color-body)', transform: 'translate(-50%, -50%)',
            boxShadow: `0 0 10px ${s.color}`, pointerEvents: 'none',
          }} />
        ))}
        {hover !== null && (
          <div className="chart-tip" style={{ left: `${tipLeftPct}%`, transform: tipLeftPct > 70 ? 'translateX(calc(-100% - 14px))' : 'translateX(14px)' }}>
            <Text size="xs" c="dimmed" ta="center" mb={4}>{dayjs(days[hover]).format('ddd, D MMM YYYY')}</Text>
            <Group gap="md" wrap="nowrap">
              {visible.map((s) => (
                <div key={s.key}>
                  <Group gap={6} wrap="nowrap">
                    <span style={{ width: 10, height: 10, borderRadius: '50%', background: s.color, display: 'inline-block' }} />
                    <Text fw={800} size="lg">{s.values[hover].toLocaleString('en-IN')}</Text>
                  </Group>
                  <Text size="xs" c="dimmed">{s.label}</Text>
                </div>
              ))}
            </Group>
          </div>
        )}
      </div>
    </Box>
  );
}
