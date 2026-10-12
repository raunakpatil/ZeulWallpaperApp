-- =====================================================================
-- Tag hygiene (Dev B request 2026-10-02)
-- Uploads named like "…rising from back….png" produced tags with a trailing ellipsis
-- ("back…", "ho…", "moonlit…"). The dashboard now cleans tags (lib/api.ts cleanTag) and
-- drops cut-off words from filenames; this makes the database enforce the same rule and
-- repairs the existing rows. Safe to re-run.
-- =====================================================================

-- Tags: lower-case letters/digits/spaces/hyphens only, max 30 chars, at least 2.
create or replace function private.normalize_tag()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.name := left(btrim(regexp_replace(regexp_replace(lower(coalesce(new.name, '')),
                '[^[:alnum:] -]+', ' ', 'g'), '\s+', ' ', 'g')), 30);
  new.name := btrim(new.name);
  if char_length(new.name) < 2 then
    raise exception 'tag must have at least 2 letters or digits' using errcode = '22023';
  end if;
  new.slug := coalesce(nullif(new.slug, ''), nullif(public.slugify(new.name), ''), left(md5(new.name), 10));
  return new;
end;
$$;
revoke all on function private.normalize_tag() from public, anon, authenticated;

-- Repair: "moonlit" is a whole word, keep it; "ho" and "back" were fragments, remove them
-- (deleting a tag also removes it from its wallpapers; search text refreshes via triggers).
update public.tags set name = 'moonlit', slug = 'moonlit'
 where name = 'moonlit…' and not exists (select 1 from public.tags where name = 'moonlit');
delete from public.tags where name in ('ho…', 'back…', 'moonlit…');

-- Anything else that doesn't match the rule gets re-normalised by the trigger.
update public.tags set name = name where name !~ '^[a-z0-9 -]{2,30}$';
