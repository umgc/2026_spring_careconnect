param(
    [string]$ProjectKeyword = "careconnect"
)

$ErrorActionPreference = "Stop"

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

function Write-Status {
    param(
        [string]$Status,
        [string]$Message
    )

    switch ($Status) {
        "PASS" { Write-Host "PASS  $Message" -ForegroundColor Green }
        "WARN" { Write-Host "WARN  $Message" -ForegroundColor Yellow }
        "FAIL" { Write-Host "FAIL  $Message" -ForegroundColor Red }
        default { Write-Host "INFO  $Message" }
    }
}

function Invoke-AwsJson {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & aws @Arguments --output json 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI failed for command: aws $($Arguments -join ' ')"
    }

    if ([string]::IsNullOrWhiteSpace($output)) {
        return $null
    }

    return $output | ConvertFrom-Json
}

function Get-Count {
    param($Value)

    if ($null -eq $Value) { return 0 }
    if ($Value -is [System.Array]) { return $Value.Count }
    return 1
}

$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param(
        [string]$Check,
        [string]$Status,
        [int]$Count,
        [string]$Details
    )

    $results.Add([PSCustomObject]@{
        Check   = $Check
        Status  = $Status
        Count   = $Count
        Details = $Details
    }) | Out-Null
}

Write-Header "AWS Cost Guard"
Write-Host "Project keyword filter: $ProjectKeyword"

try {
    $identity = Invoke-AwsJson -Arguments @("sts", "get-caller-identity")
    Write-Status "PASS" "Authenticated as account $($identity.Account), ARN $($identity.Arn)"
} catch {
    Write-Status "FAIL" "Not authenticated. Run 'aws login' first."
    exit 1
}

Write-Header "Runtime Cost Drivers"

$clusters = (Invoke-AwsJson -Arguments @("ecs", "list-clusters")).clusterArns
$clusterCount = Get-Count $clusters
if ($clusterCount -eq 0) {
    Add-Result -Check "ECS Clusters" -Status "PASS" -Count 0 -Details "No ECS clusters"
} else {
    Add-Result -Check "ECS Clusters" -Status "FAIL" -Count $clusterCount -Details (($clusters | ForEach-Object { ($_ -split "/")[-1] }) -join ", ")
}

$serviceCount = 0
if ($clusterCount -gt 0) {
    foreach ($clusterArn in $clusters) {
        $services = (Invoke-AwsJson -Arguments @("ecs", "list-services", "--cluster", $clusterArn)).serviceArns
        $serviceCount += Get-Count $services
    }
}
if ($serviceCount -eq 0) {
    Add-Result -Check "ECS Services" -Status "PASS" -Count 0 -Details "No ECS services"
} else {
    Add-Result -Check "ECS Services" -Status "FAIL" -Count $serviceCount -Details "One or more services still running"
}

$natGateways = (Invoke-AwsJson -Arguments @("ec2", "describe-nat-gateways", "--filter", "Name=state,Values=available,pending")).NatGateways
$natCount = Get-Count $natGateways
if ($natCount -eq 0) {
    Add-Result -Check "NAT Gateways" -Status "PASS" -Count 0 -Details "No NAT gateways"
} else {
    Add-Result -Check "NAT Gateways" -Status "FAIL" -Count $natCount -Details "NAT gateways are billable per hour"
}

$loadBalancers = (Invoke-AwsJson -Arguments @("elbv2", "describe-load-balancers")).LoadBalancers
$lbCount = Get-Count $loadBalancers
if ($lbCount -eq 0) {
    Add-Result -Check "Load Balancers" -Status "PASS" -Count 0 -Details "No ALB/NLB"
} else {
    Add-Result -Check "Load Balancers" -Status "FAIL" -Count $lbCount -Details "Load balancers still provisioned"
}

$dbInstances = (Invoke-AwsJson -Arguments @("rds", "describe-db-instances")).DBInstances
$dbCount = Get-Count $dbInstances
if ($dbCount -eq 0) {
    Add-Result -Check "RDS Instances" -Status "PASS" -Count 0 -Details "No RDS instances"
} else {
    Add-Result -Check "RDS Instances" -Status "FAIL" -Count $dbCount -Details "RDS instances still provisioned"
}

$instances = @()
$instanceReservations = (Invoke-AwsJson -Arguments @("ec2", "describe-instances")).Reservations
if ($instanceReservations) {
    foreach ($reservation in $instanceReservations) {
        foreach ($instance in $reservation.Instances) {
            if ($instance.State.Name -ne "terminated") {
                $instances += $instance
            }
        }
    }
}
$instanceCount = Get-Count $instances
if ($instanceCount -eq 0) {
    Add-Result -Check "EC2 Instances" -Status "PASS" -Count 0 -Details "No non-terminated EC2 instances"
} else {
    $activeIds = $instances | ForEach-Object { "$($_.InstanceId):$($_.State.Name)" }
    Add-Result -Check "EC2 Instances" -Status "FAIL" -Count $instanceCount -Details ($activeIds -join ", ")
}

Write-Header "Secondary Cost Drivers"

$addresses = (Invoke-AwsJson -Arguments @("ec2", "describe-addresses")).Addresses
$addressCount = Get-Count $addresses
if ($addressCount -eq 0) {
    Add-Result -Check "Elastic IPs" -Status "PASS" -Count 0 -Details "No allocated EIPs"
} else {
    Add-Result -Check "Elastic IPs" -Status "WARN" -Count $addressCount -Details "Allocated EIPs may incur cost"
}

$volumes = (Invoke-AwsJson -Arguments @("ec2", "describe-volumes", "--filters", "Name=status,Values=available")).Volumes
$volumeCount = Get-Count $volumes
if ($volumeCount -eq 0) {
    Add-Result -Check "Unattached EBS" -Status "PASS" -Count 0 -Details "No unattached EBS volumes"
} else {
    Add-Result -Check "Unattached EBS" -Status "WARN" -Count $volumeCount -Details "Unattached EBS volumes incur storage cost"
}

$snapshots = (Invoke-AwsJson -Arguments @("ec2", "describe-snapshots", "--owner-ids", "self")).Snapshots
$snapshotCount = Get-Count $snapshots
if ($snapshotCount -eq 0) {
    Add-Result -Check "EBS Snapshots" -Status "PASS" -Count 0 -Details "No snapshots"
} else {
    Add-Result -Check "EBS Snapshots" -Status "WARN" -Count $snapshotCount -Details "Snapshots incur storage cost"
}

$logGroups = (Invoke-AwsJson -Arguments @("logs", "describe-log-groups")).logGroups
$projectLogGroups = @()
if ($logGroups) {
    $projectLogGroups = $logGroups | Where-Object { $_.logGroupName -match [regex]::Escape($ProjectKeyword) }
}
$projectLogCount = Get-Count $projectLogGroups
if ($projectLogCount -eq 0) {
    Add-Result -Check "Project Log Groups" -Status "PASS" -Count 0 -Details "No matching CloudWatch log groups"
} else {
    $bytes = ($projectLogGroups | Measure-Object -Property storedBytes -Sum).Sum
    if ($null -eq $bytes) { $bytes = 0 }
    Add-Result -Check "Project Log Groups" -Status "WARN" -Count $projectLogCount -Details ("Stored bytes: {0}" -f $bytes)
}

$buckets = (Invoke-AwsJson -Arguments @("s3api", "list-buckets")).Buckets
$projectBuckets = @()
if ($buckets) {
    $projectBuckets = $buckets | Where-Object { $_.Name -match [regex]::Escape($ProjectKeyword) }
}
$bucketCount = Get-Count $projectBuckets
if ($bucketCount -eq 0) {
    Add-Result -Check "Project S3 Buckets" -Status "PASS" -Count 0 -Details "No matching S3 buckets"
} else {
    Add-Result -Check "Project S3 Buckets" -Status "WARN" -Count $bucketCount -Details (($projectBuckets | ForEach-Object { $_.Name }) -join ", ")
}

Write-Header "Summary"
$results | Sort-Object Check | Format-Table -AutoSize

$failCount = ($results | Where-Object { $_.Status -eq "FAIL" }).Count
$warnCount = ($results | Where-Object { $_.Status -eq "WARN" }).Count

if ($failCount -eq 0 -and $warnCount -eq 0) {
    Write-Status "PASS" "All checks clean. No obvious ongoing AWS cost drivers found."
    exit 0
}

if ($failCount -eq 0 -and $warnCount -gt 0) {
    Write-Status "WARN" "No critical runtime drivers found, but secondary billable resources remain."
    exit 0
}

Write-Status "FAIL" "Critical runtime cost drivers still exist."
exit 2
