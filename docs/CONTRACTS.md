# Contracts (v1): shared interfaces between Dev A and Dev B

The app, the dashboard and the backend all code against this file: database tables, RPCs, CDN paths, `app_config` keys and the Kotlin interfaces. **Changing it means changing every side; say so in the PR title (`CONTRACT CHANGE:`).**
Owner: DB/RPC/CDN/config sections → Dev A. Kotlin interfaces (section 5) → joint (B creates them, A implements ads/billing).

---

## 1. Database tables (Supabase Postgres, schema `public`)

```sql
categories      (id uuid pk, name text, slug text unique, icon text, sort_order int)
tags            (id uuid pk, name text unique, slug text unique)
wallpapers (
  id uuid pk default gen_random_uuid(),
  title text not null,
  storage_key text not null,          -- e.g. 'w/7f3a9c2e'  (see section 3)
  original_ext text default 'jpg',    -- 'jpg' | 'png'
  width int, height int, aspect_ratio numeric,
  device_type text not null check (device_type in ('phone','tablet','both')),
  is_amoled bool default false,
  is_curated bool default false, curated_order int,
  is_premium bool default false,
  is_published bool default false, publish_at timestamptz,
  dominant_color text,                -- '#RRGGBB'
  blurhash text, phash text,
  category_id uuid references categories,
  file_size_kb int,
  author_name text, license text, source_url text,
  download_count int default 0, fav_count int default 0, view_count int default 0,
  popularity_score numeric default 0,
  created_at timestamptz default now()
)
wallpaper_tags  (wallpaper_id uuid, tag_id uuid, primary key (wallpaper_id, tag_id))
collections     (id uuid pk, title text, cover_wallpaper_id uuid, is_premium bool, sort_order int)
collection_items(collection_id uuid, wallpaper_id uuid, position int)
profiles        (id uuid pk = auth.users.id, display_name text, role text default 'user', is_premium bool default false, created_at timestamptz)
favorites       (user_id uuid, wallpaper_id uuid, created_at timestamptz, primary key (user_id, wallpaper_id))
unlocks         (user_id uuid, wallpaper_id uuid, unlocked_at timestamptz, primary key (user_id, wallpaper_id))
user_events     (id bigserial, user_id uuid null, device_id text, wallpaper_id uuid, type text check (type in ('view','download','apply','fav','unfav')), created_at timestamptz)
user_tag_affinity (user_id uuid, tag_id uuid, score numeric, primary key (user_id, tag_id))
reports         (id uuid pk, wallpaper_id uuid, user_id uuid null, device_id text, reason text, status text default 'open', created_at timestamptz)
app_config      (key text pk, value jsonb)
```

**RLS:** public can `select` published wallpapers (`is_published and (publish_at is null or publish_at <= now())`), categories, tags, collections and app_config. Users can read/write only their own favorites/unlocks/profile. Only `profiles.role = 'admin'` can write content tables. Counters change only through RPCs.

**v1.1 additions (A2, implemented in `supabase/migrations/`, which is now the authoritative schema):**
- `wallpapers.aspect_ratio` and `wallpapers.color_family` are **generated** columns. Never write them.
- `wallpapers.updated_at`; `profiles.avatar_url`, `profiles.premium_until`; `user_tag_affinity.updated_at`.
- New tables (not read by the app): `wallpaper_daily_stats` (per-day counters behind Popular week/month) and `search_log`.
- Users can update only `display_name` and `avatar_url` on their own profile. A profile row is auto-created on sign-up (name/avatar from Google).

**View for the app:** `wallpapers_public`: published and due wallpapers only. **The app reads this view, not the raw table.** Columns:
`id, title, storage_key, original_ext, width, height, aspect_ratio, device_type, is_amoled, is_curated, curated_order, is_premium, dominant_color, color_family, blurhash, category_id, category_slug, category_name, file_size_kb, author_name, license, source_url, download_count, fav_count, view_count, popularity_score, tags text[], published_at, created_at`

## 2. RPCs (called via `supabase.postgrest.rpc`)

| Function | Args | Returns | Notes |
|----------|------|---------|-------|
| `get_section` | `section text` ('curated','new','amoled','tablet','premium'), `page int`, `page_size int` | setof `wallpapers_public` | curated sorted by `curated_order`. Phone sections exclude `device_type='tablet'`; `tablet` returns tablet + both. Unknown section → error |
| `get_popular` | `range text` ('week','month','all'), `page int`, `page_size int` | setof `wallpapers_public` | week/month score = downloads×3 + applies×2 + net favs×2 in window; excludes tablet-only |
| `search_wallpapers` | `q text`, `color text null`, `device text null`, `amoled bool null`, `category text null`, `page int`, `page_size int` | setof `wallpapers_public` | full-text + trigram (typo-tolerant). **`color` = colour family** (`red, orange, yellow, green, teal, blue, purple, pink, brown, black, white, gray`), `device` = 'phone'/'tablet', `category` = slug. All args optional. Logs page-0 queries |
| `search_suggest` | `q text` | `table(label text, kind text)` kind ∈ tag/category/title | autocomplete, max 8, prefix + fuzzy |
| `get_for_you` | `page int`, `page_size int`, **`seed_tags text[] default null`** | setof `wallpapers_public` | signed-in: tag affinity, excluding already favourited/downloaded/applied. **Guests: pass onboarding tag slugs as `seed_tags`** (store them locally). No signal → popular week |
| `set_style_preferences` | `tag_slugs text[]` | void | **signed-in only.** Call after sign-in with the onboarding picks |
| `record_event` | `wallpaper_id uuid`, `type text`, `device_id text` | void | type ∈ **view, apply, fav, unfav** (NOT download → error). Call on every fav toggle, guest or not. Rate-limited per device (view 30 min, apply 1 min, fav 5 s) |
| `increment_download` | `wallpaper_id uuid`, `device_id text` | int (current count) | counts once per device per wallpaper per hour. Unpublished/unknown id → error P0002 |
| `get_collections` | – | `table(id uuid, title text, is_premium bool, item_count int, cover_storage_key text, cover_blurhash text, cover_dominant_color text)` | **v1.3 (A8).** Only collections with ≥1 published wallpaper, in dashboard order. Cover falls back to the first item |
| `get_collection` | `collection_id uuid`, `page int`, `page_size int` | setof `wallpapers_public` | **v1.3 (A8).** Published items in the collection's order |
| `delete_my_account` | – | void | **signed-in only.** Deletes the auth user; profile, favourites, unlocks and affinity cascade. Then sign out locally |

Pagination is 0-based. Default `page_size` is 30 (max 100).
`device_id`: a random UUID generated once per install and stored in DataStore. Required by `record_event` / `increment_download`.
Favourites for signed-in users: write rows to the `favorites` table directly (RLS allows own rows) **and** call `record_event('fav'|'unfav')`. `fav_count` updates automatically.

## 3. CDN image paths

`{cdn_base_url}/{storage_key}/{variant}`

| variant | File | Size |
|---------|------|------|
| thumb | `thumb.webp` | 400 px wide |
| preview | `preview.webp` | 1080 px wide |
| full | `full.webp` | up to 1440p (free download) |
| original | `original.jpg` or `original.png` (see `original_ext` in `wallpapers_public`) | source, premium only |

`cdn_base_url` comes from `app_config`. **Never hard-code it in the app.** Current value: `https://raunakpatil.github.io/wallpaper-cdn` (GitHub Pages; sends `Access-Control-Allow-Origin: *`).
`storage_key` format: `w/` + 10 lowercase hex chars (e.g. `w/7f3a9c2e1b`). Example thumb URL: `https://raunakpatil.github.io/wallpaper-cdn/w/7f3a9c2e1b/thumb.webp`.
Files are immutable once published (re-uploads get a new key), so the app can cache them aggressively with Coil.

## 4. `app_config` keys

| key | type | default |
|-----|------|---------|
| `cdn_base_url` | string | GitHub Pages URL (set by A5) |
| `min_app_version` | int (versionCode) | 1 |
| `maintenance` | `{enabled: bool, message: string}` | off |
| `ads` | `{native_every: int, app_open_enabled: bool, app_open_min_hours: int}` | 12, false, 4 |
| `unlock_mode` | 'permanent' \| '24h' | '24h' (**v1.9**, 2026-10-06: was 'permanent'; the app falls back to 24h when the key is missing) |
| `free_max_resolution` | int (long edge px) | 2560 |
| `home_sections` | string[] (order of chips) | ["for_you","curated","popular","new","amoled","tablet","premium"] |
| `featured_banner` | `{wallpaper_id or collection_id, title}` \| null | null |
| `daily_wallpaper_id` | uuid \| null | null |
| `paywall` | `{highlight: 'annual' \| 'monthly', headline: string}` | annual |
| `auth_email_enabled` | bool | false. **v1.6 (B7):** shows the email-code sign-in option. Turn on only after custom SMTP (A17) |

## 5. Android interfaces (Kotlin package `com.wallpaperapp.app`; applicationId `com.zeul.wallpapers`, see §9)

Dev B creates these in B1 **with Fake implementations** bound in Hilt. Dev A replaces the bindings with real ones in A9/A10, changing only files under `ads/` and `billing/` plus the one Hilt binding line.

```kotlin
// ads/AdsManager.kt
interface AdsManager {
    suspend fun initialize(activity: Activity)              // UMP consent + MobileAds init
    fun preloadRewarded()
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit                           // ad failed → caller unlocks anyway
    )
    @Composable fun NativeAdSlot(modifier: Modifier)        // grid-tile styled native ad
    fun maybeShowAppOpen(activity: Activity)
    val adsEnabled: StateFlow<Boolean>                      // false for premium users

    // v1.2 (additive, A9): GDPR/UK users must be able to change ad consent later
    val privacyOptionsRequired: StateFlow<Boolean>          // show a "Privacy settings" row when true
    fun showPrivacyOptions(activity: Activity)
}

// billing/PremiumManager.kt
enum class PlanType { MONTHLY, ANNUAL }
data class Plan(val type: PlanType, val price: String, val trialDays: Int?, val pricePerMonth: String)
sealed interface PurchaseResult { object Success : PurchaseResult; object Cancelled : PurchaseResult; data class Error(val msg: String) : PurchaseResult }

interface PremiumManager {
    val isPremium: StateFlow<Boolean>
    suspend fun initialize(userId: String?)                 // call again on login/logout
    suspend fun getPlans(): List<Plan>
    suspend fun purchase(activity: Activity, plan: PlanType): PurchaseResult
    suspend fun restore(): Boolean
    fun manageSubscriptionIntent(): Intent
}
```

**AdsManager behaviour (real impl: `ads/AdMobAdsManager`, A9):**
- `showRewarded`: premium → `onRewarded` immediately. Ad not ready → waits up to 6 s, then `onUnavailable` (unlock anyway). User closes the ad **before** earning → **neither** callback fires, so keep the unlock sheet open.
- `NativeAdSlot`: renders nothing for premium users, so don't reserve grid cells when `adsEnabled` is false. Give it at least ~160dp × 240dp (the media view needs ≥120dp). Shows a "Sponsored" placeholder while loading; after 2 failed loads with nothing cached it renders **nothing** (collapses) and fills in if an ad arrives later. Place one every `app_config.ads.native_every` tiles.
- Known: Google's native **test** ads fail on the Android Studio emulator (`Incorrect native ad response. Click actions were not properly specified` → code 0 / "Invalid template ID"). Verify native ads on a real phone.
- `maybeShowAppOpen(activity)`: call from `MainActivity.onStart()`. The manager decides (remote toggle, spacing, never in the first hours after install, 4 h ad expiry).
- Debug builds always use Google **test** ad units. Real unit ids come from local.properties (`ADMOB_REWARDED_ID`, `ADMOB_NATIVE_ID`, `ADMOB_APP_OPEN_ID`) and are used by release builds only.

**PremiumManager behaviour (real impl: `billing/RevenueCatPremiumManager`, A10):**
- Binding (`di/MonetizationModule`): REVENUECAT_KEY set → RevenueCat. No key → `FakePremiumManager` in **debug only**; release gets `UnavailablePremiumManager` (nobody premium, no plans, purchase returns an error), so a release build can never grant premium for free.
- RevenueCat setup: entitlement `premium`; current offering with `$rc_monthly` and `$rc_annual`; free trials are offers on the Play base plans (reported per plan in `Plan.trialDays`).
- **App user id = Supabase user id** when signed in. B7 must call `premiumManager.initialize(userId)` after sign-in and `initialize(null)` after sign-out (it does RevenueCat logIn/logOut). Guests buy anonymously; Restore recovers it.
- Server mirror: `profiles.is_premium` / `premium_until` are kept in sync by the `revenuecat-webhook` Edge Function (A11) for signed-in buyers, with an hourly expiry job. The app's own `isPremium` comes from RevenueCat directly (works offline and for guests).

**Accounts (B7, implemented by Dev A in `data/account/`, v1.6):**
- `AuthRepository`: `state: StateFlow<AuthState>` (Loading, SignedOut, SignedIn(AccountUser(id, email, name, avatarUrl)), Unavailable in fake-backend mode), `signInWithGoogle(activity)`, `sendEmailCode(email)`, `verifyEmailCode(email, code)`, `signOut()`, `deleteAccount()`; results are `AuthResult` Success / Cancelled / Error(message). Inject it anywhere you need the signed-in user.
- Google: Credential Manager `GetSignInWithGoogleOption` with `GOOGLE_WEB_CLIENT_ID` (local.properties) and a nonce (SHA-256 to Google, raw to Supabase). Each developer machine and the Play signing key need their own Android OAuth client (package + SHA-1) in Google Cloud project `habit-smasher-fc823`.
- `AccountSync` (started in `MainActivity`): owns `premiumManager.initialize(userId)` (do NOT call it elsewhere); on sign-in merges favourites + unlocks both ways and sends style picks (`set_style_preferences`) whenever they change while signed in; `FavoritesRepository.toggle` and `UnlockRepository.unlock` write through automatically. Sign-out removes the account's synced copies from the phone (restored on next sign-in); delete account removes all local favourites/unlocks.
- UI: `SignInRoute` → `ui/account/SignInScreen` (closes itself when signed in). Profile shows Sign in / account row / Sign out / **Delete account** (label referenced by the privacy policy and Play listing; keep it).

## 6. Naming and conventions
- Branches: `a/<ID>-slug`, `b/<ID>-slug`. Commits: `B3: add popular tabs`.
- Migrations: `supabase/migrations/<timestamp>_<name>.sql`. Only Dev A writes them.
- Secrets: Android → `local.properties` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `ADMOB_APP_ID`, `REVENUECAT_KEY`), exposed via `BuildConfig`. Dashboard → `.env` (`VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`). Share them privately, never in git. Commit a `.example` file.

## 7. Share links (v1.4, B11)

Shared wallpapers use one URL that both the app and the public site understand:

`https://raunakpatil.github.io/wallpaper-app-site/w/?id={wallpaper_uuid}`

- **App (Dev B):** `MainActivity` handles this URL (App Link, `autoVerify`) and also `zeul://wallpaper/{wallpaper_uuid}`, then opens the preview. Unknown or unpublished ids show "This wallpaper isn't available".
- **Site (Dev A):** `docs/site` serves `w/index.html`: a small page that reads `id`, shows the app name with an "Open in app" button (`zeul://wallpaper/{id}`) and a Google Play link. It doesn't need to show the image. `assetlinks.json` needs the release SHA-256 (A13) for links to open the app without a chooser; until then Android opens the site page, whose button opens the app.
- If the site moves to a custom domain, change it here first (the app has the host in its manifest).

## 8. Push notifications (v1.5, A16 sends / B10 receives)

**Transport:** Firebase Cloud Messaging, **topic** messages, **data-only** (no `notification` block) with `android.priority = high`. The app builds and shows the notification itself, so it looks the same in the foreground and background and can load the image with Coil.

**Topics:** `{env}_{name}`, where `env` = `prod` for release builds and `debug` for debug builds. The dashboard's "Test" mode sends to `debug_*` only, so test pushes never reach real users.

| name | who subscribes | Android channel id (user can mute it) |
|------|----------------|------------------------|
| `all` | every install, always (rare announcements) | `announcements` |
| `new_drops` | Settings → "New wallpapers" (default **on**) | `new_drops` |
| `daily_wallpaper` | Settings → "Daily wallpaper" (default **on**) | `daily` |

**Data payload** (all values are strings; empty string = absent):

| key | meaning |
|-----|---------|
| `type` | `new_drop` \| `daily` \| `collection` \| `announcement` → pick the channel (`collection` uses `new_drops`) |
| `title`, `body` | notification text (title ≤ 65 chars, body ≤ 240) |
| `image_url` | optional full https URL (a `preview.webp`) → show as BigPicture |
| `link` | what to open on tap: `zeul://wallpaper/{id}`, `zeul://collection/{id}`, `zeul://section/{name}` (`name` as in `home_sections`), or empty → Home |
| `wallpaper_id`, `collection_id` | optional, same ids as in `link` |
| `push_id` | uuid of the dashboard's send log row |

**On tap** the app opens `link` (unknown or unpublished ids → "This wallpaper isn't available", like share links), then calls `record_push_open(push_id, device_id)` once. That is a new RPC; it's anon-callable, deduplicated per device, and feeds the "opens" count in the dashboard.

**Permission:** on Android 13+, ask for `POST_NOTIFICATIONS` at a natural moment (e.g. after the first download or apply), never on first launch. Subscribe to topics regardless; Android just won't display anything until the permission is granted.

**Rules on our side (A16):** at most 6 prod manual sends per 24 h, plus at most one daily push per day (automatic, at the hour set in the dashboard, using `daily_wallpaper_id`).

## 9. App identity (v1.6, 2026-10-02)

- **App name: Zeul.** Launcher label, store listing, website, notifications.
- **applicationId: `com.zeul.wallpapers`.** Permanent after the first Play upload. Only `applicationId` changes; the Kotlin `namespace` and source packages stay `com.wallpaperapp.app` (no file moves needed).
- **Deep-link scheme: `zeul://`** (`zeul://wallpaper/{id}`, `zeul://collection/{id}`, `zeul://section/{name}`). Nothing has shipped, so this replaces `wallpaperapp://` everywhere; the app may keep accepting the old scheme during development but must not emit it.
- Things registered per applicationId (Raunak redoes them for the new id): Firebase Android app (new `google-services.json`), Google OAuth Android clients (one per debug SHA-1, plus the Play signing SHA-1 at launch), RevenueCat app, `assetlinks.json`.

## 10. Hindi category names (v1.7, retired in v1.8 on 2026-10-05)

**Retired.** Raunak and Ghanshyam dropped Hindi: the app is English-only and never reads `categories.name_hi`. The column is unused; Dev A may drop it (and the dashboard field) whenever convenient. Nothing in the app depends on it either way.

## 11. User uploads (v1.10, 2026-10-11)

Signed-in users publish wallpapers from the app; they go live straight away (Raunak's call), credited to the uploader.

- **Schema (migration 1300):** `wallpapers.uploader_id uuid null` (null = admin upload); `profiles.upload_banned bool` (admin-only, via `admin_set_upload_banned(target, banned)`).
- **`app_config.user_uploads`:** `{enabled: bool, daily_limit: int, hide_after_reports: int}`, default `{true, 5, 3}`.
- **Edge function `user-upload`** (user JWT, not anonymous): `GET` → `{enabled, daily_limit, used_today, remaining}`; `POST` multipart `thumb`/`preview`/`full` (webp) + optional `original` (jpg) + fields `title, category_id, tags, device_type, width, height, blurhash, dominant_color, is_amoled, rights=yes` → `201 {id, storage_key, live_at}`; `DELETE ?id=` removes the caller's own upload. Errors are `{error}` with a message ready to show. Min size 720×1280. `publish_at` = now + 90 s (GitHub Pages delay).
- **RPC `my_uploads()`** (authenticated): the caller's uploads incl. hidden ones: `id, title, storage_key, width, height, blurhash, dominant_color, is_live, download_count, fav_count, created_at`.
- **Auto-hide:** a report trigger unpublishes a user upload once `hide_after_reports` different reporters have open reports on it.
- `author_name` = the uploader's display name, `license` = 'User upload'. The app view `wallpapers_public` is unchanged.

