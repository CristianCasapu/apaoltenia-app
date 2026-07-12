# Configurare Build Automat + Release ApaOltenia Client

Urmareste pasii de mai jos O SINGURA DATA. Dupa configurare, un simplu
`git tag v1.x.y && git push origin v1.x.y` declanseaza build-ul automat pe
GitHub si publica release-ul cu APK-ul semnat.

---

## PASUL 1 — Genereaza keystore-ul de semnare

Ruleaza in PowerShell (din radacina proiectului):

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
aceeasi identitate.

---

## PASUL 2 — Build local semnat (optional)

Copiaza `keystore.properties.example` in `keystore.properties` si completeaza
parolele. Fisierul este deja in `.gitignore`. Apoi:

```powershell
./gradlew assembleRelease
```

APK-ul rezulta in `app/build/outputs/apk/release/app-release.apk`.

Fara `keystore.properties`, build-ul de release ramane nesemnat (util doar
pentru testare); pentru distributie foloseste semnarea.

---

## PASUL 3 — Adauga Secrets in GitHub

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

## Alternativa rapida — `release.ps1` (build local + release local)

Daca vrei sa compilezi pe masina ta si sa publici imediat APK-ul local (fara
sa astepti GitHub Actions), foloseste scriptul:

```powershell
.\release.ps1 -Version 1.0.1
```

Cerinte: `keystore.properties` completat (PASUL 2) si acces la GitHub (git deja
autentificat sau `gh auth login`). Scriptul urca versiunea, compileaza APK-ul
semnat, il pune in `dist\` si creeaza release-ul. Creeaza tag-ul prin API, deci
**nu declanseaza si workflow-ul** — nu ai build dublu.

Pentru Secrets din GitHub Actions (PASUL 3) ai nevoie DOAR daca preferi calea
prin push de tag. Cele doua cai sunt independente.

---

## PASUL 4 — Primul release (prin GitHub Actions)

1. Verifica versiunea in `app/build.gradle.kts` (`versionName` / `versionCode`).
2. Publica:

```powershell
git add .
git commit -m "chore: pregatire release"
git tag v1.0.0
git push origin master
git push origin v1.0.0
```

GitHub Actions va:
1. Detecta tag-ul `v1.0.0`
2. Instala JDK 17 + Gradle
3. Decodifica keystore-ul din secret
4. Compila APK-ul semnat
5. Crea automat un **GitHub Release** cu APK-ul atasat

Urmareste progresul la **GitHub → repo → Actions**.

---

## Flux pentru versiuni viitoare

```powershell
# 1. Incrementeaza versiunea in app/build.gradle.kts
#    versionName = "1.1.0"  ->  versionCode = 2
# 2. Commit
git commit -am "feat: descriere functii noi"
# 3. Tag + push
git tag v1.1.0
git push origin master
git push origin v1.1.0
```

Utilizatorii vad versiunea noua din **Setari → Verifica actualizari**
(aplicatia interogheaza GitHub Releases al acestui repo — public, deci fara
niciun token in cod).

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
