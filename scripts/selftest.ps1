# Самотест ExtendedHeight: фазы baseline/vanilla-mod/write/verify/client-audit/net в dev и obf; -Env -NoClient -Phase

param(
    [ValidateSet('dev', 'obf', 'both')] [string]$Env = 'both',
    [switch]$NoClient,
    [ValidateSet('', 'baseline', 'vanilla-mod', 'write', 'verify', 'client-audit', 'net')] [string]$Phase = ''
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

if (-not $Phase -and (Test-Path $base)) { Remove-Item -Recurse -Force $base }
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

# Выделенный сервер сетевых фаз: тестовый тип мира, креатив, без защиты спавна
$netServerProperties = @'
level-type=extendedtest
level-seed=20261018
generate-structures=false
spawn-animals=false
spawn-monsters=false
spawn-npcs=false
allow-nether=false
online-mode=false
server-port=25599
view-distance=4
gamemode=1
force-gamemode=true
spawn-protection=0
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

# Создаёт рабочий каталог фазы и её свойства самотеста; возвращает каталог
function Initialize-Phase([string]$envName, [string]$dirName, [string]$phase, [hashtable]$extra,
    [bool]$disableMixins, [bool]$fresh, [string]$properties) {
    $dir = Join-Path (Join-Path $base $envName) $dirName
    if ($fresh) {
        if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
        New-Item -ItemType Directory -Force $dir | Out-Null
        if ($properties) {
            Set-Content -Encoding ascii (Join-Path $dir 'eula.txt') 'eula=true'
            Set-Content -Encoding ascii (Join-Path $dir 'server.properties') $properties
        }
    }
    $props = @("phase=$phase", "result=selftest-result-$phase.txt")
    foreach ($k in $extra.Keys) { $props += "$k=$($extra[$k] -replace '\\','/')" }
    Set-Content -Encoding ascii (Join-Path $dir 'extendedheight-selftest.properties') ($props -join "`n")
    $marker = Join-Path $dir 'extendedheight-disable-mixins'
    if ($disableMixins) { Set-Content -Encoding ascii $marker '' } elseif (Test-Path $marker) { Remove-Item $marker }
    return $dir
}

# Печатает отчёт фазы; true, если он кончается на OVERALL: PASS
function Read-Result([string]$dir, [string]$phase, [string]$log, $gradleExit) {
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

function Invoke-Phase([string]$envName, [string]$task, [string]$dirName, [string]$phase, [hashtable]$extra,
    [bool]$disableMixins, [bool]$fresh, [bool]$server) {
    $props = if ($server) { $serverProperties } else { '' }
    $dir = Initialize-Phase $envName $dirName $phase $extra $disableMixins $fresh $props
    Write-Host "=== [$envName] phase $phase ($task)"
    $log = Join-Path (Join-Path $base $envName) "$phase.gradle.log"
    $gradleExit = Invoke-Gradle $task "run/selftest/$envName/$dirName" $log
    return Read-Result $dir $phase $log $gradleExit
}

# Чеклист 3a шаг 7: выделенный сервер и два TCP-клиента — с модом и скрывающий мод (должен быть отклонён)
function Invoke-NetPhases([string]$envName, [string]$serverTask, [string]$clientTask) {
    $envBase = Join-Path $base $envName
    $props = $netServerProperties
    $dir = Initialize-Phase $envName 'netserver' 'net-server' @{} $false $true $props
    $log = Join-Path $envBase 'net-server.gradle.log'
    Write-Host "=== [$envName] phase net-server ($serverTask, in the background)"
    $p = Start-Process -FilePath (Join-Path $root 'gradlew.bat') -WorkingDirectory $root -NoNewWindow -PassThru `
        -ArgumentList @('--no-daemon', $serverTask, "-PehWorkDir=run/selftest/$envName/netserver") `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $deadline = (Get-Date).AddMinutes($PhaseTimeoutMinutes)
    $ready = $false
    while (-not $ready -and -not $p.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        $ready = (Test-Path $log) -and (Select-String -Path $log -Pattern 'network self-test server ready' -Quiet)
    }
    $r = [ordered]@{}
    if ($ready) {
        $r["$envName net-client"] = Invoke-Phase $envName $clientTask 'netclient' 'net-client' @{ server = '127.0.0.1:25599' } $false $true $false
        $r["$envName net-nomod"] = Invoke-Phase $envName $clientTask 'netnomod' 'net-nomod' @{ server = '127.0.0.1:25599' } $false $true $false
    } else {
        Write-Host "  dedicated server did not come up, see $log" -ForegroundColor Red
    }
    if (-not $p.WaitForExit(5 * 60 * 1000)) {
        Write-Host '  dedicated server did not stop, killing it' -ForegroundColor Red
        & taskkill.exe /T /F /PID $p.Id | Out-Null
    }
    Write-Host "=== [$envName] phase net-server report"
    $r["$envName net-server"] = Read-Result $dir 'net-server' $log $p.ExitCode
    return $r
}

$results = [ordered]@{}
$envs = if ($Env -eq 'both') { @('dev', 'obf') } else { @($Env) }
foreach ($e in $envs) {
    $serverTask = if ($e -eq 'obf') { 'runObfServer' } else { 'runServer' }
    $clientTask = if ($e -eq 'obf') { 'runObfClient' } else { 'runClient' }
    $envBase = Join-Path $base $e
    New-Item -ItemType Directory -Force $envBase | Out-Null
    $snapBaseline = Join-Path $envBase 'snapshot-baseline'
    $run = { param($p) -not $Phase -or $Phase -eq $p }
    if (& $run 'baseline') {
        if (Test-Path $snapBaseline) { Remove-Item -Recurse -Force $snapBaseline }
        $results["$e baseline"] = Invoke-Phase $e $serverTask 'baseline' 'baseline' @{ snapshot = $snapBaseline } $true $true $true
    }
    if (& $run 'vanilla-mod') {
        $results["$e vanilla-mod"] = Invoke-Phase $e $serverTask 'vanillamod' 'vanilla-mod' @{ snapshot = (Join-Path $envBase 'snapshot-vanillamod'); compareWith = $snapBaseline } $false $true $true
    }
    if (& $run 'write') {
        $results["$e write"] = Invoke-Phase $e $serverTask 'extended' 'write' @{ snapshot = (Join-Path $envBase 'snapshot-extended'); compareWith = $snapBaseline } $false $true $true
    }
    if (& $run 'verify') {
        $results["$e verify"] = Invoke-Phase $e $serverTask 'extended' 'verify' @{} $false $false $true
    }
    if (-not $NoClient -and (& $run 'client-audit')) {
        $results["$e client-audit"] = Invoke-Phase $e $clientTask 'client' 'client-audit' @{} $false $true $false
    }
    if (-not $NoClient -and (& $run 'net')) {
        $net = Invoke-NetPhases $e $serverTask $clientTask
        foreach ($k in $net.Keys) { $results[$k] = $net[$k] }
    }
}

Write-Host ''
$allOk = $true
foreach ($k in $results.Keys) {
    $state = if ($results[$k]) { 'PASS' } else { 'FAIL'; $allOk = $false }
    Write-Host ("{0,-20} {1}" -f $k, $state)
}
if (-not $allOk) { exit 1 }
