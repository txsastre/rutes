# Rutes: navegador GPS per a Android

Navegador GPS lliure, sense cap dependència de Google. Funciona en Android sense Google (LineageOS, GrapheneOS, /e/OS…).

## Què fa (v0.1)

- Mapa d'OpenStreetMap amb la teva posició en temps real
- Cerca de llocs, o tria del destí mantenint premut el mapa
- Rutes en cotxe, bici o a peu, amb la durada, la distància i l'hora d'arribada
- Navegació pas a pas: indicacions en pantalla i per veu en català
- Recàlcul automàtic de la ruta si et desvies
- La pantalla no s'apaga mentre navegues

## Sense Google

| Peça | Què fa servir |
|---|---|
| GPS | `LocationManager` d'Android (no Google Play Services) |
| Mapa | [MapLibre](https://maplibre.org) + estil d'[OpenFreeMap](https://openfreemap.org) |
| Cerca | [Photon](https://photon.komoot.io) (OpenStreetMap) |
| Rutes | [OSRM](https://routing.openstreetmap.de) (OpenStreetMap) |
| Veu | El motor TTS del sistema. Sense Google, instal·la **RHVoice** o **eSpeak NG** des d'F-Droid |

Les llibreries AndroidX/Compose són de codi obert i van dins l'APK; no necessiten serveis de Google al mòbil.

Els servidors públics de cerca i de rutes són gratuïts però tenen límits d'ús. Per a un ús intensiu pots muntar-ne un de propi (OSRM o GraphHopper amb Docker) i canviar les URL a `data/Api.kt`.

## Compilar sense Android Studio (VSCodium / Zed + terminal)

Cal:
1. **JDK 17** (per exemple `sudo apt install openjdk-17-jdk`)
2. **Android SDK command-line tools**: descarrega'ls de https://developer.android.com/studio#command-line-tools-only i descomprimeix-los a `~/Android/Sdk/cmdline-tools/latest/`

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

```bash
yes | sdkmanager --licenses
```

Compila i instal·la al mòbil (amb la depuració USB activada):

```bash
./gradlew assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tests de la lògica de navegació (no cal mòbil):

```bash
./gradlew testDebugUnitTest
```

Per veure els errors de l'app mentre la proves:

```bash
adb logcat --pid=$(adb shell pidof cat.rutes.navegador)
```

## Estructura

```
app/src/main/java/cat/rutes/navegador/
├── MainActivity.kt          Entrada de l'app i permisos
├── data/
│   ├── Models.kt            LatLon, Place, Route, RouteStep, Profile
│   └── Api.kt               Cerca (Photon) i rutes (OSRM)
├── location/
│   └── LocationProvider.kt  GPS amb LocationManager
├── nav/
│   ├── NavigationEngine.kt  Segueix la ruta, propera maniobra, desviaments, avisos de veu
│   ├── Instructions.kt      Maniobres → text en català
│   ├── Format.kt            Distàncies, temps, hora d'arribada
│   ├── Geo.kt               Càlculs geogràfics
│   └── Speaker.kt           Veu (TextToSpeech)
└── ui/
    ├── MainViewModel.kt     Estat de l'app
    ├── MapController.kt     Mapa MapLibre: ruta, destinació, càmera
    └── Screens.kt           Pantalles (Jetpack Compose)
```

## Idees per a properes versions

- Servei en primer pla per continuar navegant amb la pantalla apagada
- Gravar recorreguts i exportar-los en GPX
- Mapes sense connexió (fitxers PMTiles o MBTiles)
- Llocs desats i historial de cerques
- Evitar autopistes o peatges
