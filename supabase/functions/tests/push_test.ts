//   npx deno test --allow-env --allow-net supabase/functions/tests/
import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  accessToken, buildMessage, linkFor, parseManual, parseServiceAccount, pemToDer, sendFcm, signJwt, type ServiceAccount,
} from "../_shared/push.ts";

const WP = "11111111-2222-3333-4444-555555555555";

Deno.test("parseManual accepts a valid request and derives the type", () => {
  const r = parseManual({ title: " Fresh drop ", body: "20 new AMOLED", topic: "new_drops", env: "prod", wallpaper_id: WP });
  assert(!Array.isArray(r));
  assertEquals(r.title, "Fresh drop");
  assertEquals(r.type, "new_drop");
  assertEquals((parseManual({ title: "x", topic: "all", env: "debug" }) as { type: string }).type, "announcement");
  assertEquals((parseManual({ title: "x", topic: "daily_wallpaper", env: "debug" }) as { type: string }).type, "daily");
  assertEquals((parseManual({ title: "x", topic: "new_drops", env: "debug", collection_id: WP }) as { type: string }).type, "collection");
});

Deno.test("parseManual rejects bad input", () => {
  const errs = parseManual({ title: "", topic: "everyone", env: "staging", wallpaper_id: "nope", section: "hot" }) as string[];
  assert(Array.isArray(errs));
  for (const want of ["title is required", "topic must", "env must", "wallpaper_id is not", "not several"]) {
    assert(errs.some((e) => e.includes(want)), `missing error: ${want} in ${errs.join(" | ")}`);
  }
  assert((parseManual({ title: "x".repeat(66), topic: "all", env: "prod" }) as string[])[0].includes("longer than 65"));
  assert((parseManual({ title: "x", topic: "all", env: "prod", section: "hot" }) as string[])[0].includes("section must"));
});

Deno.test("links follow the contract", () => {
  assertEquals(linkFor({ wallpaper_id: WP }), `zeul://wallpaper/${WP}`);
  assertEquals(linkFor({ collection_id: WP }), `zeul://collection/${WP}`);
  assertEquals(linkFor({ section: "amoled" }), "zeul://section/amoled");
  assertEquals(linkFor({}), "");
});

Deno.test("message is data-only, high priority, all strings", () => {
  const m = buildMessage("prod_new_drops", {
    type: "new_drop", title: "t", body: "b", image_url: "", link: "", wallpaper_id: "", collection_id: "", push_id: "p",
  });
  assertEquals(m.message.topic, "prod_new_drops");
  assertEquals(m.message.android.priority, "high");
  assertEquals("notification" in m.message, false);
  assert(Object.values(m.message.data).every((v) => typeof v === "string"));
});

async function testAccount(): Promise<{ sa: ServiceAccount; pub: CryptoKey }> {
  const kp = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"],
  );
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", kp.privateKey));
  const b64 = btoa(String.fromCharCode(...der)).match(/.{1,64}/g)!.join("\n");
  const pem = `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----\n`;
  return { sa: { project_id: "demo-proj", client_email: "push@demo-proj.iam.gserviceaccount.com", private_key: pem }, pub: kp.publicKey };
}

Deno.test("service account JSON parses raw, base64 and with escaped newlines", async () => {
  const { sa } = await testAccount();
  const raw = JSON.stringify(sa);
  assertEquals(parseServiceAccount(raw)?.project_id, "demo-proj");
  assertEquals(parseServiceAccount(btoa(raw))?.client_email, sa.client_email);
  assertEquals(parseServiceAccount("{}"), null);
  assertEquals(parseServiceAccount(undefined), null);
  assertEquals(pemToDer(sa.private_key.replace(/\n/g, "\\n")).length, pemToDer(sa.private_key).length);
});

Deno.test("signed JWT verifies with the matching public key and has the FCM scope", async () => {
  const { sa, pub } = await testAccount();
  const jwt = await signJwt(sa, 1_800_000_000);
  const [h, c, s] = jwt.split(".");
  const dec = (x: string) => Uint8Array.from(atob(x.replace(/-/g, "+").replace(/_/g, "/") + "==".slice(0, (4 - x.length % 4) % 4)), (ch) => ch.charCodeAt(0));
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", pub, dec(s), new TextEncoder().encode(`${h}.${c}`));
  assert(ok, "signature must verify");
  const claims = JSON.parse(new TextDecoder().decode(dec(c)));
  assertEquals(claims.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(claims.exp - claims.iat, 3600);
});

Deno.test("send flow: token exchange then FCM call (mocked), token cached", async () => {
  const { sa } = await testAccount();
  const calls: string[] = [];
  const fake = (async (url: string | URL | Request, init?: RequestInit) => {
    const u = String(url);
    calls.push(u);
    if (u.includes("oauth2")) return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }));
    assertEquals((init?.headers as Record<string, string>).Authorization, "Bearer tok");
    return new Response(JSON.stringify({ name: "projects/demo-proj/messages/123" }));
  }) as typeof fetch;
  const msg = buildMessage("debug_all", { type: "announcement", title: "t", body: "", image_url: "", link: "", wallpaper_id: "", collection_id: "", push_id: "p" });
  assertEquals(await sendFcm(sa, msg, fake), "projects/demo-proj/messages/123");
  assertEquals(await sendFcm(sa, msg, fake), "projects/demo-proj/messages/123");
  assertEquals(calls.filter((u) => u.includes("oauth2")).length, 1);
  assert(calls.some((u) => u.endsWith("/projects/demo-proj/messages:send")));
  assertEquals(await accessToken(sa, fake), "tok");
});

Deno.test("FCM errors surface with their message", async () => {
  const { sa } = await testAccount();
  const fake = (async (url: string | URL | Request) => String(url).includes("oauth2")
    ? new Response(JSON.stringify({ access_token: "tok2", expires_in: 3600 }))
    : new Response(JSON.stringify({ error: { message: "Requested entity was not found." } }), { status: 404 })) as typeof fetch;
  const msg = buildMessage("debug_all", { type: "announcement", title: "t", body: "", image_url: "", link: "", wallpaper_id: "", collection_id: "", push_id: "p" });
  try {
    await sendFcm(sa, msg, fake);
    throw new Error("should have thrown");
  } catch (e) {
    assert((e as Error).message.includes("FCM 404: Requested entity was not found."));
  }
});
