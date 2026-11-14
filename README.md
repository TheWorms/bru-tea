# Infusion

**Français** · [English](#english)

**Infusion** est une app Android **non officielle** pour piloter en Bluetooth la machine à thé
**BRU Maker One**. Elle n'est ni écrite, ni soutenue, ni approuvée par BRU ; « BRU » et « Maker One »
appartiennent à leurs propriétaires.

## Ce qu'elle fait

- Accueil : la machine (état, demandes de la machine en clignotant, « Refaire », mode manuel), les favoris.
- Thés : sa propre liste (photo, réglages, notes), avec suggestions et fiches reprises d'une base publique
  de thés (dépôt [TheWorms/tea-db](https://github.com/TheWorms/tea-db), voir `docs/base-thes.md`).
- Infusion depuis le téléphone, suivie à l'écran et en notification, y compris app fermée.
- Machine : jusqu'à 10 thés synchronisés sur la machine (seulement sur demande), nom de la machine, langue
  (français, anglais) et unités, heure.
- Pas de mise à jour du firmware de la machine : elle reste réservée à l'app officielle.

Le protocole Bluetooth de la machine est implémenté dans `app/src/main/java/fr/theworms/infusion/ble/`.

## Construire

Android SDK (compileSdk 35) et JDK 17 à 21 :

```bash
./gradlew assembleDebug          # APK de débogage
./gradlew testDebugUnitTest      # tests JVM (Robolectric)
```

`app/build/captures/` reçoit, après les tests, des captures des écrans principaux en français et en anglais.

Une construction signée (`assembleRelease`) lit sa clé dans un fichier de propriétés hors du dépôt
(`infusion.releaseSigning`) ; sans lui, l'APK de version n'est pas signée.

## Données

Tout reste sur le téléphone (base Room, réglages DataStore, photos dans les fichiers privés de l'app).
L'app ne lit en réseau que la base de thés publique et son propre canal de mises à jour.

## Licence

MIT — voir [LICENSE](LICENSE).

<details id="english">
<summary><b>English</b></summary>

**Infusion** is an **unofficial** Android app to control the **BRU Maker One** tea machine over Bluetooth.
It is not written, supported or endorsed by BRU; “BRU” and “Maker One” belong to their owners.

## What it does

- Home: the machine (status, blinking machine requests, “Brew again”, manual mode) and your favourites.
- Teas: your own list (photo, settings, notes), with suggestions and sheets taken from a public tea
  database (repository [TheWorms/tea-db](https://github.com/TheWorms/tea-db), see `docs/base-thes.md`).
- Brewing from the phone, followed on screen and in a notification, even with the app closed.
- Machine: up to 10 teas synced to the machine (only on request), machine name, language
  (French, English) and units, clock.
- No machine firmware updates: those remain with the official app.

The machine's Bluetooth protocol is implemented in `app/src/main/java/fr/theworms/infusion/ble/`.

## Build

Android SDK (compileSdk 35) and JDK 17 to 21:

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM tests (Robolectric)
```

After the tests, `app/build/captures/` holds screenshots of the main screens in French and English.

A signed build (`assembleRelease`) reads its key from a properties file outside the repository
(`infusion.releaseSigning`); without it, the release APK is unsigned.

## Data

Everything stays on the phone (Room database, DataStore settings, photos in the app's private files).
The app only goes online to read the public tea database and its own update channel.

## Licence

MIT — see [LICENSE](LICENSE).

</details>
