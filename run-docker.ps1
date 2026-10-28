Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "               STARTING CAMPUSMESH WEB DASHBOARD VIA DOCKER                      " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

# Auto-detect host LAN IP for mobile device connectivity
$lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notmatch '^127\.' -and
    $_.IPAddress -notmatch '^172\.' -and
    $_.PrefixOrigin -ne 'WellKnown' -and
    $_.AddressState -eq 'Preferred'
} | Select-Object -First 1).IPAddress

if (-not $lanIp) { $lanIp = "127.0.0.1" }

Write-Host "Detected Host LAN IP: $lanIp" -ForegroundColor Yellow

# Add Windows Firewall rule if not already present (allows mobile access)
$ruleName = "CampusMesh Web Dashboard (TCP 8080)"
$existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if (-not $existingRule) {
    Write-Host "Adding Windows Firewall rule for port 8080..." -ForegroundColor Gray
    try {
        New-NetFirewallRule -DisplayName $ruleName `
            -Direction Inbound -Protocol TCP -LocalPort 8080 `
            -Action Allow -Profile Private,Public `
            -Description "Allow mobile devices to access CampusMesh Web Dashboard" `
            -ErrorAction Stop | Out-Null
        Write-Host "Firewall rule added successfully." -ForegroundColor Green
    } catch {
        Write-Host "Note: To allow mobile devices through Windows Firewall, run PowerShell as Administrator:" -ForegroundColor DarkYellow
        Write-Host "      netsh advfirewall firewall add rule name=`"$ruleName`" dir=in action=allow protocol=TCP localport=8080" -ForegroundColor Gray
    }
}

# Save to .env file so docker-compose and other tools always have the current host IP
Set-Content -Path (Join-Path $PSScriptRoot ".env") -Value "HOST_LAN_IP=$lanIp" -Encoding utf8
$env:HOST_LAN_IP = $lanIp

# Build if needed and start container
docker compose up -d --build campusmesh

Write-Host ""
Write-Host "CampusMesh container is running!" -ForegroundColor Green
Write-Host "  Dashboard (Laptop):  http://localhost:8080" -ForegroundColor Yellow
Write-Host "  Dashboard (Mobile):  http://${lanIp}:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "  HOW TO CONNECT YOUR MOBILE PHONE:" -ForegroundColor White
Write-Host "  1. Connect phone to the same Wi-Fi as this laptop (or enable Mobile Hotspot)" -ForegroundColor Gray
Write-Host "  2. Open Chrome/Safari on your phone and navigate to:" -ForegroundColor Gray
Write-Host "     --> http://${lanIp}:8080" -ForegroundColor Cyan
Write-Host "  3. Your phone appears on the Radar as an online node!" -ForegroundColor Gray
Write-Host ""
Write-Host "Logs: docker compose logs -f campusmesh" -ForegroundColor DarkGray
Write-Host "Stop: docker compose down" -ForegroundColor DarkGray
