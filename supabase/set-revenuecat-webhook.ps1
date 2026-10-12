# Creates the shared secret for the revenuecat-webhook function (A11).
# It is saved as a Supabase secret and copied to your clipboard so you can paste it into RevenueCat
# (Project -> Integrations -> Webhooks -> "Authorization header value"). It is never shown on screen.
# Usage (from the repo root):  powershell -ExecutionPolicy Bypass -File supabase\set-revenuecat-webhook.ps1
$ErrorActionPreference = 'Stop'
$projectRef = 'fjkfzqxbbnygzedqskkt'
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$secret = 'rcwh_' + (-join ($bytes | ForEach-Object { $_.ToString('x2') }))
npx supabase secrets set --project-ref $projectRef "REVENUECAT_WEBHOOK_AUTH=$secret" | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Host 'Supabase rejected the secret. Nothing was saved.' -ForegroundColor Red; exit 1 }
Set-Clipboard -Value $secret
$secret = $null
Write-Host ''
Write-Host 'Saved. The secret is on your clipboard.' -ForegroundColor Green
Write-Host "In RevenueCat -> Integrations -> Webhooks -> + New:" -ForegroundColor Yellow
Write-Host "  URL:                  https://$projectRef.supabase.co/functions/v1/revenuecat-webhook" -ForegroundColor Yellow
Write-Host '  Authorization header: paste (Ctrl+V)' -ForegroundColor Yellow
Write-Host 'Then press "Send test event"; it should show 200.' -ForegroundColor Yellow
