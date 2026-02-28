param(
    [string]$BaseUrl = 'http://localhost:8081',
    [switch]$EndCall = $true
)

$ErrorActionPreference = 'Stop'

function Login-User {
    param(
        [string]$Email,
        [string]$Password,
        [string]$Role
    )

    $body = @{ email = $Email; password = $Password; role = $Role } | ConvertTo-Json
    $resp = Invoke-WebRequest -Method Post -Uri "$BaseUrl/v1/api/auth/login" -ContentType 'application/json' -Body $body -UseBasicParsing
    $json = $resp.Content | ConvertFrom-Json

    if (-not $json.token) {
        throw "Login succeeded but token missing for $Email"
    }

    return [PSCustomObject]@{
        Email = $Email
        Role = $Role
        Token = $json.token
        StatusCode = $resp.StatusCode
    }
}

function Invoke-Checked {
    param(
        [scriptblock]$Action,
        [string]$Name
    )

    try {
        $r = & $Action
        [PSCustomObject]@{
            Name = $Name
            StatusCode = $r.StatusCode
            Content = $r.Content
            Ok = $true
        }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $reader = New-Object IO.StreamReader($resp.GetResponseStream())
            $body = $reader.ReadToEnd()
            [PSCustomObject]@{
                Name = $Name
                StatusCode = [int]$resp.StatusCode
                Content = $body
                Ok = $false
            }
        } else {
            throw
        }
    }
}

$callId = "verify_call_$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
if ([string]::IsNullOrWhiteSpace($callId)) {
    throw 'callId is empty'
}

Write-Output "base=$BaseUrl"
Write-Output "callId=$callId"

$caregiver = Login-User -Email 'caregiver@careconnect.com' -Password 'password' -Role 'CAREGIVER'
$patient = Login-User -Email 'patient@careconnect.com' -Password 'password' -Role 'PATIENT'

Write-Output "caregiver_login=$($caregiver.StatusCode) token_len=$($caregiver.Token.Length)"
Write-Output "patient_login=$($patient.StatusCode) token_len=$($patient.Token.Length)"

$patientProtected = Invoke-Checked -Name 'patient_protected' -Action {
    Invoke-WebRequest -Method Get -Uri "$BaseUrl/v1/api/caregiver-patient-links/patients/1/caregivers" -Headers @{ Authorization = "Bearer $($patient.Token)" } -UseBasicParsing
}
Write-Output "patient_protected_status=$($patientProtected.StatusCode)"

$caregiverJoin = Invoke-Checked -Name 'caregiver_join' -Action {
    Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v3/calls/$callId/join" -Headers @{ Authorization = "Bearer $($caregiver.Token)" } -UseBasicParsing
}
Write-Output "caregiver_join_status=$($caregiverJoin.StatusCode)"
if (-not $caregiverJoin.Ok) {
    Write-Output "caregiver_join_body=$($caregiverJoin.Content)"
    exit 1
}

$patientJoin = Invoke-Checked -Name 'patient_join' -Action {
    Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v3/calls/$callId/join" -Headers @{ Authorization = "Bearer $($patient.Token)" } -UseBasicParsing
}
Write-Output "patient_join_status=$($patientJoin.StatusCode)"
if (-not $patientJoin.Ok) {
    Write-Output "patient_join_body=$($patientJoin.Content)"
    if ($EndCall) {
        $endAttempt = Invoke-Checked -Name 'end_call_after_failure' -Action {
            Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v3/calls/$callId/end" -Headers @{ Authorization = "Bearer $($caregiver.Token)" } -ContentType 'application/json' -Body '{}' -UseBasicParsing
        }
        Write-Output "end_call_after_failure_status=$($endAttempt.StatusCode)"
    }
    exit 1
}

if ($EndCall) {
    $endCallResult = Invoke-Checked -Name 'end_call' -Action {
        Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v3/calls/$callId/end" -Headers @{ Authorization = "Bearer $($caregiver.Token)" } -ContentType 'application/json' -Body '{}' -UseBasicParsing
    }
    Write-Output "end_call_status=$($endCallResult.StatusCode)"
}

Write-Output 'result=PASS'
