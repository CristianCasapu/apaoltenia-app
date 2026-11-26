# Configurare Build Automat + Release ApaOltenia Client

Release-urile oficiale (APK semnat + GitHub Release) se fac **pe GitHub**,
prin `.github/workflows/release.yml`. Build-ul local cu `release.ps1` ramane
disponibil, dar doar compileaza — nu publica nimic.

Pasii 1–2 se fac **o singura data**.

---

## PASUL 1 — Genereaza keystore-ul de semnare

Ruleaza in PowerShell:

```powershell
keytool -genkey -v `
  -keystore apaoltenia.jks `
  -alias apaoltenia `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

La intrebari poti completa:
- First and last name: `Cristian Casapu`
- Organization: `cristiancasapu.ro`
- Country code: `RO`
- Alege o parola puternica pentru keystore SI pentru key.

**IMPORTANT:** Salveaza `apaoltenia.jks` intr-un loc sigur (cloud personal,
NU in repo!). Daca il pierzi, nu mai poti publica actualizari semnate cu
aceeasi identitate — utilizatorii ar trebui sa dezinstaleze aplicatia.

---

## PASUL 2 — Adauga Secrets in GitHub

Mergi la: **GitHub → repo apaoltenia-app → Settings → Secrets and variables → Actions**

Adauga 4 secrets:

### KEYSTORE_BASE64
Encodeaza keystore-ul in Base64 si copiaza in clipboard:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\calea\catre\apaoltenia.jks")) | Set-Clipboard
```

Lipeste continutul clipboard-ului ca valoare a secretului.

### KEYSTORE_PASSWORD
Parola aleasa pentru keystore.

### KEY_ALIAS
`apaoltenia`

### KEY_PASSWORD
Parola aleasa pentru key (poate fi aceeasi cu KEYSTORE_PASSWORD).

---

## Publicarea unei versiuni noi

1. In `app/build.gradle.kts` mareste `versionName` (ex. `1.10.0`) si
   `versionCode` (+1).
2. In `app/src/main/java/ro/apaoltenia/client/Changelog.kt` adauga intrarea
   pentru aceeasi versiune, prima in lista — din ea se genereaza notele
   release-ului.
3. Fa merge pe `main` (PR sau push direct).

GitHub Actions observa ca pentru `versionName` nu exista inca tag-ul `vX.Y.Z`
si, automat:
1. Verifica concordanta versiune ↔ `Changelog.kt`
2. Instaleaza JDK 17 + Gradle si decodeaza keystore-ul din secret
3. Compileaza APK-ul semnat
4. Creeaza tag-ul si **GitHub Release**-ul cu APK-ul atasat

Un push pe `main` care nu schimba versiunea nu publica nimic.

Urmareste progresul la **GitHub → repo → Actions**.

### Alternative

- Declansare manuala (versiunea e luata din Gradle):
  ```powershell
  gh workflow run release.yml --ref main
  ```
- Push de tag: `git tag v1.10.0 && git push origin v1.10.0`

Daca tag-ul versiunii exista deja, rularea manuala se opreste cu eroare — un
release publicat nu e suprascris niciodata cu un APK din alt commit.

Utilizatorii vad versiunea noua din **Setari → Verifica actualizari**
(aplicatia interogheaza GitHub Releases al acestui repo — public, deci fara
niciun token in cod).

---

## Build local optional

Pentru test pe masina ta: copiaza `keystore.properties.example` in
`keystore.properties`, completeaza parolele, apoi:

```powershell
.\release.ps1 -Version 1.10.0   # APK semnat in dist\
.\release.ps1 -Debug            # APK de debug
```

Scriptul nu creeaza tag-uri si nu publica release-uri.

---

## Fisiere care NU trebuie sa ajunga in git

Deja acoperite de `.gitignore`:
```
keystore.properties
*.jks
*.keystore
*.apk
*.aab
```
