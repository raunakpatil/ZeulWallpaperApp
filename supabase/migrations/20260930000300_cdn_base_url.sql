-- A5 · Point the app at the GitHub Pages CDN (only if it hasn't been set yet).
-- When images move to Cloudflare R2 (A14), change this row from the dashboard. No app update needed.
update public.app_config
   set value = '"https://raunakpatil.github.io/wallpaper-cdn"'
 where key = 'cdn_base_url'
   and value = '""'::jsonb;
