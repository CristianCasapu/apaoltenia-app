# ============================================================
#  ApaOltenia Client - Compilare + Release APK (PowerShell)
#  Autor: Cristian Casapu | cristiancasapu.ro
# ============================================================
#
#  Utilizare:
#    .\release.ps1 -Version 1.0.1              # build semnat + publica release pe GitHub
#    .\release.ps1 -Version 1.0.1 -NoPublish   # doar compileaza APK-ul, fara release
#    .\release.ps1 -Debug                      # APK de debug (rapid, pentru test)
#    .\release.ps1 -Version 1.0.1 -Clean       # cu ./gradlew clean inainte
#
#  Publicarea creeaza release-ul prin API-ul GitHub (creeaza tag-ul direct pe
#  GitHub), deci NU declanseaza workflow-ul .github/workflows/release.yml —
#  eviti astfel o dubla compilare. Foloseste ACEST script SAU push de tag.
# ============================================================

param(
    [string]$Version,
    [switch]$Debug,
    [switch]$NoPublish,
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

# ── 4. Verificare semnare pentru publicare ──────────────────────────────────
$signed = Test-Path (Join-Path $root "keystore.properties")
if (-not $Debug -and -not $signed) {
    if (-not $NoPublish) {
        Fail "Lipseste keystore.properties, deci APK-ul de release ar fi NESEMNAT (neinstalabil). Configureaza semnarea (vezi GITHUB_RELEASE_SETUP.md) sau ruleaza cu -NoPublish / -Debug."
    }
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

# ── 7. Publicare release pe GitHub ──────────────────────────────────────────
if ($NoPublish -or $Debug) {
    if ($Debug) { Info "Build de debug — fara publicare." }
    else { Info "-NoPublish setat — APK compilat, fara release." }
    explorer $distDir
    exit 0
}

$tag = "v$currentName"
Info "[2/2] Publicare release $tag pe GitHub ($repo)..."

# Commit + push al bump-ului de versiune (push de BRANCH, nu de tag — nu declanseaza CI).
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
git add app/build.gradle.kts 2>$null
if ((git status --porcelain app/build.gradle.kts).Trim()) {
    git commit -q -m "chore: release $tag"
    Ok "Commit versiune creat."
}
Info "Push branch '$branch'..."
git push -q origin $branch
if ($LASTEXITCODE -ne 0) { Fail "Push esuat. Verifica accesul la GitHub." }

$notes = @"
## ApaOltenia Client $tag

### Instalare manuala
1. Descarca ``ApaOltenia-v$currentName.apk`` de mai jos
2. Pe telefon: **Setari -> Securitate -> Instalare aplicatii necunoscute**
3. Deschide fisierul si instaleaza

> Din aplicatie: **Setari -> Verifica actualizari** te anunta la versiuni noi.
"@

# Preferam gh CLI daca e autentificat; altfel folosim API-ul GitHub cu tokenul din git.
$ghOk = $false
if (Get-Command gh -ErrorAction SilentlyContinue) {
    gh auth status 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $ghOk = $true }
}

if ($ghOk) {
    gh release create $tag $apkOut --repo $repo --target $branch --title "ApaOltenia Client $tag" --notes $notes
    if ($LASTEXITCODE -ne 0) { Fail "gh release create a esuat." }
    Ok "Release publicat prin gh."
} else {
    Info "gh indisponibil/neautentificat — folosesc API-ul GitHub cu tokenul din Git Credential Manager."
    $cred = "protocol=https`nhost=github.com`n`n" | git credential fill
    $token = ($cred | Select-String '^password=(.*)$').Matches.Groups[1].Value
    if (-not $token) { Fail "Nu am putut obtine un token GitHub. Autentifica-te (ex. 'gh auth login' sau un push care sa salveze credentialele)." }

    $headers = @{ Authorization = "Bearer $token"; "Accept" = "application/vnd.github+json"; "User-Agent" = "apaoltenia-release" }
    $body = @{ tag_name = $tag; target_commitish = $branch; name = "ApaOltenia Client $tag"; body = $notes; draft = $false; prerelease = $false } | ConvertTo-Json
    try {
        $rel = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Body $body -ContentType "application/json"
    } catch {
        Fail "Crearea release-ului a esuat: $($_.Exception.Message). Poate exista deja tag-ul $tag."
    }
    Ok "Release $tag creat (id $($rel.id))."

    $uploadUrl = "https://uploads.github.com/repos/$repo/releases/$($rel.id)/assets?name=ApaOltenia-v$currentName.apk"
    $uploadHeaders = @{ Authorization = "Bearer $token"; "Accept" = "application/vnd.github+json"; "User-Agent" = "apaoltenia-release" }
    Info "Incarc APK-ul ca asset..."
    Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $uploadHeaders -InFile $apkOut -ContentType "application/vnd.android.package-archive" | Out-Null
    Ok "APK incarcat pe release."
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  RELEASE PUBLICAT: https://github.com/$repo/releases/tag/$tag" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
explorer $distDir
