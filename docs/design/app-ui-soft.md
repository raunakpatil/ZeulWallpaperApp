# Zeul app UI: soft design system + motion (Dev A, 2026-10-02/03, branch `a/app-ui`, merged to main)

Raunak's direction: **light mode = soft neumorphic grey** (raised/pressed-in surfaces, monochrome, orange only as a small accent); **dark mode = deep black with charcoal cards and bold Zeul orange** for primary actions and the active tab. Same shapes and layout in both. Plus a premium motion layer and the boot intro.

**When you add or restyle a screen, build it from the pieces below** so it matches. Don't use Material You dynamic colour (removed on purpose), stock `NavigationBar`, or ripple-only feedback on soft surfaces.

## Files

| File | What |
|---|---|
| `ui/theme/Theme.kt` | `WallpaperTheme` (no `dynamicColor` param any more): brand colour schemes + provides `LocalSoft`. `ZeulOrange` / `ZeulOrangeDark`, `ZeulDisplay` font family. |
| `ui/theme/Soft.kt` | Tokens `SoftColors` (`SoftLight`, `SoftDark`, read with `LocalSoft.current`) and the kit: `Modifier.softRaised(shape, depth, fill, pressed)`, `Modifier.softInset(shape, depth, fill)`, `SoftIconButton`, `SoftSearchBar`, `SoftPillButton`, `SoftScreenTitle(text, trailing)`, `SoftGroup { }` (settings card; ListItems inside get a transparent background automatically). |
| `ui/theme/Motion.kt` | `ZeulMotion` springs/durations, `rememberSoftPress()` + `Modifier.pressScale(press)` + `Modifier.softClickable(press, …)`, `Modifier.softShimmer()`, `Modifier.riseIn(index, animate)`, `Modifier.popOnActivate(active)`. |
| `ui/theme/ZeulIcons.kt` | Thin-line icon set (Home, Grid, Search, Heart/HeartFilled, Person, Download, Gem). Use these, not `Icons.Filled.*`, for UI chrome. |
| `ui/theme/CategoryIcons.kt` | Line icons per category; `CategoryIcons.forCategory(category)` (slug → dashboard icon name → `Picture` fallback). |
| `ui/components/SoftNavBar.kt` | Floating pill tab bar, gliding indicator, haptic tick. 5 tabs: Home, Explore, **Search (new top-level)**, Favourites, Profile. |
| `ui/components/SharedTransitions.kt` | `LocalSharedScope`, `LocalNavScope`, `AnimatedContentScope.WithNavScope { }`, `Modifier.sharedWallpaper(id, enabled, corner)` (hero transition). |
| `ui/components/TileActions.kt` | `TileActionsViewModel` (favourite ids + toggle) used by every grid card. |
| `ui/components/WallpaperTile.kt` | `WallpaperTile` (image only) + **`WallpaperCard`** (soft frame + heart/download pills, hero source) + shimmering `WallpaperTileSkeleton`. |
| `ui/intro/BootIntro.kt`, `WaveClip.kt`, `IntroHandoff.kt` | Boot intro (spec `boot-intro.md`) + Looney Tunes iris-out + the diamond-button encore. `IntroPolicy` decides if it plays. |
| `res/font/montserrat_*.ttf` | Display face (SIL OFL, licence in `brand/fonts/`), Latin subset ~44 KB each. Titles only; body text stays system. |

## Rules of the style

- **Shadows are hand-drawn** (stacked fading copies of the shape, `softShadow`/`softInnerShadow` in Soft.kt). Don't switch to Compose `dropShadow`/`innerShadow` or `Paint` blur: on some phones/GPUs the blur isn't rendered and you get hard-edged ghost copies (Raunak saw this on the tab bar). Dark mode draws no up-left highlight.
- **Surfaces:** cards/chips/buttons = `softRaised` (radius 16–24 dp); fields and selected states = `softInset`. In light mode the fill equals the background and depth comes from the paired shadows; in dark mode the fill is `soft.raised` (#1C1C1E) on #0D0D0E.
- **Titles:** every top-level screen starts with `SoftScreenTitle(...)` (Montserrat Light 40 sp; same position as Home's "ZEUL"). Section labels: uppercase `labelLarge`, `soft.textMuted`, letterSpacing 1.2 sp.
- **Colour:** text `soft.text` / `soft.textMuted`; accent `soft.accent` sparingly in light (active tab, toggled heart, selected chip label), freely in dark (filled buttons, active tab circle). Category icons: charcoal in light, orange in dark.
- **Spacing:** screen padding 20 dp horizontal; 14–22 dp between groups; grid gutters 18 dp, row gap 22 dp.
- **Feedback:** soft surfaces use `rememberSoftPress()` + `pressScale` + `softRaised(pressed = press.progress)` + `softClickable` (no ripple). Haptics: `SegmentTick` for tab/chip switches, `ToggleOn/Off` for hearts, `ContextClick` for buttons.
- **Motion:** use `ZeulMotion` specs only (no hand-picked durations). Lists/grids: `riseIn` for the first screenful only. Navigation transitions are set once on the NavHost (`zeulEnter`/`zeulExit` in `WallpaperAppRoot.kt`): tab↔tab fade-through, detail slide, preview fade (+ hero).
- **New destinations:** wrap content in `WithNavScope { }` inside `composable<…> { }` so shared elements work. A new grid of wallpapers should use `WallpaperGrid` (cards, hearts, hero and stagger come for free).
- **Reduced motion:** Compose scales all of it by the system animator setting; the boot intro is skipped when it's 0.

## Behaviour changes to know about

- `TopLevelDestination` gained `SEARCH(SearchRoute())`; Search is a tab now (Home's search bar opens it too). `ExploreScreen` lost `onColor` (colour row removed; colour stays a Search filter). `HomeScreen` takes `onSearch` and `onPremium`.
- Grid cards: heart toggles the favourite directly (same `FavoritesRepository.toggle` as the preview); download opens the preview (unlocks/Download/Apply live there).
- Preview: the current page is the hero target; a THUMB `AsyncImage` sits under the PREVIEW one so the hero stays sharp while the larger image loads.
- `MainActivity`: launch theme `Theme.WallpaperApp.Launch` (manifest) → `setTheme(Theme.WallpaperApp)`; Android 12+ splash fades out; intro overlay on cold start; **`adsManager.maybeShowAppOpen` is skipped while the intro plays and called when it ends**.
- Profile: settings in `SoftGroup` cards; new "Startup animation" switch (`IntroPolicy`, SharedPreferences `zeul_intro`); plan label "Plan: Freemium".
- Strings added (EN + HI): `nav_search`, `home_search_hint`, `app_wordmark` (non-translatable), `startup_animation`, `startup_animation_body`; `plan_free` text changed.

**Dark depth (2026-10-04):** dark surfaces use `softRaised`/`softInset` like light ones; they add a black shadow, faint up-left glow, top-lit sheen and a light rim automatically. Don't use flat `background(raised)` cards in dark.

## Controls kit (`ui/theme/SoftControls.kt`)

Use these instead of Material `Button`/`TextButton`/`FilterChip`/`OutlinedTextField`/`TopAppBar`:

- `AccentButton(text, onClick, enabled, loading, leading)`: the orange main action (Apply, Continue, trial CTA). One per screen.
- `SoftButton` (raised secondary), `SoftTextButton` (plain link-style), `SoftIconButton` (in `Soft.kt`).
- Over a wallpaper: `GlassIconButton` and `Modifier.glassPanel(radius)` (dark frosted fill + thin light edge), as in Preview and Apply.
- `SoftChip(label, selected, onClick, trailing = ZeulIcons.ChevronDown)` for filters/tags; `SoftSegmented(options, selectedIndex, onSelect)` for 2-4 choices.
- `SoftTextField(value, onValueChange, placeholder, leading, trailing, fieldModifier)`: pass `focusRequester`/`onFocusChanged` via `fieldModifier`.
- `SoftTopBar(onBack, backLabel, title)` for detail screens (add `statusBarsPadding()` if the screen is edge to edge); `SoftSectionLabel` for small uppercase headers.
- Bottom sheets: `containerColor = softSheetColor()`.

Every screen is in the soft style now (2026-10-04). Notification small icon: hamster silhouette, `res/drawable/ic_notification.xml`.

## Photo-led layer (B14, 2026-10-05, Ghanshyam's reference)

Same black + orange theme and soft kit; the screens lead with photos. New pieces live in `ui/components/PhotoCards.kt`:

- `ImageCard(imageUrl, title, onClick, subtitle, label, labelIcon, blurhash, dominantColor)`: big rounded (28dp) photo card, small frosted pill top-left (`label`, e.g. "12 wallpapers"), frosted ↗ button top-right, two-line title bottom-left over a dark fade ("Nature / Wallpapers"). The caller sizes it.
- `PhotoPill` (frosted text pill over photos), `OutlinePill` (tags; `onDark = true` over photos), `IconTab` (icon + label; selected = orange fill), `RoundSoftButton` (round header button, e.g. search).
- New icons in `ZeulIcons`: `ArrowUpRight`, `ArrowRight`, `Flame`, `Star`, `Tablet`, `Moon`.

Screens:
- **Home:** title + round search + gem; section tabs are `IconTab`s; banners are full-width `ImageCard`s; grid cards are plain photo cards (24dp corners, ratio 0.7) with a frosted heart in the corner (download lives in Preview).
- **Explore:** title + round search; collections are `ImageCard`s with the count pill; categories are a full-width hero card then a 2-column grid of photo cards. Cover = first `search_wallpapers` result for the category (`WallpaperRepository.categoryCover`).
- **Preview:** back · centre mode pill (PREVIEW / LOCK SCREEN / HOME SCREEN, tap cycles) · share · heart. Bottom card (always the dark soft palette): title + downloads, BY AUTHOR · CATEGORY, tag pills, colour palette (androidx.palette on the thumbnail) + resolution + info, orange "Set Wallpaper" pill + round download.
- **Onboarding:** page 0 is a full-bleed photo landing (popular wallpapers crossfading, outlined pill, big uppercase headline, corner captions, full-width orange CTA), then 2 hamster pages and the style picker.
- **Search tab:** no back arrow and no keyboard when opened from the tab bar; the header search buttons open it focused (`SearchRoute(focus = true)`).

## Verified

Built and checked on the Android 16 emulator in light + dark (all screens incl. the 2026-10-04 restyles, tab glide, fade-through, hero open/close, stagger, intro + iris + encore, warm start = no intro, link cold start = no intro). `testDebugUnitTest` 98/98, `compileDebugAndroidTestKotlin` OK, `lintDebug` clean. **Not checked on a real phone:** haptics, press feel, intro sound/ducking, tablet layout of the soft UI.
