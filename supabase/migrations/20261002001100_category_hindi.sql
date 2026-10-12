-- =====================================================================
-- Hindi category names (Dev B request 2026-10-02, after B13 added Hindi to the app)
-- categories.name_hi: optional; the app shows it when the UI language is Hindi, else `name`.
-- Readable through the existing "public read" policy on categories. Safe to re-run.
-- Drafted by Claude; have a native speaker review before launch.
-- =====================================================================

alter table public.categories add column if not exists name_hi text;
alter table public.categories drop constraint if exists categories_name_hi_len;
alter table public.categories add constraint categories_name_hi_len
  check (name_hi is null or char_length(btrim(name_hi)) between 1 and 40);

update public.categories c set name_hi = v.hi
from (values
  ('minimal',      'मिनिमल'),
  ('nature',       'प्रकृति'),
  ('space',        'अंतरिक्ष'),
  ('abstract',     'एब्स्ट्रैक्ट'),
  ('dark',         'डार्क'),
  ('gradient',     'ग्रेडिएंट'),
  ('architecture', 'वास्तुकला'),
  ('cityscape',    'शहर'),
  ('cars',         'कारें'),
  ('animals',      'जानवर'),
  ('art',          'कला'),
  ('patterns',     'पैटर्न')
) as v(slug, hi)
where c.slug = v.slug and c.name_hi is null;
