# Contributing to Zeul

Thanks for helping. Zeul is a community app, and there are lots of ways in:

- **Wallpapers:** upload your own from the app (Home → upload button). No code needed.
- **Bugs and ideas:** open an [issue](https://github.com/raunakpatil/zeul/issues/new/choose).
- **Code, design, docs:** open a pull request (below).
- **Questions and show-and-tell:** [Discussions](https://github.com/raunakpatil/zeul/discussions).

## Getting set up

Follow [Build it yourself](README.md#build-it-yourself). For app work, `USE_FAKE_BACKEND=true` in
`android/local.properties` gives you sample data with no accounts or keys.

## Making a change

1. **Find or open an issue** first for anything bigger than a small fix, so we can agree on the approach.
2. **Fork and branch** from `main` (`fix/…`, `feat/…`).
3. **Keep the contract.** Anything the app, dashboard and backend share (tables, RPCs, image paths, `app_config` keys, the Kotlin interfaces) is described in [`docs/CONTRACTS.md`](docs/CONTRACTS.md). If your change touches it, update the file in the same PR and start the PR title with `CONTRACT CHANGE:`.
4. **Database changes** go in a new file in `supabase/migrations/` (never edit an applied one). Keep them re-runnable where you can (`if not exists`, `create or replace`).
5. **Match the style around you.** For UI, use the soft kit (`ui/theme/Soft.kt`, `SoftControls.kt`), the `ZeulMotion` springs and `ZeulRadius` corners rather than raw Material components; see [`docs/design/app-ui-soft.md`](docs/design/app-ui-soft.md). Put user-facing text in `res/values/strings.xml`.
6. **Check it before you push:**
   ```bash
   cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug
   cd dashboard && npm run lint && npm run build
   ```
   For UI changes, run the app and add a screenshot (light and dark) to the PR.
7. **Open the PR** and fill in the template. CI must pass.

## Things we won't merge

- Secrets of any kind (keys, tokens, `google-services.json`, keystores). The `.gitignore` covers the usual ones; double-check your diff.
- Copyrighted wallpapers or assets you don't have the rights to.
- New tracking, analytics or permissions without a clear reason in the PR.
- Large unrelated refactors mixed into a feature or fix.

## Licence

By contributing you agree that your contribution is licensed under the [GPL-3.0](LICENSE), like the rest of the code.
