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
- 🌗 **Temă luminoasă / întunecată / ca sistemul**, comutabila din **Setari**
  si aplicata inclusiv paginii web (algorithmic darkening).
- 🔄 **Pull-to-refresh** si bara de progres la incarcare.
- 🔔 **Notificari facturi noi** (best-effort — vezi mai jos).
- ⬆️ **Verificare actualizari** din aplicatie (Setari → Verifica actualizari),
  interogand GitHub Releases al acestui repo.

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
completeaza parolele si ruleaza `./gradlew assembleRelease`. Pe GitHub,
un tag `vX.Y.Z` declanseaza build-ul si publica automat release-ul.

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
