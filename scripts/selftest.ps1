# Самотест ExtendedHeight: фазы baseline/vanilla-mod/write/verify/client-audit в dev и obf; -Env -NoClient

param(
    [ValidateSet('dev', 'obf', 'both')] [string]$Env = 'both',
    [switch]$NoClient
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$base = Join-Path $root 'run\selftest'

# Gradle нужен JDK 17+: EH_JAVA_HOME, затем известные JDK, затем JAVA_HOME
$javaCandidates = @($env:EH_JAVA_HOME, 'C:\Program Files\Java\jdk-25', 'C:\Program Files\Java\jdk-21',
    'C:\Program Files\Java\jdk-17', $env:JAVA_HOME) | Where-Object { $_ }
$javaHome = $javaCandidates | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
if (-not $javaHome) { throw 'No JDK found: set EH_JAVA_HOME to a JDK 17+ installation' }
$env:JAVA_HOME = $javaHome
Write-Host "JAVA_HOME=$javaHome"

if (Test-Path $base) { Remove-Item -Recurse -Force $base }
New-Item -ItemType Directory -Force $base | Out-Null

$serverProperties = @'
level-type=FLAT
generator-settings=2;7,2x3,2;1;
level-seed=20261018
generate-structures=false
spawn-animals=false
spawn-monsters=false
spawn-npcs=false
allow-nether=false
online-mode=false
server-port=25599
view-distance=4
'@

$PhaseTimeoutMinutes = 12

# Запускает задачу gradle с таймаутом; по таймауту убивает всё дерево процессов
function Invoke-Gradle([string]$task, [string]$relDir, [string]$log) {
    $p = Start-Process -FilePath (Join-Path $root 'gradlew.bat') -WorkingDirectory $root -NoNewWindow -PassThru `
        -ArgumentList @('--no-daemon', $task, "-PehWorkDir=$relDir") `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    if (-not $p.WaitForExit($PhaseTimeoutMinutes * 60 * 1000)) {
        Write-Host "  TIMEOUT after $PhaseTimeoutMinutes min, killing the process tree" -ForegroundColor Red
        & taskkill.exe /T /F /PID $p.Id | Out-Null
        return -1
    }
    return $p.ExitCode
}

function Invoke-Phase([string]$envName, [string]$task, [string]$dirName, [string]$phase, [hashtable]$extra,
    [bool]$disableMixins, [bool]$fresh, [bool]$server) {
    $envBase = Join-Path $base $envName
    $dir = Join-Path $envBase $dirName
    if ($fresh) {
        New-Item -ItemType Directory -Force $dir | Out-Null
        if ($server) {
            Set-Content -Encoding ascii (Join-Path $dir 'eula.txt') 'eula=true'
            Set-Content -Encoding ascii (Join-Path $dir 'server.properties') $serverProperties
        }
    }
    $props = @("phase=$phase", "result=selftest-result-$phase.txt")
    foreach ($k in $extra.Keys) { $props += "$k=$($extra[$k] -replace '\\','/')" }
    Set-Content -Encoding ascii (Join-Path $dir 'extendedheight-selftest.properties') ($props -join "`n")
    $marker = Join-Path $dir 'extendedheight-disable-mixins'
    if ($disableMixins) { Set-Content -Encoding ascii $marker '' } elseif (Test-Path $marker) { Remove-Item $marker }

    Write-Host "=== [$envName] phase $phase ($task)"
    $log = Join-Path $envBase "$phase.gradle.log"
    $gradleExit = Invoke-Gradle $task "run/selftest/$envName/$dirName" $log

    $result = Join-Path $dir "selftest-result-$phase.txt"
    if (-not (Test-Path $result)) {
        Write-Host "  no report (gradle exit $gradleExit), see $log" -ForegroundColor Red
        return $false
    }
    $lines = Get-Content $result
    $lines | Where-Object { $_ -like 'INFO*' -or $_ -like 'BENCH*' } | ForEach-Object { Write-Host "  $_" }
    $lines | Where-Object { $_ -like 'FAIL*' } | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    $passed = ($lines | Where-Object { $_ -like 'PASS*' }).Count
    $ok = $lines[-1] -eq 'OVERALL: PASS'
    Write-Host ("  {0} checks passed, {1}" -f $passed, $lines[-1]) -ForegroundColor ($(if ($ok) { 'Green' } else { 'Red' }))
    return $ok
}

$results = [ordered]@{}
$envs = if ($Env -eq 'both') { @('dev', 'obf') } else { @($Env) }
foreach ($e in $envs) {
    $serverTask = if ($e -eq 'obf') { 'runObfServer' } else { 'runServer' }
    $clientTask = if ($e -eq 'obf') { 'runObfClient' } else { 'runClient' }
    $envBase = Join-Path $base $e
    New-Item -ItemType Directory -Force $envBase | Out-Null
    $snapBaseline = Join-Path $envBase 'snapshot-baseline'
    $results["$e baseline"] = Invoke-Phase $e $serverTask 'baseline' 'baseline' @{ snapshot = $snapBaseline } $true $true $true
    $results["$e vanilla-mod"] = Invoke-Phase $e $serverTask 'vanillamod' 'vanilla-mod' @{ snapshot = (Join-Path $envBase 'snapshot-vanillamod'); compareWith = $snapBaseline } $false $true $true
    $results["$e write"] = Invoke-Phase $e $serverTask 'extended' 'write' @{ snapshot = (Join-Path $envBase 'snapshot-extended'); compareWith = $snapBaseline } $false $true $true
    $results["$e verify"] = Invoke-Phase $e $serverTask 'extended' 'verify' @{} $false $false $true
    if (-not $NoClient) {
        $results["$e client-audit"] = Invoke-Phase $e $clientTask 'client' 'client-audit' @{} $false $true $false
    }
}

Write-Host ''
$allOk = $true
foreach ($k in $results.Keys) {
    $state = if ($results[$k]) { 'PASS' } else { 'FAIL'; $allOk = $false }
    Write-Host ("{0,-20} {1}" -f $k, $state)
}
if (-not $allOk) { exit 1 }
