// Unit tests always run. The GitHub integration test runs only when CDN_IT=1 plus
// GITHUB_TOKEN and CDN_REPO are set; it commits a throwaway folder and deletes it again.
//   npx deno test --allow-env --allow-net supabase/functions/tests/
import { assert, assertEquals, assertMatch } from "jsr:@std/assert@1";
import { detectImageType, fileName, newStorageKey, STORAGE_KEY_RE, validateVariant } from "../_shared/images.ts";
import { commitFiles, configFromEnv, deleteDir } from "../_shared/github.ts";

const webp = (n = 32) => {
  const b = new Uint8Array(n);
  b.set([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]);
  return b;
};
const jpg = () => new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3]);
const png = () => new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0]);

Deno.test("detectImageType by magic bytes", () => {
  assertEquals(detectImageType(webp()), "webp");
  assertEquals(detectImageType(jpg()), "jpg");
  assertEquals(detectImageType(png()), "png");
  assertEquals(detectImageType(new TextEncoder().encode("<svg>hello</svg>")), null);
  assertEquals(detectImageType(new Uint8Array()), null);
});

Deno.test("validateVariant rules", () => {
  assertEquals(validateVariant("thumb", null), "thumb is required");
  assertEquals(validateVariant("original", null), null);
  assertEquals((validateVariant("thumb", jpg()) as string).includes("must be webp"), true);
  assertEquals((validateVariant("original", webp()) as string).includes("must be jpg or png"), true);
  assertEquals((validateVariant("thumb", webp(400 * 1024)) as string).includes("max is"), true);
  const ok = validateVariant("original", png());
  assert(typeof ok === "object" && ok !== null);
  assertEquals(fileName(ok), "original.png");
  assertEquals(fileName(validateVariant("full", webp()) as never), "full.webp");
});

Deno.test("storage keys are random and well-formed", () => {
  const a = newStorageKey(), b = newStorageKey();
  assertMatch(a, STORAGE_KEY_RE);
  assert(a !== b);
  assert(!STORAGE_KEY_RE.test("w/../etc"));
});

Deno.test({
  name: "GitHub: commit folder then delete it",
  ignore: Deno.env.get("CDN_IT") !== "1",
  fn: async () => {
    const cfg = configFromEnv();
    const key = newStorageKey();
    const sha = await commitFiles(cfg, [
      { path: `${key}/thumb.webp`, bytes: webp() },
      { path: `${key}/original.jpg`, bytes: jpg() },
    ], `test: add ${key}`);
    assertMatch(sha, /^[0-9a-f]{40}$/);
    const del = await deleteDir(cfg, key, `test: remove ${key}`);
    assertMatch(del ?? "", /^[0-9a-f]{40}$/);
    assertEquals(await deleteDir(cfg, key, "noop"), null);
  },
});
