<#
.SYNOPSIS
    Build wrapper that serializes Gradle invocations.

.DESCRIPTION
    Hai agent pane (Codex + Qwen) cung chay mot Gradle project trong cung mot
    worktree. Gradle khong cong chiu giua cac build dong thoi: chung tranh
    `GradleDaemon` port va cac file khoa trong `app/build/`. Wrapper nay dung
    mot named mutex toan may de xep hang cac lenh build.

    Tu dong tao `subst R:` tro toi thu muc co dau tieng Viet, vi AGP/gradlew.bat
    khong xu ly duoc duong dan non-ASCII (xem README cua project).

.EXAMPLE
    .\build.ps1 :app:assembleDebug
    .\build.ps1 :app:testDebugUnitTest
    .\build.ps1 check -q
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = $null

# $PSScriptRoot rong khi script duoc invoke khong qua file path chuan (vi du
# `& .\build.ps1`), nen tim project bang cach quet len tu PWD truoc.
$searchRoots = @()
if ($PSScriptRoot) { $searchRoots += $PSScriptRoot }
$searchRoots += $PWD.Path
$searchRoots += (Get-Location).Path

foreach ($root in ($searchRoots | Select-Object -Unique)) {
    if (-not $root) { continue }
    $candidate = Join-Path $root 'AI Study'
    if (Test-Path -LiteralPath (Join-Path $candidate 'settings.gradle.kts')) {
        $ProjectRoot = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
    if (Test-Path -LiteralPath (Join-Path $root 'settings.gradle.kts')) {
        $ProjectRoot = (Resolve-Path -LiteralPath $root).Path
        break
    }
}

if (-not $ProjectRoot) {
    throw "Khong tim thay Gradle project. Da thu: $($searchRoots -join ', ')"
}

# ---- 1. Dam bao R: tro dung project (AGP chan duong dan co dau) ----
$substTarget = (cmd /c subst 2>$null) | Where-Object { $_ -match '^R:\\:' }
if ($substTarget -and $substTarget -notlike "*$ProjectRoot*") {
    cmd /c 'subst R: /d' | Out-Null
    $substTarget = $null
}
if (-not $substTarget) {
    cmd /c "subst R: `"$ProjectRoot`"" | Out-Null
}
$driveReady = Test-Path 'R:\settings.gradle.kts'
if (-not $driveReady) { throw "Khong mount duoc R: tro toi $ProjectRoot" }

# ---- 2. Xep hang: named mutex toan may ----
$mutex = New-Object System.Threading.Mutex($false, 'Global\AIStudyGradleBuild')
$held = $false
try {
    try {
        $held = $mutex.WaitOne(0)
    } catch [System.Threading.AbandonedMutexException] {
        $held = $true
    }
    if (-not $held) {
        Write-Host '[build] Co build khac dang chay - cho...'
        $held = $mutex.WaitOne()
    }

    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    if (-not $GradleArgs -or $GradleArgs.Count -eq 0) { $GradleArgs = @(':app:assembleDebug') }

    # Gradle bat FileLockContentionHandler tren mot co UDP. Agent nao chay
    # gradlew.bat truc tiep (khong qua wrapper nay) se khong nam trong mutex, va
    # hai build cung luc se dau nhau "Address already in use". Retry la lua duoi
    # thay vi bao loi ngay cho nguoi dung.
    $attempts = 0
    $maxAttempts = 4
    while ($true) {
        $attempts++
        Write-Host "[build] R: $GradleArgs (lan $attempts/$maxAttempts)"
        $output = & R:\gradlew.bat -p R:\ @GradleArgs 2>&1
        $code = $LASTEXITCODE
        $text = ($output | Out-String)

        $portClash = $text -match 'Address already in use'
        if (-not $portClash -or $attempts -ge $maxAttempts) {
            $output | ForEach-Object { Write-Host $_ }
            break
        }

        Write-Warning 'Gradle daemon port dang bi tranh - cho 15s roi thu lai.'
        Start-Sleep -Seconds 15
    }
} finally {
    if ($held) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}

exit $code
