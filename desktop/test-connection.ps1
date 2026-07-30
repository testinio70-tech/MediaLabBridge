param(
    [string]$Server = "http://127.0.0.1:8765",
    [Parameter(Mandatory = $true)]
    [string]$Token
)

$health = Invoke-RestMethod -Uri "$Server/health" -Method Get
Write-Host "Health:" ($health | ConvertTo-Json -Compress)

$body = @{
    requestId = [guid]::NewGuid().ToString()
    action    = "copy"
    text      = "MediaLabBridge conectado correctamente: $(Get-Date -Format o)"
} | ConvertTo-Json

$result = Invoke-RestMethod `
    -Uri "$Server/api/v1/command" `
    -Method Post `
    -Headers @{ Authorization = "Bearer $Token" } `
    -ContentType "application/json" `
    -Body $body

Write-Host "Command:" ($result | ConvertTo-Json -Compress)
