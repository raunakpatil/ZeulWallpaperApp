# Stores the Firebase service-account key for the send-push Edge Function as Supabase secrets,
# plus a random CRON_SECRET for the automatic daily push.
# The key file is picked in a dialog and sent straight to Supabase; it never appears on screen.
# Usage (from the repo root):  powershell -ExecutionPolicy Bypass -File supabase\set-firebase-key.ps1
$ErrorActionPreference = 'Stop'
$projectRef = 'fjkfzqxbbnygzedqskkt'

Add-Type -AssemblyName System.Windows.Forms
$dlg = New-Object System.Windows.Forms.OpenFileDialog
$dlg.Title = 'Choose the Firebase service-account key (.json) you just downloaded'
$dlg.Filter = 'JSON key (*.json)|*.json'
$dlg.InitialDirectory = [Environment]::GetFolderPath('UserProfile') + '\Downloads'
if ($dlg.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { Write-Host 'Cancelled. Nothing was saved.'; exit 1 }

$raw = Get-Content -Raw -Encoding UTF8 $dlg.FileName
try { $j = $raw | ConvertFrom-Json } catch { Write-Host 'That file is not valid JSON. Nothing was saved.' -ForegroundColor Red; exit 1 }
if ($j.type -ne 'service_account' -or -not $j.project_id -or -not $j.private_key -or -not $j.client_email) {
    Write-Host 'That is not a service-account key. In Firebase: Project settings -> Service accounts -> Generate new private key.' -ForegroundColor Red
    exit 1
}

$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($raw))
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$cron = -join ($bytes | ForEach-Object { $_.ToString('x2') })

Write-Host "Saving key for Firebase project '$($j.project_id)'..." -ForegroundColor Cyan
npx supabase secrets set --project-ref $projectRef "FIREBASE_SERVICE_ACCOUNT=$b64" "CRON_SECRET=$cron" | Out-Host
if ($LASTEXITCODE -ne 0) { Write-Host 'Supabase rejected the secrets. Nothing else was changed.' -ForegroundColor Red; exit 1 }

$sql = "update private.push_cron set url = 'https://$projectRef.supabase.co/functions/v1/send-push', secret = '$cron' where id = 1;"
Set-Clipboard -Value $sql
$raw = $null; $b64 = $null; $cron = $null

Write-Host ''
Write-Host 'Done. The dashboard can now send notifications.' -ForegroundColor Green
Write-Host 'Last step for the automatic daily push: open the Supabase SQL Editor, paste (Ctrl+V) and Run.' -ForegroundColor Yellow
Write-Host "Then delete the key file from Downloads ($([IO.Path]::GetFileName($dlg.FileName))); you can always generate a new one." -ForegroundColor Yellow
