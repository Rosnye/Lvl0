# GymTracker

App Android para registrar entrenamientos de gimnasio con metas y puntaje, **sin servidores**:
los datos viven cifrados en el teléfono y el puntaje se comparte solo con amigos vinculados
en persona, mediante paquetes cifrados de extremo a extremo.

Documentos de diseño:

- [PLAN.md](PLAN.md): visión, funciones, arquitectura, tecnologías y fases.
- [docs/EVENTOS.md](docs/EVENTOS.md): esquema de eventos v1 (registro solo-anexar, firmado).
- [docs/PUNTAJE_V1.md](docs/PUNTAJE_V1.md): fórmula de puntaje v1.

## Módulos

```
domain/   Kotlin/JVM puro. Modelos, eventos, proyecciones y motor de puntaje. Sin Android.
crypto/   Kotlin/JVM puro. Identidad Ed25519/X25519, firma, cifrado de paquetes (BouncyCastle).
data/     Android. Room + SQLCipher, sellado de secretos con Android Keystore. Implementa EventLog.
sync/     Android. Identidad local, construcción/recepción de paquetes, transportes (Nearby, archivo).
app/      Android. UI en Jetpack Compose + Hilt.
```

Dependencias: `app → sync → data → domain`, `sync → crypto → domain`. Toda la lógica de negocio
es testeable en JVM sin emulador.

## Requisitos

- JDK 17 o superior (probado con 21).
- Android SDK con `platforms;android-37.0` y `build-tools;37.0.0`. Indicar la ruta en
  `local.properties` (`sdk.dir=...`) o con la variable `ANDROID_HOME`.
- Gradle lo descarga el wrapper (9.9). AGP 9.5 con Kotlin integrado.

## Comandos

```bash
./gradlew :domain:test :crypto:test     # pruebas de dominio y criptografía (rápidas, solo JVM)
```

```bash
./gradlew :app:assembleDebug            # APK en app/build/outputs/apk/debug/
```

```bash
./gradlew test                          # todas las pruebas unitarias
```

## Estado

Fase 0 completada: estructura de módulos, esquema de eventos v1, fórmula de puntaje v1 con pruebas,
identidad criptográfica, base de datos cifrada y una pantalla inicial que muestra la huella de
identidad y el puntaje. Siguiente: Fase 1 (registro de entrenamientos).
