# Adds the two GitHub Actions secrets the Supabase keep-alive workflow needs (P3).
# Reads the values from android/local.properties, so nothing is typed or shown on screen.
# Usage (from any folder):  powershell -ExecutionPolicy Bypass -File supabase\set-keepalive-secrets.ps1
$ErrorActionPreference = 'Stop'
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
$repoRoot = Split-Path -Parent $PSScriptRoot   # works from any folder
$props = Get-Content (Join-Path $repoRoot 'android\local.properties') | Where-Object { $_ -match '^(SUPABASE_URL|SUPABASE_ANON_KEY)=' }
$map = @{}; foreach ($l in $props) { $k, $v = $l -split '=', 2; $map[$k] = $v.Trim() }
if (-not $map['SUPABASE_URL'] -or -not $map['SUPABASE_ANON_KEY']) { Write-Host 'SUPABASE_URL / SUPABASE_ANON_KEY missing in android\local.properties.' -ForegroundColor Red; exit 1 }
$ErrorActionPreference = 'Continue'   # gh writes normal progress to stderr; we check exit codes instead
& $gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host 'GitHub CLI is not signed in for this window. Use a normal (not Administrator) PowerShell, or run: gh auth login' -ForegroundColor Red; exit 1 }
foreach ($name in 'SUPABASE_URL', 'SUPABASE_ANON_KEY') {
    $map[$name] | & $gh secret set $name --repo raunakpatil/wallpaper-app-android
    if ($LASTEXITCODE -ne 0) { Write-Host "Could not save $name to GitHub (see the message above). Nothing else was changed." -ForegroundColor Red; exit 1 }
}
& $gh workflow run supabase-keepalive.yml --repo raunakpatil/wallpaper-app-android
if ($LASTEXITCODE -ne 0) { Write-Host 'Secrets saved, but the test run did not start. Start it from GitHub -> Actions -> Supabase keep-alive -> Run workflow.' -ForegroundColor Yellow; exit 0 }
Write-Host 'Secrets saved and a first keep-alive run started. Check it under GitHub -> Actions.' -ForegroundColor Green
