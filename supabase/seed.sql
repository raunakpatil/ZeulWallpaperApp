-- Starter categories. Safe to re-run. Wallpapers themselves are added via the dashboard (A6/A7).
insert into public.categories (name, icon, sort_order) values
  ('Minimal',      'minimize',      10),
  ('Nature',       'forest',        20),
  ('Space',        'rocket',        30),
  ('Abstract',     'blur_on',       40),
  ('Dark',         'dark_mode',     50),
  ('Gradient',     'gradient',      60),
  ('Architecture', 'apartment',     70),
  ('Cityscape',    'location_city', 80),
  ('Cars',         'directions_car',90),
  ('Animals',      'pets',         100),
  ('Art',          'palette',      110),
  ('Patterns',     'grid_on',      120)
on conflict (name) do nothing;
