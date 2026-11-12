# Esquema de eventos v1

Código: `domain/src/main/kotlin/io/gymtracker/domain/event/`.

## Principios

1. **Solo-anexar.** Nada se edita ni se borra. Un cambio es un evento nuevo (`session_deleted` en lugar de borrar una fila).
2. **Un autor por registro.** Cada usuario escribe únicamente en su propio registro. Los amigos reciben copias de lectura, así no hay conflictos que resolver.
3. **Firmado.** Cada evento lleva la firma Ed25519 del autor sobre sus bytes canónicos. Un evento alterado o atribuido a otra persona se rechaza.
4. **Visibilidad por tipo.** Cada tipo de evento es `PRIVATE` o `FRIENDS`; el tipo lo fija, no el usuario ni el cliente. Un receptor rechaza cualquier evento que no sea `FRIENDS`, aunque la firma sea válida.
5. **Puntaje derivado.** El puntaje nunca se "guarda": se recalcula desde los eventos con la fórmula versionada. Lo que se publica a los amigos es un `score_published` derivado del registro privado.

## Sobre (`SignedEvent`)

```json
{
  "body": {
    "schemaVersion": 1,
    "authorId": "<clave pública Ed25519, 64 hex>",
    "seq": 17,
    "createdAtEpochMs": 1762732800000,
    "visibility": "PRIVATE",
    "payload": { "type": "session_logged", "...": "..." }
  },
  "signatureHex": "<firma Ed25519, 128 hex>"
}
```

| Campo | Regla |
|---|---|
| `schemaVersion` | 1. Un lector acepta versiones menores o iguales a la suya e ignora campos desconocidos. |
| `authorId` | Clave pública Ed25519 en hex minúscula. Es la única identidad de usuario. |
| `seq` | Entero estrictamente creciente por autor, empieza en 1. En el registro propio es consecutivo; en la copia de un amigo tiene huecos (solo llegan los `FRIENDS`). |
| `createdAtEpochMs` | Reloj del autor. Informativo; el orden lo da `seq`. |
| `visibility` | Debe coincidir con `payload.requiredVisibility`. |
| `signatureHex` | Firma de `EventCodec.canonicalBytes(body)`. |

**Bytes canónicos:** JSON de kotlinx.serialization con `encodeDefaults = true`, `explicitNulls = true`, discriminador `"type"`, campos en orden de declaración y sin espacios. Cualquier otra implementación debe reproducirlo byte a byte. Si en el futuro hace falta interoperar con otros lenguajes, se migrará a CBOR determinista con una nueva `schemaVersion`.

**Identificador de contenido:** `eventId = SHA-256(bytes canónicos)`. Se usa para deduplicar; la identidad lógica es `(authorId, seq)`.

## Tipos de evento

| `type` | Visibilidad | Contenido | Efecto en la proyección |
|---|---|---|---|
| `profile_updated` | FRIENDS | `profile: { nickname, avatarId }` | Reemplaza el perfil. |
| `exercise_defined` | PRIVATE | `exercise: ExerciseDefinition` | Alta o reemplazo por `id`. |
| `session_logged` | PRIVATE | `session: WorkoutSession` | Alta o reemplazo por `id`, salvo que exista lápida. |
| `session_deleted` | PRIVATE | `sessionId` | Lápida: elimina la sesión e ignora registros posteriores con ese `id`. |
| `goal_created` | PRIVATE | `goal: Goal` | Alta o reemplazo por `id`, salvo que esté archivada. |
| `goal_archived` | PRIVATE | `goalId` | Elimina la meta de las activas. |
| `routine_saved` | PRIVATE | `routine: Routine` | Alta o reemplazo por `id`, salvo que exista lápida. |
| `routine_deleted` | PRIVATE | `routineId` | Lápida: elimina la rutina e ignora guardados posteriores con ese `id`. |
| `score_published` | FRIENDS | `summary: ScoreSummary` | No afecta al estado propio. En el feed de un amigo, el último por `weekStart` manda. |

### Modelos de carga útil

**WorkoutSession**
- `id`, `localDate` (yyyy-MM-dd del autor; define día y semana del puntaje), `startedAtEpochMs`, `durationSeconds`, `routineName?`, `notes?`
- `entries[]`: `{ exerciseId, notes?, sets[] }`
- `sets[]`: `{ reps, weightGrams, durationSeconds, distanceMeters, rpe?, isWarmup }`. Pesos en gramos enteros para que la serialización sea exacta.

**ExerciseDefinition**: `id, name, primaryMuscle, equipment, loadType`. `loadType` decide qué cuenta como volumen y récord: `WEIGHTED`, `BODYWEIGHT` (solo el lastre), `TIMED`, `DISTANCE`.

**Routine** (añadido en la Fase 2, misma `schemaVersion` porque es privado y nunca llega a un amigo): `id, name, exercises[]`, cada uno `{ exerciseId, sets[] }` con series de plantilla (`DraftSet` sin marcar).

**Catálogo precargado**: los ejercicios con id `builtin:*` viven en el código (`BuiltInExercises`) y nunca se registran como eventos. Solo los personalizados (`custom:*`) generan `exercise_defined`.

**Goal**: `id, spec, createdLocalDate, deadlineLocalDate?`. `spec` es `weekly_frequency { sessionsPerWeek }` o `strength { exerciseId, targetWeightGrams, minReps }`.

**ScoreSummary** (lo único que ve un amigo sobre tu entrenamiento):
```json
{ "formulaVersion": 1, "weekStart": "2026-11-09", "weekPoints": 220, "weekSessions": 2,
  "totalPoints": 4310, "streakWeeks": 3, "level": 3 }
```
No incluye ejercicios, pesos, fechas de sesión, duración ni notas.

## Sincronización

- **Paquete** = `{ version: 1, events: [SignedEvent...] }` con los eventos `FRIENDS` del autor local con `seq > lastSentSeq` del amigo, serializado y cifrado para ese amigo (`crypto/PacketCipher`).
- El receptor abre el paquete, comprueba que todos los eventos son de un único autor **ya vinculado**, valida firma y visibilidad, descarta `seq` ya conocidos y anexa el resto.
- Un paquete de un desconocido se rechaza sin procesar: la vinculación previa por QR es el único mecanismo de confianza.
- `lastSentSeq` solo avanza cuando el transporte confirma la entrega (o el usuario comparte el archivo).

## Compatibilidad hacia adelante

- Añadir un campo opcional con valor por defecto: compatible, misma `schemaVersion`.
- Añadir un tipo de evento: compatible; lectores antiguos deberán ignorar tipos desconocidos (pendiente de implementar un `unknown` tolerante antes de la v1.0).
- Cambiar el significado de un campo o la forma canónica: nueva `schemaVersion`.
