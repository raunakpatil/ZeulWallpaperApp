// Tips shown on the Home page (rotating) and field explanations reused across forms.

export const HOME_TIPS: string[] = [
  'Upload in batches of 10–20: drop them all at once, set category, author and licence once in "Defaults", then fix titles and tags.',
  'Good tags are what users type: colours (blue, neon), moods (calm, dark), subjects (mountains, car, galaxy). 4–8 tags per wallpaper is plenty.',
  'Check Insights → "Searches with no results" every week. It\'s a free list of wallpapers people want and you don\'t have yet.',
  'Keep Curated small and excellent (20–40). It\'s the first thing new users see.',
  'AMOLED wallpapers need mostly pure black (#000000). The uploader tells you the "% true black"; aim for 40% or more.',
  'Landscape images are marked Tablet automatically and are hidden on phones. Portrait images are marked Both, so everyone sees them.',
  'Schedule uploads to publish over several days. A steady stream of "New" wallpapers brings users back.',
  'Set a Daily wallpaper in App settings. It shows as the daily card on Home.',
  'Always record the licence and author. If a copyright complaint comes in, you can answer in minutes.',
  'Premium wallpapers are visible to everyone but only premium users can download them for free. Use them for your very best work.',
  'Collections ("Neon Tokyo", "Minimal Mountains") make browsing fun. 8–15 wallpapers per pack works well.',
  'Your storage meter (bottom left) shows GitHub Pages usage. Plan the move to Cloudflare R2 before it reaches ~80%.',
];

export const FIELD_TIPS = {
  title: 'Shown under the wallpaper in the app and used in search. Short and descriptive: "Neon Tokyo Street".',
  category: 'The main group shown in the app\'s Explore tab. Pick one; use tags for everything else.',
  tags: 'Keywords users search for: colours, moods, subjects, styles. Press Enter or comma after each. New tags are created automatically.',
  device: 'Phone = very tall (9:16, 9:20). Both = any other portrait or square image (phones crop the sides, tablets show more). Tablet = landscape only; these are hidden from phones and appear only in the Tablet section.',
  amoled: 'Mostly pure-black background. Saves battery on OLED screens and appears in the AMOLED section.',
  curated: 'Hand-picked best wallpapers. They appear in the Curated section in the order you set on the "Curated order" page.',
  premium: 'Everyone can see it, but only premium subscribers can download it for free (subscriptions launch later).',
  status: 'Draft = hidden from the app. Publish now = live immediately. Schedule = goes live automatically at the chosen time.',
  license: 'Required. Who owns the rights and on what terms. Only upload wallpapers you are allowed to redistribute commercially.',
  author: 'The artist or photographer, credited in the app. Leave empty for your own work.',
  source: 'Where the original came from (artist page, commission invoice, permission email). Only admins see it; useful if a complaint arrives.',
};
