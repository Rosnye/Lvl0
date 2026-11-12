# Plan de trabajo: app Android de seguimiento de gimnasio (descentralizada)

Fecha: 2026-11-10
Estado: v1.3. Fases 0, 1 y 2 completadas (2026-11-12).

## 1. Visión y principios

Una app Android para registrar entrenamientos, fijar metas y ganar puntos, que:

1. **No necesita servidores.** Todos los datos viven en el teléfono. No hay cuenta, correo, ni login.
2. **Es social solo entre amigos.** Dos personas se vinculan en persona (código QR) o con un enlace de invitación. No existe directorio público de usuarios ni descubrimiento abierto.
3. **Nunca guarda información sensible fuera del dispositivo.** Lo único que sale del teléfono son paquetes cifrados de extremo a extremo (puntajes y resúmenes), y solo hacia amigos vinculados.
4. **Funciona 100 % sin conexión.** La red solo se usa para sincronizar con amigos; nada se rompe sin ella.

## 2. Funciones de la app

Prioridad: **P0** = MVP, **P1** = segunda versión, **P2** = más adelante.

### 2.1 Registro de entrenamiento
| Prioridad | Función |
|---|---|
| P0 | Catálogo de ejercicios precargado (grupo muscular, equipo) + ejercicios personalizados |
| P0 | Rutinas/plantillas (día de pierna, push/pull, etc.) y programas semanales |
| P0 | Registro de sesión: series, repeticiones, peso, RPE opcional, notas, duración |
| P0 | Temporizador de descanso con notificación local |
| P0 | Historial por fecha y calendario de asistencia |
| P1 | Calculadora de discos, 1RM estimado (fórmula Epley/Brzycki) |
| P1 | Medidas corporales y peso corporal |
| P1 | Cardio (tiempo, distancia) y ejercicios por tiempo (plancha) |
| P2 | Superseries, circuitos, pirámides |
| P2 | Importar CSV desde otras apps (Strong, Hevy) |

### 2.2 Progreso
| Prioridad | Función |
|---|---|
| P0 | Récords personales (PR) por ejercicio con aviso al superarlos |
| P0 | Gráficas: volumen semanal, peso máximo por ejercicio, frecuencia |
| P1 | Volumen por grupo muscular (mapa de calor semanal) |
| P2 | Comparativa entre periodos (mes vs mes) |

### 2.3 Metas
| Prioridad | Función |
|---|---|
| P0 | Meta de frecuencia: "entrenar N días por semana" |
| P0 | Meta de fuerza: "llegar a X kg en Y ejercicio" |
| P0 | Rachas (semanas consecutivas cumpliendo la meta) |
| P1 | Meta de volumen semanal, meta de peso corporal |
| P1 | Retos con amigos con fecha límite (ej. "más sesiones en noviembre") |
| P2 | Metas sugeridas según historial |

### 2.4 Puntaje y gamificación
| Prioridad | Función |
|---|---|
| P0 | Puntos por sesión: base por completar + bonus por volumen relativo al propio promedio + bonus por PR |
| P0 | Multiplicador por racha, nivel acumulado |
| P0 | Tablero semanal y mensual entre amigos |
| P1 | Logros/insignias (primer PR, 10 sesiones, 4 semanas seguidas, etc.) |
| P2 | Temporadas (el tablero se reinicia cada trimestre, se conservan trofeos) |

Regla de diseño: el motor de puntaje es **determinista y puro**. Dado el mismo historial, cualquier teléfono calcula el mismo puntaje. Así los amigos no reciben "el número" sino los eventos firmados y calculan el puntaje localmente. Esto evita confiar en un número arbitrario y permite cambiar la fórmula con versionado.

Limitación honesta: los datos son autodeclarados. No se puede impedir que alguien invente una sesión; solo que la visibilidad entre amigos y la firma digital hacen que lo registrado quede atribuido a esa persona y no pueda modificarse por terceros.

### 2.5 Social entre amigos (sin servidor)
| Prioridad | Función |
|---|---|
| P0 | Identidad local: par de claves generado en el teléfono. El "usuario" es su clave pública, con apodo y avatar opcionales |
| P0 | Vincular amigo por QR en persona (intercambio mutuo de claves públicas) |
| P0 | Sincronización por proximidad (Bluetooth / Wi-Fi Direct) cuando están juntos, por ejemplo en el gimnasio |
| P0 | Exportar "paquete cifrado" para un amigo y compartirlo por cualquier medio (WhatsApp, Telegram, correo). El mensajero solo ve bytes cifrados |
| P0 | Control de qué se comparte: solo puntaje, puntaje + resumen (sesiones, PRs) o nada |
| P0 | Eliminar amigo (deja de recibir y de aceptar paquetes suyos) |
| P1 | Enlace de invitación (contiene la clave pública) para vincular sin estar juntos |
| P1 | Reacciones/kudos a sesiones de amigos (se propagan en la siguiente sincronización) |
| P2 | Sincronización asíncrona por internet usando relés públicos cifrados (ver sección 4.3) |

### 2.6 Datos, privacidad y respaldo
| Prioridad | Función |
|---|---|
| P0 | Base de datos local cifrada |
| P0 | Respaldo manual cifrado a archivo (con frase de contraseña) y restauración |
| P1 | Migrar a teléfono nuevo por proximidad o QR + archivo |
| P1 | Borrado total con un botón |
| P0 | Cero analítica, cero rastreadores, sin permisos innecesarios |

### 2.7 Otros
| Prioridad | Función |
|---|---|
| P0 | Modo oscuro, español e inglés |
| P1 | Recordatorios locales ("hoy toca pierna") |
| P1 | Widget de racha/próxima sesión |
| P2 | Integración con Health Connect (leer peso, escribir sesiones) |
| P2 | Wear OS para registrar series desde el reloj |

## 3. Arquitectura

### 3.1 Modelo de datos: registro de eventos por autor
Cada usuario produce un **registro de eventos solo-anexar** (append-only log): `SesionRegistrada`, `MetaCreada`, `MetaCumplida`, `PerfilActualizado`, etc. Cada evento lleva:

- id del autor (clave pública)
- número de secuencia incremental
- marca de tiempo
- carga útil (datos del evento)
- firma digital del autor

Por qué funciona sin servidor ni CRDT complejo: **solo el autor escribe en su propio registro**. Los amigos reciben copias de lectura. Sincronizar es "dame tus eventos a partir de la secuencia N". No hay conflictos posibles. Si una fórmula de puntaje cambia, se recalcula desde los eventos.

Lo que se comparte con amigos es una **proyección** del registro (eventos con el nivel de detalle que el usuario eligió), no la base de datos completa. Las notas personales y medidas corporales nunca salen del teléfono salvo que se active explícitamente.

### 3.2 Identidad y confianza
- Al instalar, se genera un par de claves (Ed25519 para firmar, X25519 para cifrar). La clave privada se protege con Android Keystore.
- Vincular amigo = intercambiar claves públicas por QR en persona (modelo "confianza en el primer uso", como Signal con códigos de seguridad). No hay servidor que valide quién es quién; lo valida el encuentro físico o un canal que el usuario ya confía.
- Cada paquete hacia un amigo se cifra con la clave de ese amigo (cifrado de extremo a extremo). Un paquete interceptado no sirve a nadie más.

### 3.3 Transportes de sincronización (en capas)
1. **Proximidad (P0):** Nearby Connections API de Google (combina Bluetooth, BLE y Wi-Fi Direct). Sincroniza automáticamente cuando dos amigos abren la app cerca.
2. **Archivo cifrado (P0):** la app genera un archivo `.gympkg` cifrado para un amigo concreto y lo envía con la hoja de compartir de Android. El amigo lo abre con la app. Funciona con cualquier mensajero y sin Google Play Services. Es el transporte de respaldo universal.
3. **Relés cifrados por internet (P2, opcional):** usar la red Nostr (relés públicos, protocolo abierto) como buzón: los paquetes van cifrados de extremo a extremo y el relé solo ve bytes y la clave pública destino. No es "cero servidores", pero son servidores de terceros, intercambiables, que no guardan nada legible. El usuario puede desactivarlo o apuntar a su propio relé.

### 3.4 Capas de la app
```
domain/      Modelos, esquema de eventos, proyecciones y motor de puntaje (Kotlin/JVM puro, 100 % testeable)
crypto/      Identidad Ed25519/X25519, firma y cifrado de paquetes con BouncyCastle (Kotlin/JVM puro)
data/        Room + SQLCipher, sellado de secretos con Keystore; implementa EventLog (Android)
sync/        Identidad local, paquetes hacia amigos, transportes Nearby y archivo (Android)
app/         Jetpack Compose + Hilt: pantallas, navegación, temas (Android)
```
Dependencias: `app → sync → data → domain` y `sync → crypto → domain`. Toda la lógica de negocio y la criptografía se prueban en JVM sin emulador. Ver docs/EVENTOS.md y docs/PUNTAJE_V1.md.

## 4. Tecnologías evaluadas

### 4.1 Framework de la app

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| **Kotlin + Jetpack Compose (nativo)** | Acceso directo a Nearby Connections, Bluetooth, Keystore, WorkManager. Mejor rendimiento y batería. Es el estándar actual de Android | Solo Android (iOS requeriría KMP después) | **Recomendado** |
| Flutter | Multiplataforma, UI rápida de construir | Las librerías P2P/Bluetooth son plugins de terceros con mantenimiento irregular; cada API nativa requiere puente | Válido si iOS fuera prioridad desde el día 1 |
| React Native / Expo | Ecosistema grande | Mismo problema de plugins para P2P y criptografía; más capas | No recomendado para este caso |
| Kotlin Multiplatform + Compose Multiplatform | Comparte dominio y UI con iOS | Añade complejidad hoy sin necesidad | Dejar la puerta abierta aislando `domain/`; adoptar solo si surge iOS |

**Decisión:** Kotlin + Jetpack Compose. La parte más difícil del proyecto (P2P y criptografía) es justamente donde lo nativo da más ventaja.

### 4.2 Stack recomendado
| Área | Tecnología | Nota |
|---|---|---|
| Lenguaje / UI | Kotlin 2.3, Jetpack Compose (BOM 2026.09), Material 3, Navigation Compose | |
| Inyección de dependencias | Hilt | |
| Asincronía | Coroutines + Flow | |
| Base de datos | Room sobre SQLite + SQLCipher | Cifrado en reposo con clave en Android Keystore |
| Serialización | kotlinx.serialization con formato CBOR o Protobuf | Paquetes compactos para Bluetooth |
| Criptografía | BouncyCastle (API ligera, Java puro) | Ed25519 firmas, X25519 + HKDF + ChaCha20-Poly1305. Sin librerías nativas, testeable en JVM. Se descartó libsodium/JNA por complejidad de build |
| Códigos QR | CameraX + ML Kit Barcode (o ZXing si se evita Google) | |
| Proximidad | Nearby Connections API | Requiere Google Play Services; ver 4.4 |
| Gráficas | Vico (Compose) | |
| Tareas en segundo plano | WorkManager | Recordatorios, limpieza |
| Pruebas | JUnit 5, Turbine, Robolectric, Compose UI Test | Motor de puntaje con pruebas exhaustivas |
| Build / CI | AGP 9.5 (Kotlin integrado), Gradle 9.9, compileSdk 37, minSdk 26, version catalog; GitHub Actions pendiente | Hilt 2.59+ y AndroidX actuales exigen AGP 9 |
| Distribución | Google Play + APK en GitHub Releases (y F-Droid más adelante) | Coherente con la filosofía abierta |

### 4.3 Opciones de red descentralizada consideradas
| Opción | Evaluación |
|---|---|
| Nearby Connections (proximidad) | Simple, sin infraestructura. Limitado a estar cerca. **Usar en P0** |
| Archivo cifrado por share sheet | Cero infraestructura, funciona siempre. Requiere acción manual. **Usar en P0** |
| Nostr (relés públicos, mensajes cifrados) | Protocolo abierto y simple, librerías Kotlin disponibles, relés intercambiables. Expone metadatos (qué clave habla con qué clave) al relé. **Candidato para P2** |
| libp2p / IPFS | Potente pero pesado en móvil, consumo de batería, NAT traversal complicado. Descartado |
| WebRTC directo | Requiere servidor de señalización. Solo tiene sentido usando Nostr como señalización. Descartado por ahora |
| Matrix (E2E) | Requiere homeserver y cuentas. Contradice "sin cuentas". Descartado |
| Tor / Briar-style | Máxima privacidad, pero latencia alta y complejidad enorme. Descartado |

### 4.4 Riesgos y compromisos
- **Dependencia de Google Play Services** para Nearby Connections. Mitigación: el transporte por archivo cifrado no la necesita; si se quiere eliminar del todo, se puede implementar Wi-Fi Direct/BLE a mano en una fase posterior (mucho más trabajo).
- **Permisos de Bluetooth/ubicación** en Android para descubrir dispositivos cercanos. Hay que explicarlo bien en el onboarding para no asustar.
- **Pérdida del teléfono = pérdida de identidad y datos.** No hay servidor que recupere nada. Mitigación: respaldo cifrado obligatorio sugerido en onboarding y migración entre teléfonos.
- **Sin anti-trampas real.** Aceptarlo y comunicarlo: es una app para amigos, no una competición pública.
- **Versionado del motor de puntaje.** Cada evento guarda la versión de fórmula vigente; al cambiarla, se publica la nueva versión y los tableros se recalculan igual en todos los teléfonos.

## 5. Plan de trabajo por fases

Estimaciones para una persona a tiempo parcial; ajustar según disponibilidad.

### Fase 0: Base del proyecto (1 a 2 semanas)
- Crear repositorio, proyecto Android, módulos `ui`, `domain`, `data`, `sync`.
- Configurar Hilt, Room + SQLCipher, kotlinx.serialization, CI con pruebas.
- Generar identidad (par de claves) y guardarla en Keystore.
- Definir el esquema de eventos y la tabla del registro de eventos.
- Entregable: app vacía que arranca, crea identidad y pasa CI.

### Fase 1: MVP de entrenamiento (3 a 4 semanas)
- Catálogo de ejercicios, ejercicios personalizados.
- Rutinas y registro de sesión con series, temporizador de descanso.
- Historial, calendario, PRs, gráficas básicas.
- Entregable: app usable a diario para una sola persona.

### Fase 2: Metas y puntaje (2 semanas)
- Motor de puntaje en `domain` con pruebas unitarias completas.
- Metas de frecuencia y fuerza, rachas, nivel.
- Pantalla "Mi progreso" con puntos, racha y metas.
- Entregable: puntaje reproducible y versionado.

### Fase 3: Amigos y sincronización P2P (3 a 4 semanas)
- Vinculación por QR (intercambio de claves).
- Serialización y cifrado de paquetes; proyección según nivel de privacidad.
- Transporte por archivo cifrado (exportar/importar).
- Transporte por Nearby Connections con sincronización incremental por secuencia.
- Tablero semanal/mensual entre amigos, eliminar amigo.
- Entregable: dos teléfonos sincronizan puntajes sin ningún servidor.

### Fase 4: Pulido y lanzamiento (2 semanas)
- Respaldo cifrado y restauración, onboarding con explicación de permisos.
- Localización ES/EN, modo oscuro, accesibilidad básica.
- Pruebas de UI, firma de APK, publicación en Play y GitHub Releases.
- Entregable: versión 1.0.

### Fase 5: Evolución (posterior)
- Retos con amigos, logros, reacciones.
- Sincronización asíncrona por relés Nostr.
- Health Connect, widget, Wear OS.
- Evaluar Kotlin Multiplatform si aparece demanda de iOS.

## 6. Registro de decisiones
| Fecha | Decisión |
|---|---|
| 2026-11-10 | **Sin iOS.** Kotlin + Jetpack Compose nativo. `domain` y `crypto` quedan en JVM puro igualmente. |
| 2026-11-10 | **Se acepta Google Play Services** para Nearby Connections en la v1. El transporte por archivo cifrado sigue como alternativa sin GMS. |
| 2026-11-10 | **Se comparte solo el puntaje.** Los amigos reciben `score_published` (puntos de la semana, total, racha, nivel) y el perfil. Nunca sesiones, ejercicios ni pesos. Consecuencia: los amigos confían en el resumen firmado en vez de recalcularlo. |
| 2026-11-10 | **Fórmula de puntaje v1** definida e implementada con pruebas (docs/PUNTAJE_V1.md). |
| 2026-11-10 | **BouncyCastle** en lugar de libsodium: Java puro, sin JNA, mismo código en pruebas JVM y en Android. |
| 2026-11-10 | **AGP 9.5 / compileSdk 37**: las versiones actuales de Hilt y AndroidX lo exigen. |
| 2026-11-12 | **Metas sin efecto retroactivo**: cambiar la meta semanal crea una nueva; las metas de fuerza cumplidas no se pueden eliminar. |
| 2026-11-12 | **Insignias y rutinas privadas**: no se comparten con amigos (coherente con "solo el puntaje"). |
| 2026-11-12 | **Clave de depuración en el repositorio** para que los APK de cualquier máquina o del CI se actualicen entre sí. |

Pendiente: nombre definitivo de la app y licencia (recomendación: código abierto, GPL-3.0 o MIT).

## 7. Estado y siguientes pasos
**Fase 0 completada (2026-11-10):** estructura de módulos, esquema de eventos v1, motor de puntaje v1 con 17 pruebas, identidad criptográfica con 6 pruebas, Room + SQLCipher con clave sellada en Keystore, app Compose que arranca y muestra huella y puntaje. `./gradlew :app:assembleDebug` genera el APK.

**Fase 1 completada (2026-11-11):**
- Catálogo precargado de 55 ejercicios en `domain` (ids estables `builtin:*`, no se registran como eventos) y ejercicios personalizados por `exercise_defined`.
- Borrador de entrenamiento persistido en DataStore (`WorkoutDraft`), convertido a `session_logged` al terminar; publica `score_published` en el mismo paso.
- Temporizador de descanso: servicio en primer plano (`specialUse`) con notificación que usa el cronómetro del sistema, acciones +30 s / Omitir y aviso con sonido y vibración al terminar. Estado persistido para sobrevivir a la muerte del proceso.
- Historial agrupado por semana, detalle de sesión con desglose del puntaje y borrado por lápida.
- Capturas de pantalla automatizadas para todas las pantallas (`updateDebugScreenshotTest`).

Pendiente de la Fase 1 original: rutinas/plantillas y calendario de asistencia (completados en la Fase 2).

**Fase 2 completada (2026-11-12):**
- Pestaña Progreso: nivel con barra hacia el siguiente, racha, calendario mensual de asistencia, metas e insignias; el historial pasa a una pantalla secundaria.
- Metas: meta semanal editable (2 a 6 sesiones) sin alterar semanas pasadas; metas de fuerza y de repeticiones con progreso, eliminables solo si no están cumplidas.
- 15 insignias deterministas con fecha de desbloqueo (`AchievementEngine`).
- Rutinas: eventos privados `routine_saved` / `routine_deleted`, editor, empezar entrenamiento desde rutina y guardar una sesión como rutina.
- Resumen al terminar: puntos de la sesión e insignias nuevas.
- CI con GitHub Actions: pruebas, validación de capturas y APK descargable como artefacto. Clave de depuración versionada para que los APK del CI se instalen encima de los anteriores.

Siguientes pasos (Fase 3, amigos y sincronización P2P):
1. Pantalla Amigos: mostrar mi QR (tarjeta firmada) y escanear el del amigo.
2. Transporte por archivo `.gympkg` con FileProvider y apertura desde otras apps.
3. Sincronización por proximidad con Nearby Connections.
4. Tablero semanal y mensual entre amigos.
