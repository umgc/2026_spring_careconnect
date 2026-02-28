Param(
    [string]$OutputPath = "web/amazon-chime-sdk.min.js"
)

$ErrorActionPreference = "Stop"

$targets = @(
    "https://unpkg.com/amazon-chime-sdk-js@3.20.0/build/amazon-chime-sdk.min.js",
    "https://cdn.jsdelivr.net/npm/amazon-chime-sdk-js@3.20.0/build/amazon-chime-sdk.min.js"
)

$output = Join-Path $PSScriptRoot $OutputPath
$outputDir = Split-Path -Parent $output
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

foreach ($url in $targets) {
    try {
        Write-Host "Downloading Chime SDK from: $url"
        Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing

        if ((Test-Path $output) -and ((Get-Item $output).Length -gt 0)) {
            Write-Host "Chime SDK ready at: $output"
            exit 0
        }
    }
    catch {
        Write-Warning "Failed from $url : $($_.Exception.Message)"
    }
}

throw "Failed to download amazon-chime-sdk.min.js from all configured sources."
