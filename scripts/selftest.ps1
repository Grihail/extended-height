# Серверный самотест ExtendedHeight (этап 1): четыре запуска runServer — baseline, vanilla-mod, write, verify

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

function Invoke-Phase([string]$dirName, [string]$phase, [hashtable]$extra, [bool]$disableMixins, [bool]$fresh) {
    $dir = Join-Path $base $dirName
    if ($fresh) {
        New-Item -ItemType Directory -Force $dir | Out-Null
        Set-Content -Encoding ascii (Join-Path $dir 'eula.txt') 'eula=true'
        Set-Content -Encoding ascii (Join-Path $dir 'server.properties') $serverProperties
    }
    $props = @("phase=$phase", "result=selftest-result-$phase.txt")
    foreach ($k in $extra.Keys) { $props += "$k=$($extra[$k] -replace '\\','/')" }
    Set-Content -Encoding ascii (Join-Path $dir 'extendedheight-selftest.properties') ($props -join "`n")
    $marker = Join-Path $dir 'extendedheight-disable-mixins'
    if ($disableMixins) { Set-Content -Encoding ascii $marker '' } elseif (Test-Path $marker) { Remove-Item $marker }

    Write-Host "=== phase $phase ($dirName)"
    $relDir = "run/selftest/$dirName"
    $log = Join-Path $base "$phase.gradle.log"
    $previousDir = [Environment]::CurrentDirectory
    $previousPref = $ErrorActionPreference
    try {
        # Gradle ищет проект по рабочему каталогу процесса
        [Environment]::CurrentDirectory = $root
        # В PowerShell 5.1 stderr нативных команд становится ошибками — скрипт не должен на них падать
        $ErrorActionPreference = 'Continue'
        & (Join-Path $root 'gradlew.bat') --no-daemon runServer "-PrunServerWorkingDirectory=$relDir" 2>&1 |
            ForEach-Object { "$_" } | Out-File -Encoding utf8 $log
        $gradleExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPref
        [Environment]::CurrentDirectory = $previousDir
    }

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

$snapBaseline = Join-Path $base 'snapshot-baseline'
$results = [ordered]@{}
$results['baseline'] = Invoke-Phase 'baseline' 'baseline' @{ snapshot = $snapBaseline } $true $true
$results['vanilla-mod'] = Invoke-Phase 'vanillamod' 'vanilla-mod' @{ snapshot = (Join-Path $base 'snapshot-vanillamod'); compareWith = $snapBaseline } $false $true
$results['write'] = Invoke-Phase 'extended' 'write' @{ snapshot = (Join-Path $base 'snapshot-extended'); compareWith = $snapBaseline } $false $true
$results['verify'] = Invoke-Phase 'extended' 'verify' @{} $false $false

Write-Host ''
$allOk = $true
foreach ($k in $results.Keys) {
    $state = if ($results[$k]) { 'PASS' } else { 'FAIL'; $allOk = $false }
    Write-Host ("{0,-12} {1}" -f $k, $state)
}
if (-not $allOk) { exit 1 }
