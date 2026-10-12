//   npx deno test --allow-env --allow-net supabase/functions/tests/
import { assert, assertEquals } from "jsr:@std/assert@1";
import { authorized, decide, supabaseIds, type RcEvent } from "../_shared/revenuecat.ts";

const U1 = "11111111-2222-3333-4444-555555555555";
const U2 = "66666666-7777-8888-9999-000000000000";
const NOW = Date.UTC(2026, 9, 1);
const later = NOW + 30 * 864e5;
const earlier = NOW - 864e5;
const ev = (e: Partial<RcEvent>): RcEvent => ({ id: "e1", type: "INITIAL_PURCHASE", app_user_id: U1, entitlement_ids: ["premium"], expiration_at_ms: later, ...e });

Deno.test("only Supabase user ids are used; anonymous ids are skipped", () => {
  assertEquals(supabaseIds([U1, "$RCAnonymousID:abc", undefined, U1.toUpperCase(), U2]), [U1, U2]);
});

Deno.test("purchase and renewal grant premium until expiry", () => {
  for (const type of ["INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE"]) {
    const c = decide(ev({ type }), NOW)!;
    assertEquals(c[0].premium, true, type);
    assertEquals(c[0].premiumUntil, new Date(later).toISOString());
    assertEquals(c[0].userIds, [U1]);
  }
});

Deno.test("trial start (INITIAL_PURCHASE with period TRIAL) grants premium", () => {
  assertEquals(decide(ev({ type: "INITIAL_PURCHASE" }), NOW)![0].premium, true);
});

Deno.test("a grant that already expired does not turn premium on", () => {
  assertEquals(decide(ev({ type: "RENEWAL", expiration_at_ms: earlier }), NOW)![0].premium, false);
});

Deno.test("cancellation keeps access until expiry, expiration removes it", () => {
  const cancel = decide(ev({ type: "CANCELLATION" }), NOW)![0];
  assertEquals(cancel.premium, null);
  assertEquals(cancel.premiumUntil, new Date(later).toISOString());
  assertEquals(decide(ev({ type: "CANCELLATION", expiration_at_ms: earlier }), NOW)![0].premium, false);
  assertEquals(decide(ev({ type: "EXPIRATION", expiration_at_ms: earlier }), NOW)![0].premium, false);
  assertEquals(decide(ev({ type: "BILLING_ISSUE" }), NOW)![0].premium, null);
});

Deno.test("lifetime purchase (no expiration) is premium with no end date", () => {
  const c = decide(ev({ type: "NON_RENEWING_PURCHASE", expiration_at_ms: null }), NOW)![0];
  assertEquals(c.premium, true);
  assertEquals(c.premiumUntil, null);
});

Deno.test("aliases are all updated; other entitlements and TEST events are ignored", () => {
  assertEquals(decide(ev({ app_user_id: "$RCAnonymousID:x", aliases: [U1, U2] }), NOW)![0].userIds, [U1, U2]);
  assertEquals(decide(ev({ entitlement_ids: ["something_else"] }), NOW), null);
  assertEquals(decide(ev({ type: "TEST" }), NOW), null);
  assertEquals(decide(ev({ app_user_id: "$RCAnonymousID:x" }), NOW), null);
  assertEquals(decide(ev({ type: "SUBSCRIBER_ALIAS" }), NOW), null);
});

Deno.test("transfer removes premium from the old account", () => {
  const c = decide({ id: "t", type: "TRANSFER", transferred_from: [U1], transferred_to: [U2] }, NOW)!;
  assertEquals(c, [{ userIds: [U1], premium: false, premiumUntil: null }]);
});

Deno.test("authorization header check", () => {
  assert(authorized("Bearer s3cret-value", "s3cret-value"));
  assert(authorized("s3cret-value", "s3cret-value"));
  assert(!authorized("Bearer wrong-value", "s3cret-value"));
  assert(!authorized(null, "s3cret-value"));
  assert(!authorized("Bearer s3cret-value", undefined));
});
