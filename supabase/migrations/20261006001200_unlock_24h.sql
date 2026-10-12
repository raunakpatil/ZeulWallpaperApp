-- Decided 2026-10-06 (Raunak): a rewarded-ad unlock lasts 24 hours, not forever.
-- Still switchable in the dashboard (Settings > Unlocks & downloads).
update public.app_config set value = '"24h"'::jsonb where key = 'unlock_mode';
