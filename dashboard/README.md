# Dev dashboard (owner: Dev A)

React + Vite + TypeScript + Mantine. Talks to Supabase directly, where admin RLS policies allow writes. Uploads go through the `upload-wallpaper` Edge Function.

## Run
```bash
cp .env.example .env      # fill in the project URL + publishable key
npm install
npm run dev               # http://localhost:5173
```
Use **Chrome or Edge**: WebP encoding happens in the browser.

## First login
1. **Create account** on the login screen and confirm the email.
2. In the Supabase SQL Editor, make yourself admin (the dashboard shows the exact SQL):
   ```sql
   update public.profiles set role = 'admin' where id = (select id from auth.users where email = 'you@example.com');
   ```

## Pages
| Page | What it does |
|------|--------------|
| **Wallpapers** | Search and filter (status, category, curated/AMOLED/premium/tablet), sort by downloads/favs. Click a row to edit metadata and tags, publish/schedule/unpublish, or delete (DB + CDN files) |
| **Upload** | Drag and drop many images. For each file the browser makes thumb (400 px) / preview (1080 px) / full (≤ 2560 px) WebP, blurhash, dominant colour, AMOLED %, phone/tablet guess, duplicate warning (dHash), and title/tags from the filename. Review, then upload one by one or all |
| **Curated order** | Drag to reorder the Curated section, or remove items from it |
| **Categories** | Add, rename, reorder, delete; set the icon name |
| Sidebar meter | CDN (GitHub Pages) storage used vs the ~1 GB budget |

## Deploy (optional, free)
Cloudflare Pages / Netlify / Vercel: build command `npm run build`, output `dist`, and set the two `VITE_` env vars. Add the deployed URL under Supabase → Authentication → URL Configuration → Redirect URLs.
