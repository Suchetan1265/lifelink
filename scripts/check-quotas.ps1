<#
    Reports usage against the free-tier limits of every service LifeLink uses.

    None of these providers can bill you without a payment method on file -- they
    stop working rather than charging. This is about knowing when something is
    about to stop, not about avoiding a bill.

    Run from the repository root:  powershell -File scripts/check-quotas.ps1
#>

$ErrorActionPreference = "Continue"

function Show-Bar([string]$label, [double]$used, [double]$limit, [string]$unit) {
    $pct = if ($limit -gt 0) { [math]::Round(($used / $limit) * 100, 1) } else { 0 }
    $state = if ($pct -ge 90) { "CRITICAL" } elseif ($pct -ge 70) { "WATCH" } else { "ok" }
    "{0,-26} {1,10} / {2} {3,-8} {4,5}%  {5}" -f $label, [math]::Round($used, 2), $limit, $unit, $pct, $state
}

Write-Host ""
Write-Host "LifeLink free-tier usage" -ForegroundColor Cyan
Write-Host ("-" * 78)

# ---------- Neon -------------------------------------------------------------
Write-Host "`nNEON (Postgres)" -ForegroundColor Yellow
try {
    $neonBin = Join-Path $env:APPDATA "npm\neon.cmd"
    $json = & $neonBin projects get shy-sunset-38479693 --output json 2>$null | ConvertFrom-Json
    Show-Bar "compute time" ($json.compute_time_seconds / 3600) 191.9 "hours"
    Show-Bar "storage" ($json.synthetic_storage_size / 1GB) 0.5 "GB"
    Show-Bar "data transfer" ($json.data_transfer_bytes / 1GB) 5 "GB"
    Write-Host "  note: compute only accrues while a connection is open. Render sleeping"
    Write-Host "        is what keeps this low -- see the warning at the end."
} catch {
    Write-Host "  could not read Neon usage: $($_.Exception.Message)"
}

# ---------- Upstash ----------------------------------------------------------
Write-Host "`nUPSTASH (Redis)" -ForegroundColor Yellow
try {
    $envFile = Join-Path $PSScriptRoot "..\.env.render"
    $token = (Get-Content $envFile | Where-Object { $_ -match '^SPRING_DATA_REDIS_PASSWORD=' }) -replace '^SPRING_DATA_REDIS_PASSWORD=', ''
    $redisHost = (Get-Content $envFile | Where-Object { $_ -match '^SPRING_DATA_REDIS_HOST=' }) -replace '^SPRING_DATA_REDIS_HOST=', ''
    $headers = @{ Authorization = "Bearer $token" }
    $info = (Invoke-RestMethod "https://$redisHost/info" -Headers $headers -TimeoutSec 30).result -split "`r?`n"
    $mem = ($info | Where-Object { $_ -match '^used_memory:' }) -replace 'used_memory:', ''
    $cmds = ($info | Where-Object { $_ -match '^total_commands_processed:' }) -replace 'total_commands_processed:', ''
    $size = (Invoke-RestMethod "https://$redisHost/dbsize" -Headers $headers -TimeoutSec 30).result
    Show-Bar "memory" ([double]$mem / 1MB) 256 "MB"
    Write-Host ("{0,-26} {1,10}" -f "keys stored", $size)
    Write-Host ("{0,-26} {1,10}    (free tier allows 10,000 per day)" -f "commands, lifetime", $cmds)
} catch {
    Write-Host "  could not read Upstash usage: $($_.Exception.Message)"
}

# ---------- Render -----------------------------------------------------------
Write-Host "`nRENDER (app hosting)" -ForegroundColor Yellow
Write-Host "  No public usage API without a key. Check the dashboard:"
Write-Host "    https://dashboard.render.com  ->  Billing"
Write-Host "  Free limits: 750 instance hours, 500 build minutes, 5 GB bandwidth per month."
Write-Host "  Build minutes are the one that actually runs out -- each deploy compiles"
Write-Host "  Maven and npm inside Docker, so it is several minutes per push."

# ---------- CloudAMQP --------------------------------------------------------
Write-Host "`nCLOUDAMQP (RabbitMQ)" -ForegroundColor Yellow
Write-Host "  Little Lemur: 1M messages/month, 100 queued at once, 20 connections."
Write-Host "  Check: https://customer.cloudamqp.com  ->  your instance  ->  Metrics"
Write-Host "  The 100-message queue depth binds first if a worker ever stops consuming."

Write-Host "`n$("-" * 78)"
Write-Host "None of these bill without a card on file. They stop serving instead." -ForegroundColor Green
Write-Host ""
Write-Host "The one real trap:" -ForegroundColor Red
Write-Host "  Making the Render service always-on removes cold starts, but then Quartz"
Write-Host "  polls the database every 15 minutes forever, so Neon never scales to zero"
Write-Host "  and burns ~744 compute hours a month against a ~192 hour free allowance."
Write-Host "  Upgrade Render and you must budget for Neon too."
Write-Host ""
