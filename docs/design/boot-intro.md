# Boot intro (Raunak's request, 2026-10-02) · owner: Dev B

When Zeul opens from a cold start, the hamster intro video plays full-screen, then the app opens with a seamless transition. The goal is that it feels like **one continuous motion** from tapping the icon to seeing Home: no black frames, no jumps in colour.

## Assets (in `brand/intro/`, copy into the app)

| File | Copy to | What |
|---|---|---|
| `boot_video.mp4` | `res/raw/boot_video.mp4` | 4.0 s, 1080×1920, 24 fps, H.264 Main + AAC, faststart, audio fades out in the last 0.3 s (816 KB) |
| `boot_video_first.webp` | `res/drawable-nodpi/boot_video_first.webp` | exact first frame (hamster face close-up on orange) |
| `boot_video_last.webp` | `res/drawable-nodpi/boot_video_last.webp` | exact last frame (hamster standing on orange) |

Video colours: background **`#F2550C`** at the top, warming to **`#F46C16`** at the floor. (The launcher icon's orange is `#FF4A01`; use the video's colours for everything intro-related.)

## The sequence

1. **System splash (Android 12+ SplashScreen API, `core-splashscreen` for 8–11):** `windowSplashScreenBackground = #F2550C`, icon = the launcher icon (already the hamster). `postSplashScreenTheme` = the app theme. Keep the splash on screen (`setKeepOnScreenCondition`) only until the intro's first frame is ready, at most ~500 ms.
2. **Handoff splash → intro:** underneath the splash, the intro screen already shows `boot_video_first.webp` full-screen (same framing as frame 0). Exit animation: fade the splash out over ~250 ms (`setOnExitAnimationListener`). Because the poster equals the video's first frame, the switch to the playing video is invisible.
3. **Intro (4 s):** full-screen video, edge-to-edge (drawn behind transparent status and navigation bars, with **light** system bar icons over the orange). Scale: **crop to fill** on phones (9:19.5 crops a little off the sides; the hamster is centred). On wide screens (tablets, landscape, foldables) **fit** the video and fill the bars with a vertical gradient `#F2550C → #F46C16` so the bars blend in. Use a `TextureView`-backed player (Media3 ExoPlayer) so the layer can be faded/scaled; hide the poster when the first frame renders (`onRenderedFirstFrame`).
4. **Transition intro → app (~450 ms, starts at video end or on skip):** the intro layer fades out while scaling up slightly (1.0 → 1.06, ease-out), revealing Home underneath, which fades in from 0.96 → 1.0 scale. When the video ends, show `boot_video_last.webp` frozen under the player so a late/dropped final frame can't flash. Navigation underneath is already on Home: **start loading Home's data at app start**, behind the intro, so Home is populated when it appears.

## Rules

- **Plays on every cold start**, not when returning to the app from the background, not on configuration changes (rotation), not after process-death restore in the middle of a session.
- **Tap anywhere to skip** → jumps straight to step 4. Show no visible Skip button; a single tap is enough.
- **Skipped entirely** (app opens directly) when:
  - launched from a **deep link / share link / notification tap** (the user wants that content now);
  - Settings → **"Startup animation"** is off (new switch in Profile → Settings, default **on**);
  - the system "**Remove animations**" accessibility setting is on (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`);
  - the video fails to load or prepare (fall back silently to the normal start).
- **Sound (Raunak chose: with sound):** play the audio only if the ringer mode is **normal** (not silent/vibrate) and media volume > 0; otherwise play muted. Request **transient, may-duck** audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, usage `USAGE_MEDIA`, content `CONTENT_TYPE_MOVIE`) so other music lowers briefly instead of stopping, and abandon focus when the intro ends or is skipped. Never play sound if another app holds exclusive focus (call in progress).
- **Performance:** start preparing the player as early as possible (in `onCreate`), release it immediately after the transition. The intro must not delay Home's data loading or the first frame of Home after the transition. No intro on the first frame of a **baseline-profile** run (keep the profile focused on Home).
- **Accessibility:** the intro is decorative: `importantForAccessibility = no`; TalkBack users get it skipped automatically (`AccessibilityManager.isTouchExplorationEnabled` → skip).
- **Strings:** the Settings switch label + description go in `values/` and `values-hi/` ("Startup animation" / "स्टार्टअप एनिमेशन", "Play the Zeul hamster when the app opens").

## Done when

- Cold start on a phone: icon tap → orange splash with hamster → fades into the video with no black frame → hamster zooms out and waves → Home fades in, already loaded.
- Second launch from recents/background: no intro.
- Notification tap / share link: no intro, opens the content.
- Silent mode: video plays muted. Music playing in another app: it ducks for 4 s, then returns.
- Tablet landscape: video fitted with blended orange bars.
- Startup animation switch off: app opens straight to Home.
