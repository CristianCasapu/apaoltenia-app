# ============================================================
#  ApaOltenia Client - Compilare + Release APK (PowerShell)
#  Autor: Cristian Casapu | cristiancasapu.ro
# ============================================================
#
#  Utilizare (DOAR build local — publicarea se face din CI):
#    .\release.ps1 -Version 1.0.1              # compileaza APK-ul semnat
#    .\release.ps1 -Debug                      # APK de debug (rapid, pentru test)
#    .\release.ps1 -Version 1.0.1 -Clean       # cu ./gradlew clean inainte
#
#  PUBLICAREA nu se mai face din acest script. Release-ul oficial se produce
#  prin .github/workflows/release.yml (push de tag v* SAU declansare manuala
#  workflow_dispatch), care genereaza notele din Changelog.kt si aplica
#  gardurile tag<->versionName si changelog<->tag. Acest script doar
#  compileaza APK-ul local pentru test.
# ============================================================

param(
    [string]$Version,
    [switch]$Debug,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$repo = "CristianCasapu/apaoltenia-app"
$root = $PSScriptRoot
Set-Location $root

function Info($m)  { Write-Host $m -ForegroundColor Cyan }
function Ok($m)    { Write-Host "[OK] $m" -ForegroundColor Green }
function Warn($m)  { Write-Host "[ATENTIE] $m" -ForegroundColor Yellow }
function Fail($m)  { Write-Host "[EROARE] $m" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  ApaOltenia Client - Compilare + Release APK" -ForegroundColor Cyan
Write-Host "  Autor: Cristian Casapu | cristiancasapu.ro" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$gradleFile = Join-Path $root "app\build.gradle.kts"
if (-not (Test-Path $gradleFile)) { Fail "Nu gasesc app\build.gradle.kts. Ruleaza scriptul din radacina proiectului." }

# ── 1. Java (necesar pentru Gradle) ─────────────────────────────────────────
if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $env:JAVA_HOME) {
    $jbrCandidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "C:\Program Files\Java\jdk-17"
    )
    $jbr = $jbrCandidates | Where-Object { Test-Path (Join-Path $_ "bin\java.exe") } | Select-Object -First 1
    if ($jbr) {
        $env:JAVA_HOME = $jbr
        $env:PATH = "$jbr\bin;$env:PATH"
        Ok "Java gasit in $jbr"
    } else {
        Fail "Java (JDK 17) nu a fost gasit. Instaleaza Android Studio sau seteaza JAVA_HOME."
    }
} else {
    Ok "Java disponibil."
}

# ── 2. Android SDK (creeaza local.properties daca lipseste) ──────────────────
$localProps = Join-Path $root "local.properties"
if (-not (Test-Path $localProps) -and -not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    $sdkDefault = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $sdkDefault) {
        $sdkEsc = $sdkDefault -replace '\\', '\\'
        "sdk.dir=$sdkEsc" | Out-File -FilePath $localProps -Encoding ascii
        Ok "local.properties creat (sdk.dir=$sdkDefault)"
    } else {
        Fail "Android SDK negasit. Instaleaza Android Studio, apoi ruleaza din nou."
    }
} else {
    Ok "Android SDK configurat."
}

# ── 3. Versiune: patch versionName + bump versionCode ───────────────────────
$gradleText = Get-Content $gradleFile -Raw
$currentName = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$currentCode = [int][regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)').Groups[1].Value

if ($Version) {
    if ($Version -notmatch '^\d+\.\d+\.\d+$') { Fail "Versiune invalida: '$Version'. Format asteptat: X.Y.Z (ex. 1.0.1)." }
    if ($Version -ne $currentName) {
        $newCode = $currentCode + 1
        $gradleText = [regex]::Replace($gradleText, 'versionCode\s*=\s*\d+', "versionCode = $newCode")
        $gradleText = [regex]::Replace($gradleText, 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`"")
        # Scriere UTF-8 FARA BOM (Set-Content -Encoding utf8 din PS 5.1 ar adauga BOM).
        [System.IO.File]::WriteAllText($gradleFile, $gradleText, (New-Object System.Text.UTF8Encoding($false)))
        Ok "Versiune actualizata: $currentName -> $Version (versionCode $currentCode -> $newCode)"
        $currentName = $Version
    } else {
        Ok "Versiunea $Version este deja setata."
    }
} else {
    Info "Nu ai dat -Version; folosesc versiunea curenta: $currentName"
}

# ── 4. Verificare semnare ───────────────────────────────────────────────────
$signed = Test-Path (Join-Path $root "keystore.properties")
if (-not $Debug -and -not $signed) {
    Warn "keystore.properties lipseste — APK-ul de release va fi NESEMNAT (doar pentru testare)."
}

# ── 5. Compilare ────────────────────────────────────────────────────────────
$gradlew = Join-Path $root "gradlew.bat"
if ($Clean) {
    Info "`n[.] Curatare (gradlew clean)..."
    & $gradlew clean
    if ($LASTEXITCODE -ne 0) { Fail "Eroare la clean." }
}

if ($Debug) {
    Info "`n[1/2] Compilare APK DEBUG..."
    & $gradlew assembleDebug --console=plain
    $builtApk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
} else {
    Info "`n[1/2] Compilare APK RELEASE (poate dura cateva minute)..."
    & $gradlew assembleRelease --console=plain
    $signedApk   = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
    $unsignedApk = Join-Path $root "app\build\outputs\apk\release\app-release-unsigned.apk"
    $builtApk = if (Test-Path $signedApk) { $signedApk } else { $unsignedApk }
}
if ($LASTEXITCODE -ne 0) { Fail "Compilare esuata. Verifica erorile de mai sus." }
if (-not (Test-Path $builtApk)) { Fail "APK-ul nu a fost gasit dupa compilare ($builtApk)." }

# ── 6. Copiaza APK-ul in dist\ cu numele versiunii ──────────────────────────
$distDir = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$suffix = if ($Debug) { "-debug" } else { "" }
$apkOut = Join-Path $distDir "ApaOltenia-v$currentName$suffix.apk"
Copy-Item $builtApk $apkOut -Force

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  COMPILARE REUSITA!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  APK: $apkOut"
$sizeMb = [math]::Round((Get-Item $apkOut).Length / 1MB, 2)
Write-Host "  Marime: $sizeMb MB"
Write-Host ""

# ── 7. Gata (doar build local) ──────────────────────────────────────────────
# Publicarea release-ului se face din CI (.github/workflows/release.yml):
# push de tag v* SAU declansare manuala (workflow_dispatch). Astfel notele vin
# din Changelog.kt si se aplica gardurile de versiune.
if ($Debug) { Info "Build de debug — doar pentru test." }
else { Info "APK de release compilat local. Pentru publicare, foloseste workflow-ul de release (tag v* sau workflow_dispatch)." }
explorer $distDir
exit 0
