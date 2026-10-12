# Public website (owner: Dev A)

Privacy policy, terms, account-deletion page, `app-ads.txt` and `assetlinks.json`, built from `src/` + `site.config.json`.

- **Live:** https://raunakpatil.github.io/wallpaper-app-site/ (public repo `raunakpatil/wallpaper-app-site`, GitHub Pages)
- **Never** publish to `raunakpatil/raunakpatil.github.io`, which is Raunak's personal portfolio. `build.mjs` refuses to write into any repo it didn't create.

## Update
1. Edit `src/*.html` or `site.config.json` (app name, contact email, AdMob publisher id, Play URL, signing fingerprints).
2. Preview: `node docs/site/build.mjs`, then open `docs/site/dist/`.
3. Publish: `node docs/site/build.mjs --publish`. Pages updates in about a minute.

Empty config values render as highlighted **[placeholders]** so they can't slip through unnoticed.

## Links for Play Console / the app
| Where | URL |
|-------|-----|
| Privacy policy (Play listing + in-app) | https://raunakpatil.github.io/wallpaper-app-site/privacy.html |
| Terms (in-app) | https://raunakpatil.github.io/wallpaper-app-site/terms.html |
| Account deletion URL (Play Data safety form, required) | https://raunakpatil.github.io/wallpaper-app-site/delete-account.html |

## Before launch (A13)
- Fill in `APP_NAME`, `CONTACT_EMAIL`, `PLAY_STORE_URL`, `ADMOB_PUBLISHER_ID` and republish.
- **app-ads.txt: done.** Live at https://raunakpatil.github.io/app-ads.txt (one file, `public/app-ads.txt` in the portfolio repo, added with Raunak's OK on 2026-10-01). On the Play listing, set **Developer website = https://raunakpatil.github.io**. If the publisher id ever changes, edit that one file.
- `SHA256_CERT_FINGERPRINTS`: add the Play App Signing SHA-256 (Play Console → Setup → App signing) for deep links (B11).
- Re-check `privacy.html` against the final app (Firebase, subscriptions) and the Play Data safety answers. I'm not a lawyer; have the text reviewed if you can.
