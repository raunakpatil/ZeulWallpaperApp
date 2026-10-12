import { useCallback, useEffect, useState } from 'react';
import { supabase } from './supabase';

/** Admin profile photos live in dashboard/public/avatars/<name before @>.jpg (square; shown as a circle). */
export const avatarFor = (email: string) =>
  `${import.meta.env.BASE_URL}avatars/${email.split('@')[0].toLowerCase()}.jpg`;

export const TEAM = [
  { name: 'Raunak Patil', role: 'Backend, dashboard & ads', email: 'raunakpatil15@gmail.com' },
  { name: 'Ghanshyam Paunikar', role: 'Android app', email: 'ghanshyampaunikar@gmail.com' },
];

/**
 * Name to show for the signed-in admin: the profile's display_name if they set one, otherwise the
 * team name for their email, otherwise the part of the email before "@".
 * (New accounts get display_name = email prefix automatically, which counts as "not set".)
 */
export function useDisplayName(userId: string, email: string) {
  const prefix = email.split('@')[0];
  const fallback = TEAM.find((m) => m.email === email.toLowerCase())?.name ?? prefix;
  const [stored, setStored] = useState<string | null>(null);

  useEffect(() => {
    supabase.from('profiles').select('display_name').eq('id', userId).maybeSingle()
      .then(({ data }) => setStored(data?.display_name ?? null));
  }, [userId]);

  const save = useCallback(async (name: string) => {
    const clean = name.trim().slice(0, 60);
    const { error } = await supabase.from('profiles').update({ display_name: clean || null }).eq('id', userId);
    if (error) throw new Error(error.message);
    setStored(clean || null);
  }, [userId]);

  const name = stored && stored !== prefix ? stored : fallback;
  return { name, save };
}
