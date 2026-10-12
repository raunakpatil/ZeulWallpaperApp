# Security policy

## Reporting a vulnerability

Please **don't open a public issue** for security problems. Instead, use GitHub's
[private vulnerability reporting](https://github.com/raunakpatil/ZeulWallpaperApp/security/advisories/new)
or email **zeulwallpapers@gmail.com** with steps to reproduce.

We'll reply within a few days, keep you updated, and credit you in the fix if you'd like.

## Scope

- The Android app in `android/`
- The database schema, row-level security and RPCs in `supabase/migrations/`
- The edge functions in `supabase/functions/`
- The admin dashboard in `dashboard/`

## How the backend is protected

- Every table has row-level security; the app reads published wallpapers through a view and changes counters only through RPCs.
- Only admins can write content; uploads by users go through the `user-upload` edge function, which checks the account, a daily limit and the files themselves.
- Abuse limits per IP (stored as a salted hash for two days) protect reports, events and downloads.
- The publishable Supabase key in the app is public by design; all secrets live in Supabase function secrets, never in the repo.
