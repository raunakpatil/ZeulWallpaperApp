import { SUPABASE_URL } from './supabase';
import { authHeaders } from './api';

const FN_URL = `${SUPABASE_URL}/functions/v1/send-push`;

export type Topic = 'all' | 'new_drops' | 'daily_wallpaper';
export type PushEnv = 'prod' | 'debug';

export interface SendRequest {
  title: string;
  body: string;
  topic: Topic;
  env: PushEnv;
  wallpaper_id?: string | null;
  collection_id?: string | null;
  section?: string | null;
}

async function call(init: RequestInit) {
  let res: Response;
  try {
    res = await fetch(FN_URL, { ...init, headers: { ...(await authHeaders()), 'Content-Type': 'application/json' } });
  } catch {
    throw new Error('Could not reach the send-push function. It is probably not deployed yet.');
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = body.details ? `${body.error}: ${body.details.join('; ')}` : body.error ?? `${res.status} ${res.statusText}`;
    throw new Error(res.status === 404 ? 'The send-push function is not deployed yet.' : msg);
  }
  return body;
}

export async function pushSetupStatus(): Promise<{ configured: boolean; project_id: string | null; cron_secret_set: boolean } | null> {
  try { return await call({ method: 'GET' }); } catch { return null; }
}

export const sendPush = (req: SendRequest) => call({ method: 'POST', body: JSON.stringify(req) });

export const sendDailyNow = (env: PushEnv) => call({ method: 'POST', body: JSON.stringify({ mode: 'daily', env }) });
