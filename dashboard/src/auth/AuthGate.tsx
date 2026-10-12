import { useEffect, useState, type ReactNode } from 'react';
import type { Session } from '@supabase/supabase-js';
import {
  Alert, Anchor, Button, Center, Code, Loader, Paper, PasswordInput, PinInput, Stack, Tabs, Text, TextInput, Title,
} from '@mantine/core';
import { BrandMark } from '../components/BrandMark';
import { supabase } from '../lib/supabase';

type AdminState = 'unknown' | 'yes' | 'no';

export function AuthGate({ children }: { children: (session: Session) => ReactNode }) {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const [admin, setAdmin] = useState<AdminState>('unknown');

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data } = supabase.auth.onAuthStateChange((_e, s) => setSession(s));
    return () => data.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (!session) { setAdmin('unknown'); return; }
    supabase.rpc('is_admin').then(({ data }) => setAdmin(data === true ? 'yes' : 'no'));
  }, [session]);

  if (session === undefined || (session && admin === 'unknown')) {
    return <Center h="100vh"><Loader /></Center>;
  }
  if (!session) return <Login />;
  if (admin === 'no') return <NotAdmin email={session.user.email ?? ''} />;
  return <>{children(session)}</>;
}

/** Emails carry a 6-digit code only (no links: links to another domain push them into spam). */
type CodeStep = { email: string; type: 'email' | 'signup' };

function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [codeStep, setCodeStep] = useState<CodeStep | null>(null);

  async function run(fn: () => Promise<{ error: { message: string } | null }>, okText?: string): Promise<boolean> {
    setBusy(true);
    setMsg(null);
    const { error } = await fn();
    setBusy(false);
    if (error) setMsg({ ok: false, text: error.message });
    else if (okText) setMsg({ ok: true, text: okText });
    return !error;
  }

  if (codeStep) {
    return <CodeEntry step={codeStep} onBack={() => { setCodeStep(null); setMsg(null); }} />;
  }

  return (
    <Center h="100vh" p="md">
      <Paper withBorder className="soft-card" p="xl" radius="xl" w={400} maw="100%">
        <Stack align="center" gap={6} mb="lg">
          <BrandMark size={60} />
          <Title order={2} className="ig-text brand-word" mt={6}>Zeul<span className="brand-word-light"> Studio</span></Title>
          <Text c="dimmed" size="sm">Sign in to manage Zeul</Text>
        </Stack>
        <Tabs defaultValue="signin" variant="pills" radius="xl">
          <Tabs.List grow mb="md">
            <Tabs.Tab value="signin">Sign in</Tabs.Tab>
            <Tabs.Tab value="signup">Create account</Tabs.Tab>
          </Tabs.List>
          <Stack>
            <TextInput label="Email" value={email} onChange={(e) => setEmail(e.currentTarget.value)} autoComplete="email" />
            <PasswordInput label="Password" value={password} onChange={(e) => setPassword(e.currentTarget.value)} />
            <Tabs.Panel value="signin">
              <Stack gap="xs">
                <Button fullWidth loading={busy}
                  variant="gradient" onClick={() => run(() => supabase.auth.signInWithPassword({ email, password }))}>
                  Sign in
                </Button>
                <Anchor size="sm" ta="center" component="button" type="button"
                  onClick={async () => {
                    if (await run(() => supabase.auth.signInWithOtp({ email: email.trim(), options: { shouldCreateUser: false } }))) {
                      setCodeStep({ email: email.trim(), type: 'email' });
                    }
                  }}>
                  Email me a sign-in code instead
                </Anchor>
              </Stack>
            </Tabs.Panel>
            <Tabs.Panel value="signup">
              <Button fullWidth loading={busy} variant="gradient"
                onClick={async () => {
                  if (await run(() => supabase.auth.signUp({ email: email.trim(), password }))) {
                    setCodeStep({ email: email.trim(), type: 'signup' });
                  }
                }}>
                Create account
              </Button>
            </Tabs.Panel>
            {msg && <Alert color={msg.ok ? 'green' : 'red'}>{msg.text}</Alert>}
          </Stack>
        </Tabs>
      </Paper>
    </Center>
  );
}

function CodeEntry({ step, onBack }: { step: CodeStep; onBack: () => void }) {
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  async function verify(token: string) {
    if (token.length !== 6) return;
    setBusy(true);
    setMsg(null);
    // Success fires onAuthStateChange, which swaps this screen for the dashboard.
    const { error } = await supabase.auth.verifyOtp({ email: step.email, token, type: step.type });
    setBusy(false);
    if (error) setMsg({ ok: false, text: error.message.includes('expired') || error.message.includes('invalid')
      ? 'That code is wrong or has expired. Check the latest email, or send a new one.' : error.message });
  }

  async function resend() {
    setBusy(true);
    setMsg(null);
    const { error } = step.type === 'signup'
      ? await supabase.auth.resend({ type: 'signup', email: step.email })
      : await supabase.auth.signInWithOtp({ email: step.email, options: { shouldCreateUser: false } });
    setBusy(false);
    setMsg(error ? { ok: false, text: error.message } : { ok: true, text: 'New code sent. It can take a minute (check Spam too).' });
  }

  return (
    <Center h="100vh" p="md">
      <Paper withBorder className="soft-card" p="xl" radius="xl" w={400} maw="100%">
        <Stack align="center" gap="sm">
          <BrandMark size={48} />
          <Title order={3} ta="center">Check your email</Title>
          <Text c="dimmed" size="sm" ta="center">
            We sent a 6-digit code to <b>{step.email}</b>.{step.type === 'signup' ? ' Enter it to confirm your account.' : ''} It expires in 10 minutes.
          </Text>
          <PinInput length={6} type="number" oneTimeCode autoFocus size="lg" value={code} disabled={busy}
            onChange={setCode} onComplete={verify} aria-label="6-digit code" my="xs" />
          <Button fullWidth variant="gradient" loading={busy} disabled={code.length !== 6} onClick={() => verify(code)}>
            {step.type === 'signup' ? 'Confirm account' : 'Sign in'}
          </Button>
          {msg && <Alert w="100%" color={msg.ok ? 'green' : 'red'}>{msg.text}</Alert>}
          <Text size="sm" c="dimmed" ta="center">
            No email? <Anchor component="button" type="button" size="sm" onClick={resend} disabled={busy}>Send a new code</Anchor>
            {' · '}
            <Anchor component="button" type="button" size="sm" onClick={onBack}>Back</Anchor>
          </Text>
        </Stack>
      </Paper>
    </Center>
  );
}

function NotAdmin({ email }: { email: string }) {
  return (
    <Center h="100vh" p="md">
      <Paper withBorder p="xl" radius="md" maw={560}>
        <Title order={3} mb="sm">Not an admin yet</Title>
        <Text mb="sm">You're signed in as <b>{email}</b>, but this account isn't an admin. In the Supabase SQL Editor, run:</Text>
        <Code block mb="md" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{`update public.profiles set role = 'admin'\nwhere id = (select id from auth.users where email = '${email}');`}</Code>
        <Button.Group>
          <Button onClick={() => window.location.reload()}>I've done it, reload</Button>
          <Button variant="default" onClick={() => supabase.auth.signOut()}>Sign out</Button>
        </Button.Group>
      </Paper>
    </Center>
  );
}
