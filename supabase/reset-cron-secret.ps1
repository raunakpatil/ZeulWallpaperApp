# Makes a new CRON_SECRET for the automatic daily push (A16), saves it as a Supabase secret,
# and copies the matching SQL line to the clipboard. Use it if that line got lost.
# Usage (from any folder):  powershell -ExecutionPolicy Bypass -File "R:\Wallpaper APP\supabase\reset-cron-secret.ps1"
$ErrorActionPreference = 'Continue'
$projectRef = 'fjkfzqxbbnygzedqskkt'
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$cron = -join ($bytes | ForEach-Object { $_.ToString('x2') })
npx supabase secrets set --project-ref $projectRef "CRON_SECRET=$cron" | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Host 'Supabase rejected the secret. Nothing was changed.' -ForegroundColor Red; exit 1 }
$sql = "update private.push_cron set url = 'https://$projectRef.supabase.co/functions/v1/send-push', secret = '$cron' where id = 1;"
Set-Clipboard -Value $sql
Start-Sleep -Milliseconds 300
if ((Get-Clipboard -Raw) -notlike 'update private.push_cron*') {
    Write-Host 'Could not copy to the clipboard. Run this script again.' -ForegroundColor Red; exit 1
}
$cron = $null; $sql = $null
Write-Host ''
Write-Host 'New secret saved. The SQL line is on your clipboard (checked).' -ForegroundColor Green
Write-Host 'Now: Supabase -> SQL Editor -> new query -> Ctrl+V -> Run. Expect "Success. No rows returned".' -ForegroundColor Yellow
Write-Host 'Do this right away, before copying anything else.' -ForegroundColor Yellow
