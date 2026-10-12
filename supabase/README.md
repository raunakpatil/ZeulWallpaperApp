# Supabase backend (owner: Dev A)

```
migrations/   schema + RPCs, applied in filename order (only Dev A adds files here)
seed.sql      starter categories
tests/        SQL smoke tests (run in a transaction, then rolled back)
```

## Apply to the hosted project

**Option 1: SQL editor (no tools needed).** In the Supabase dashboard, open SQL Editor and run, in order:
1. `migrations/20260930000100_core_schema.sql`
2. `migrations/20260930000200_rpcs.sql`
3. `migrations/20260930000300_cdn_base_url.sql`
4. `migrations/20260930000400_scheduled_jobs.sql` (pg_cron: hourly popularity, daily cleanup)
5. `migrations/20260930000500_collections_insights.sql` (collections RPCs + dashboard insights)
6. `migrations/20261001000600_push.sql` (push log, opens, daily push scheduler)
7. `migrations/20261001000700_billing.sql` (subscription events + premium expiry job)
8. `migrations/20261002000800_admin_users_trends.sql` (dashboard trend chart + Users page)
9. `migrations/20261002000900_abuse_limits.sql` (per-IP rate limits, profile field limits; safe to re-run)
10. `migrations/20261002001000_clean_tags.sql` (tag hygiene: letters/digits/spaces/hyphens, repairs cut-off tags; safe to re-run)
11. `migrations/20261002001100_category_hindi.sql` (categories.name_hi + Hindi names for the 12 categories; safe to re-run)
12. `seed.sql`

**Option 2: Supabase CLI.**
```bash
npx supabase login
npx supabase link --project-ref <project-ref>
npx supabase db push
```

## Image CDN + `upload-wallpaper` Edge Function (A5)

- CDN repo: https://github.com/raunakpatil/wallpaper-cdn (public, **GitHub Pages** → `https://raunakpatil.github.io/wallpaper-cdn/`)
- Files: `w/<10 hex>/thumb.webp | preview.webp | full.webp | original.jpg|png`, all committed in **one commit per wallpaper**.
- The function (`functions/upload-wallpaper`) is **admin-only**: `POST` multipart upload, `DELETE ?storage_key=`, `GET` for the usage meter.
- Budget: GitHub Pages sites should stay **under ~1 GB**. At about 1.5 MB per wallpaper (thumb + preview + full), that's roughly 600 wallpapers, fewer if you upload originals (max 15 MB each). The `repo_size_mb` field in responses tracks it. Move to R2 (A14) before ~800 MB.
- Uploading many wallpapers quickly can make an intermediate Pages build show "errored" (it gets superseded). The latest build always publishes everything.

**One-time setup (Raunak):**
1. GitHub → Settings → Developer settings → **Fine-grained tokens** → Generate:
   - Repository access: **Only select repositories → wallpaper-cdn**
   - Permissions → Repository → **Contents: Read and write** (nothing else)
   - Expiration: 1 year (set a reminder)
2. Deploy and set secrets. **Type the token yourself; never paste it into chat or commit it:**
```bash
npx supabase functions deploy upload-wallpaper --project-ref <project-ref>
npx supabase secrets set --project-ref <project-ref> CDN_REPO=raunakpatil/wallpaper-cdn GITHUB_TOKEN=<paste-token-here>
```

**Tests** (unit tests always run; the integration test commits and deletes a throwaway folder in the CDN repo):
```bash
npx deno test --allow-env --allow-net supabase/functions/tests/
CDN_IT=1 CDN_REPO=raunakpatil/wallpaper-cdn GITHUB_TOKEN=$(gh auth token) npx deno test --allow-env --allow-net supabase/functions/tests/
```

## Make yourself admin (once, after signing in to the dashboard/app)
```sql
update public.profiles set role = 'admin'
where id = (select id from auth.users where email = 'you@example.com');
```

## Run the tests locally (Docker)
```bash
docker run -d --name wp-pg -e POSTGRES_PASSWORD=localtest -p 54329:5432 supabase/postgres:15.19.0.002
# wait ~20 s, then:
docker exec -i wp-pg psql -U postgres -v ON_ERROR_STOP=1 < supabase/migrations/20260930000100_core_schema.sql
docker exec -i wp-pg psql -U postgres -v ON_ERROR_STOP=1 < supabase/migrations/20260930000200_rpcs.sql
docker exec -i wp-pg psql -U postgres -v ON_ERROR_STOP=1 < supabase/tests/smoke_a2_a3.sql   # ends with ALL CHECKS PASSED
```

## Security model (summary)
- RLS is on for every table. Guests and users can only **read** published content (`wallpapers_public` view) and app_config.
- Users read/write only their own favourites, unlocks and profile (display_name/avatar only; they can't change their own `role` or `is_premium`).
- Counters, events, stats and affinity are written **only** through the security-definer RPCs, which validate input and rate-limit per `device_id` **and per client IP** (0900: only a salted hash of the IP is kept, for 2 days; limits are generous because carriers share IPs). Reports are limited to 20/hour per IP (HTTP 429 above that).
- Never commit key files: service-account JSON, `google-services.json`, `.env`, `local.properties` are git-ignored; secrets go into Supabase secrets / GitHub secrets.
- Admins (`profiles.role = 'admin'`) can write all content tables and read events/stats/search logs/reports.
- Internal helpers live in the `private` schema, which isn't exposed by the API.

## Known limitation (fix in A14)
While images are on public GitHub Pages, premium originals can't be truly protected. Anyone who knows the path can fetch them. When moving to Cloudflare R2, serve originals through signed URLs from an Edge Function.

## Push notifications (A16)
1. Deploy: `npx supabase functions deploy send-push --project-ref <ref> --no-verify-jwt` (the function checks admin JWTs or the cron secret itself; pg_cron calls it without a JWT).
2. Firebase console → Project settings → Service accounts → **Generate new private key**.
3. Run `powershell -ExecutionPolicy Bypass -File supabase\set-firebase-key.ps1` and pick the file. It sets `FIREBASE_SERVICE_ACCOUNT` + a random `CRON_SECRET`, and copies one SQL line to the clipboard. Run that line in the SQL Editor so pg_cron can reach the function.
4. Test from the dashboard → Notifications with Test mode on (reaches `debug_*` topics only).

## Subscriptions (A10/A11)
1. RevenueCat (free): create a project + Android app (package `com.zeul.wallpapers`), connect it to Play Console with a service account (RevenueCat's guide), create entitlement **`premium`** and a current offering with **`$rc_monthly`** + **`$rc_annual`** packages mapped to the Play subscription base plans (trial = an offer on the base plan).
2. Put the RevenueCat **public Android SDK key** (`goog_...`) in `android/local.properties` as `REVENUECAT_KEY=`.
3. Webhook: `npx supabase functions deploy revenuecat-webhook --project-ref <ref> --no-verify-jwt` (done), then run `supabase\set-revenuecat-webhook.ps1` and paste the secret into RevenueCat -> Integrations -> Webhooks.
4. Apply migration 0700. Test with RevenueCat "Send test event" (expects 200) and a Play license-tester purchase.
