# ApaOltenia Client (Android)

Aplicatie Android **neoficiala** care incapsuleaza portalul
`https://clienti.apaoltenia.ro/self_utilities/login.jsp` intr-un WebView si
adauga **deblocare cu metoda dispozitivului** (amprenta, fata, model sau PIN),
**temă luminoasă/întunecată** si **notificari pentru facturi noi**.

Nu are nicio legatura cu S.C. Compania de Apa Oltenia S.A. Toate datele raman
pe dispozitivul tau.

## Cum functioneaza

1. **Prima deschidere** – se incarca pagina de login. Te autentifici manual o
   singura data cu emailul si parola ta (portalul cere si o verificare
   Cloudflare Turnstile).
2. La trimiterea formularului, aplicatia iti propune sa salveze datele. Daca
   accepti, emailul si parola sunt **criptate pe dispozitiv** (AES-256-GCM,
   cheie generata si pastrata in Android Keystore, hardware-backed — cheia nu
   poate fi extrasa din telefon).
3. **La urmatoarele deschideri** – aplicatia cere deblocarea prin
   amprenta / fata / model / PIN. Dupa deblocare, **completeaza automat**
   emailul si parola; tu doar bifezi verificarea Turnstile si apesi Login.
4. **Daca iti schimbi parola in portal** – la urmatoarea logare manuala
   aplicatia detecteaza ca datele difera si iti propune sa le actualizeze.

## Functii

- 🔒 **Deblocare biometrica** inainte de auto-login.
- 🌗 **Temă luminoasă / întunecată / ca sistemul**, comutabila din **Setari**.
  Tema intunecata este **desenata manual** printr-o foaie de stil proprie
  (nu algorithmic darkening), cu culori de brand pe toate ecranele portalului.
- 🎨 **Stil custom peste portal**: aplicatia injecteaza o foaie de stil proprie
  (culori de brand, carduri rotunjite, umbre discrete, fara logo/fundal implicit)
  peste pagina de login si peste aplicatia OpenUI5 a portalului, pentru un aspect
  modern, coerent si optimizat pentru telefoane.
- 📊 **Grafic de consum lizibil pe mobil**: graficul Chart.js al portalului este
  redimensionat automat (inaltime utila, etichete rotite) ca sa nu mai apara
  ingramadit.
- 🔄 **Pull-to-refresh** si bara de progres la incarcare.
- 🔔 **Notificari facturi noi** (best-effort — vezi mai jos).
- ⬆️ **Actualizare complet in aplicatie**: verificare automata la pornire si din
  **Setari → Verifica actualizari**; APK-ul se **descarca si se instaleaza direct
  din aplicatie**, fara redirectionare catre GitHub.

## Notificari pentru facturi noi — cum si limitele

Portalul **nu ofera API public si nu ofera push**, iar autentificarea este
protejata de **Cloudflare Turnstile** (anti-bot). De aceea nu exista o cale de
a re-loga complet automat in fundal. Solutia implementata:

- Cat timp **sesiunea ramane activa**, aplicatia verifica periodic portalul
  (aprox. o data la 6 ore, prin `WorkManager`), reutilizand cookie-urile
  sesiunii tale intr-un WebView invizibil.
- Extrage o **amprenta** (hash) a sumelor/facturilor vizibile — fara a stoca
  continut sensibil — si o compara cu ultima observata. La o schimbare, iti
  trimite o **notificare locala**.
- Daca sesiunea a expirat (portalul cere din nou login + Turnstile),
  verificarea se opreste silentios pana te autentifici manual din nou.

> Pentru notificare **garantata** la fiecare factura, activeaza si
> **„Factura pe email"** din contul tau de pe portal.

## Securitate

- Credentialele: **AES-256-GCM** cu Android Keystore (fara chei in fisiere,
  fara biblioteci deprecate). Parola nu apare niciodata in codul sursa.
- WebView blindat: acces la fisiere local dezactivat, fara continut mixt
  (`MIXED_CONTENT_NEVER_ALLOW`), fara cookie-uri third-party, Safe Browsing
  activat cand e disponibil.
- Navigarea in aplicatie e restrictionata strict la domeniul `apaoltenia.ro`
  (verificare exacta de host); linkurile externe se deschid in browser doar
  pentru scheme `http`/`https` (schemele `intent:`/`file:` etc. sunt refuzate).
- `allowBackup="false"`, trafic exclusiv HTTPS.
- Verificarea actualizarilor foloseste un **repo public**, deci nu exista
  niciun token in cod.

## Compilare

Ai nevoie de **Android Studio (Koala sau mai nou)** sau de Android SDK.
Wrapper-ul Gradle este inclus.

```bash
# APK de debug:
./gradlew assembleDebug
# instalare pe dispozitiv conectat:
./gradlew installDebug
```

Pentru APK de release semnat, vezi [`GITHUB_RELEASE_SETUP.md`](GITHUB_RELEASE_SETUP.md).
Pe scurt: copiaza `keystore.properties.example` → `keystore.properties`,
completeaza parolele si ruleaza `./gradlew assembleRelease`.

### Release rapid dintr-o comanda (`release.ps1`)

Compileaza APK-ul semnat **si** publica release-ul cu APK-ul local, dintr-un
singur pas (Windows / PowerShell):

```powershell
.\release.ps1 -Version 1.0.1            # build semnat + release pe GitHub
.\release.ps1 -Version 1.0.1 -NoPublish # doar compileaza (dist\ApaOltenia-v1.0.1.apk)
.\release.ps1 -Debug                    # APK de debug, rapid, pentru test
```

Scriptul urca `versionName`/`versionCode`, compileaza, copiaza APK-ul in `dist\`
si creeaza release-ul prin API-ul GitHub. **Doua cai separate**, alege una:

- **`release.ps1`** — build local rapid, publica APK-ul de pe masina ta.
- **Push de tag** (`git tag vX.Y.Z && git push origin vX.Y.Z`) — build in cloud
  prin GitHub Actions.

(`release.ps1` creeaza tag-ul prin API, deci NU declanseaza si workflow-ul —
nu vei avea build dublu.)

## Structura

```
app/src/main/
├── java/ro/apaoltenia/client/
│   ├── App.kt                    # Application: tema + canal notificari
│   ├── MainActivity.kt           # WebView + biometric + auto-login + toolbar
│   ├── SettingsActivity.kt       # tema, notificari, actualizari, date
│   ├── AppPreferences.kt         # preferinte (tema, notificari) locale
│   ├── CredentialStore.kt        # criptare AES-256-GCM cu Android Keystore
│   ├── BiometricAuthenticator.kt # amprenta / fata / PIN / model
│   ├── WebAppInterface.kt        # captarea credentialelor la logare
│   ├── InvoiceCheckWorker.kt     # verificare facturi in fundal (WorkManager)
│   ├── InvoiceCheckScheduler.kt  # pornire/oprire verificare periodica
│   ├── NotificationHelper.kt     # notificarea de factura noua
│   └── UpdateChecker.kt          # verificare versiuni via GitHub Releases
├── assets/portal_style.css       # stilul custom injectat peste portal
├── res/…                         # layout, teme day/night, iconite, texte
└── AndroidManifest.xml
.github/workflows/release.yml     # build + release automat la tag
```

## Cerinte minime

- minSdk 30 (Android 11+). Optimizat pentru Xiaomi Redmi Note 13 Pro.
- O metoda de deblocare configurata pe telefon.

## Creator

**Cristian Casapu** · [cristiancasapu.ro](https://cristiancasapu.ro) ·
[contact@cristiancasapu.ro](mailto:contact@cristiancasapu.ro)
