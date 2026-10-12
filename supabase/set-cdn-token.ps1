# Stores the GitHub token for the upload-wallpaper Edge Function as a Supabase secret.
# The token is read with hidden input, so it never appears on screen or in shell history.
# Usage (from the repo root):  powershell -ExecutionPolicy Bypass -File supabase\set-cdn-token.ps1
$ErrorActionPreference = 'Stop'
$projectRef = 'fjkfzqxbbnygzedqskkt'

Write-Host ''
Write-Host 'Paste the fine-grained GitHub token (wallpaper-cdn, Contents: read/write), then press Enter.' -ForegroundColor Cyan
$secure = Read-Host -AsSecureString 'Token (hidden)'
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim()
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

if ($token -notmatch '^(github_pat_|ghp_)[A-Za-z0-9_]{20,}$') {
    Write-Host 'That does not look like a GitHub token (should start with github_pat_). Nothing was saved.' -ForegroundColor Red
    exit 1
}

# Quick check that the token can actually see the CDN repo before saving it.
try {
    $repo = Invoke-RestMethod -Uri 'https://api.github.com/repos/raunakpatil/wallpaper-cdn' `
        -Headers @{ Authorization = "Bearer $token"; 'User-Agent' = 'set-cdn-token' }
    if (-not $repo.permissions.push) {
        Write-Host 'The token can read wallpaper-cdn but cannot write. Give it "Contents: Read and write". Nothing was saved.' -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "GitHub rejected the token or it has no access to wallpaper-cdn: $($_.Exception.Message). Nothing was saved." -ForegroundColor Red
    exit 1
}

npx supabase secrets set --project-ref $projectRef "GITHUB_TOKEN=$token" | Out-Host
$token = $null
Write-Host 'Done. The upload function can now write to the CDN.' -ForegroundColor Green
