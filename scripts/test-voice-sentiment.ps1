param(
    [string]$BackendBaseUrl = "http://localhost:8080",
    [string]$Email,
    [string]$Password,
    [string]$Jwt,
    [string]$CallId = "TEST_CALL_1",
    [int]$DurationMs = 2500,
    [int]$SampleRate = 16000,
    [int]$FrequencyHz = 440,
    [switch]$SkipProbe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-TestWavBase64 {
    param(
        [int]$DurationMs,
        [int]$SampleRate,
        [int]$FrequencyHz
    )

    $channels = 1
    $bitsPerSample = 16
    $bytesPerSample = $bitsPerSample / 8
    $sampleCount = [Math]::Max(1, [int]($SampleRate * ($DurationMs / 1000.0)))
    $dataSize = $sampleCount * $channels * $bytesPerSample

    $stream = New-Object System.IO.MemoryStream
    $writer = New-Object System.IO.BinaryWriter($stream)

    try {
        # WAV header (RIFF + fmt + data)
        $writer.Write([Text.Encoding]::ASCII.GetBytes("RIFF"))
        $writer.Write([int](36 + $dataSize))
        $writer.Write([Text.Encoding]::ASCII.GetBytes("WAVE"))
        $writer.Write([Text.Encoding]::ASCII.GetBytes("fmt "))
        $writer.Write([int]16)
        $writer.Write([int16]1)
        $writer.Write([int16]$channels)
        $writer.Write([int]$SampleRate)
        $writer.Write([int]($SampleRate * $channels * $bytesPerSample))
        $writer.Write([int16]($channels * $bytesPerSample))
        $writer.Write([int16]$bitsPerSample)
        $writer.Write([Text.Encoding]::ASCII.GetBytes("data"))
        $writer.Write([int]$dataSize)

        # Simple sine wave to produce a valid, non-empty audio sample
        for ($i = 0; $i -lt $sampleCount; $i++) {
            $t = $i / [double]$SampleRate
            $sample = [Math]::Sin(2.0 * [Math]::PI * $FrequencyHz * $t)
            $pcm = [int16]([Math]::Round($sample * 12000))
            $writer.Write($pcm)
        }

        $bytes = $stream.ToArray()
        return [Convert]::ToBase64String($bytes)
    }
    finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

if ([string]::IsNullOrWhiteSpace($Jwt)) {
    if ([string]::IsNullOrWhiteSpace($Email) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide either -Jwt or both -Email and -Password."
    }

    $loginBody = @{
        email = $Email
        password = $Password
    } | ConvertTo-Json -Compress

    $loginUri = "$BackendBaseUrl/v1/api/auth/login"
    Write-Host "Logging in at $loginUri ..."
    $loginResp = Invoke-RestMethod -Method Post -Uri $loginUri -ContentType "application/json" -Body $loginBody
    $Jwt = $loginResp.token

    if ([string]::IsNullOrWhiteSpace($Jwt)) {
        throw "Login succeeded but no JWT token was returned."
    }
}

$headers = @{ Authorization = "Bearer $Jwt" }

if (-not $SkipProbe) {
    $probeUri = "$BackendBaseUrl/api/v3/calls/sentiment/voice/probe"
    Write-Host "Running probe: $probeUri"
    $probe = Invoke-RestMethod -Method Get -Uri $probeUri -Headers $headers
    $probeJson = $probe | ConvertTo-Json -Depth 10
    Write-Host "Probe result:"
    Write-Host $probeJson
}

$audioBase64 = New-TestWavBase64 -DurationMs $DurationMs -SampleRate $SampleRate -FrequencyHz $FrequencyHz
$voiceBody = @{
    audioBase64 = $audioBase64
    audioFormat = "wav"
} | ConvertTo-Json -Compress

$voiceUri = "$BackendBaseUrl/api/v3/calls/$CallId/sentiment/voice"
Write-Host "Submitting generated WAV to: $voiceUri"
$voiceResp = Invoke-RestMethod -Method Post -Uri $voiceUri -Headers $headers -ContentType "application/json" -Body $voiceBody

Write-Host "Voice sentiment response:"
$voiceResp | ConvertTo-Json -Depth 10
