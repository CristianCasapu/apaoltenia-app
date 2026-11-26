# ApaOltenia Client (Android)

Aplicatie Android **neoficiala** care incapsuleaza portalul
`https://clienti.apaoltenia.ro/self_utilities/` intr-un WebView si
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
   amprenta / fata / model / PIN. Dupa deblocare, **incarca direct aplicatia
   portalului**: daca sesiunea ta e inca valida, intri **fara login si fara
   Cloudflare**. Doar daca sesiunea a expirat portalul revine la pagina de
   login — atunci aplicatia **completeaza automat** emailul si parola. Cu
   **Autentificare automata** activa (implicit), trimite si formularul imediat
   ce verificarea Cloudflare e trecuta — adesea complet automat. Daca preferi
   sa apesi tu Login, dezactiveaza optiunea din **Setari**.
4. **Daca iti schimbi parola in portal** – la urmatoarea logare manuala
   aplicatia detecteaza ca datele difera si iti propune sa le actualizeze.

## Functii

- 🚪 **Acces direct cand esti deja autentificat**: la pornire aplicatia incarca
  direct pagina interna a portalului; daca sesiunea e valabila intri imediat,
  fara login si fara Cloudflare. Doar cand sesiunea a expirat se afiseaza login.
- 💾 **Sesiune pastrata cat mai mult**: cookie-urile de sesiune sunt scrise pe
  disc la trecerea in fundal si dupa fiecare pagina, ca sesiunea sa
  supravietuiasca inchiderii aplicatiei sau uciderii procesului de catre sistem.
- 🔒 **Deblocare biometrica** inainte de auto-login.
- ⚡ **Autentificare automata** (optionala, din Setari): dupa deblocare, aplicatia
  completeaza datele si trimite formularul imediat ce verificarea Cloudflare este
  trecuta — adesea complet automat, cu o singura atingere biometrica. Nu ocoleste
  verificarea anti-bot.
- 🎨 **Bara de sus brandata**, coerenta cu antetul portalului, in tema clara si
  intunecata.
- 🧭 **Meniu lateral restilizat** in ambele teme (carduri rotunjite, text si
  iconite clare, element selectat evidentiat brand).
- ♿ **Meniu de accesibilitate in navigatie**: optiunea a fost mutata din butonul
  flotant intr-un element propriu, dupa "Stergere cont".
- 🛡️ **Stergere cont protejata cu parola**: contul nu poate fi sters decat dupa
  ce introduci parola contului in aplicatie si confirmi.
- ✉️ **Pagina de contact simplificata**: editorul de mesaj a fost degajat de bara
  de instrumente stufoasa, cu o zona de scriere curata, adaptata temei.
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
- 🕑 **Istoric versiuni in aplicatie**: **Setari → Istoric versiuni** arata pe
  scurt evolutia aplicatiei sub forma de linie a timpului.

## Notificari pentru facturi noi — cum si limitele

Portalul **nu ofera API public si nu ofera push**, iar autentificarea este
protejata de **Cloudflare Turnstile** (anti-bot). De aceea nu exista o cale de
a re-loga complet automat in fundal. Solutia implementata:

- Cat timp **sesiunea ramane activa**, aplicatia verifica periodic portalul
  (aprox. o data la 6 ore, prin `WorkManager`), reutilizand cookie-urile
  sesiunii tale.
- Verificarea foloseste **API-ul intern al portalului** (cel apelat de propria
  lui interfata, nedocumentat): o singura cerere JSON cu facturile neachitate.
  Pastreaza doar **hash-uri** ale identificatorilor de factura — fara numere de
  factura sau sume — si te anunta cu o **notificare locala** cand apare o
  factura noua, cu valoarea ei.
- Daca API-ul raspunde neasteptat (de ex. s-a schimbat), aplicatia revine
  automat la metoda veche: incarca portalul intr-un WebView invizibil si
  compara o **amprenta** (hash) a sumelor/facturilor vizibile.
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

Release-urile oficiale se compileaza si se publica **pe GitHub** — vezi
[`GITHUB_RELEASE_SETUP.md`](GITHUB_RELEASE_SETUP.md). Build-ul local
(`release.ps1`, mai jos) e optional, doar pentru test.

### Publicarea unui release

Pasii pentru o versiune noua:

1. Mareste `versionName` si `versionCode` in `app/build.gradle.kts`.
2. Adauga intrarea noua (aceeasi versiune) la inceputul listei din `Changelog.kt`.
3. Fa merge pe `main` — **release-ul se publica automat**: workflow-ul vede ca
   pentru `versionName` nu exista inca tag-ul `vX.Y.Z`, compileaza APK-ul
   semnat, creeaza tag-ul si release-ul cu notele din `Changelog.kt`.

Alternative: push de tag (`git tag vX.Y.Z && git push origin vX.Y.Z`) sau
declansare manuala (`gh workflow run release.yml --ref main`; versiunea e luata
din Gradle). Daca tag-ul exista deja, rularea manuala se opreste cu eroare, ca
sa nu se suprascrie un release existent cu un APK din alt commit.

### Build local optional (`release.ps1`)

Doar compileaza, nu publica nimic (Windows / PowerShell). Pentru APK semnat
local ai nevoie de `keystore.properties` (copiat din `keystore.properties.example`).

```powershell
.\release.ps1 -Version 1.0.1            # APK semnat in dist\ApaOltenia-v1.0.1.apk
.\release.ps1 -Version 1.0.1 -Clean     # cu ./gradlew clean inainte
.\release.ps1 -Debug                    # APK de debug, rapid, pentru test
```

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
│   ├── PortalApi.kt              # cererea JSON catre API-ul intern al portalului
│   ├── InvoiceCheckScheduler.kt  # pornire/oprire verificare periodica
│   ├── NotificationHelper.kt     # notificarea de factura noua
│   └── UpdateChecker.kt          # verificare versiuni via GitHub Releases
├── assets/portal_style.css       # stilul custom injectat peste portal
├── res/…                         # layout, teme day/night, iconite, texte
└── AndroidManifest.xml
.github/workflows/release.yml     # build + release automat (versiune noua pe main / tag)
```

## Cerinte minime

- minSdk 30 (Android 11+). Optimizat pentru Xiaomi Redmi Note 13 Pro.
- O metoda de deblocare configurata pe telefon.

## Creator

**Cristian Casapu** · [cristiancasapu.ro](https://cristiancasapu.ro) ·
[contact@cristiancasapu.ro](mailto:contact@cristiancasapu.ro)
