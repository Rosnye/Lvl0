# Fórmula de puntaje v1

Código: `domain/src/main/kotlin/io/gymtracker/domain/scoring/`. Pruebas: `domain/src/test/.../ScoringEngineTest.kt`.

## Objetivos de diseño

- **Premiar la constancia por encima de la fuerza absoluta.** Compites contra tu propio historial, no contra el peso de tus amigos.
- **Determinista.** Mismo historial + misma fecha = mismo puntaje, en cualquier teléfono. Sin reloj, sin aleatoriedad.
- **Difícil de inflar sin mentir.** Partir una sesión en dos, crear ejercicios nuevos para "romper récords" o meter series vacías no da puntos.
- **Explicable.** Cada sesión tiene un desglose que la pantalla puede mostrar.

## Definiciones

- **Semana:** lunes a domingo (ISO), según la fecha local del autor en cada sesión.
- **Serie de trabajo:** no es calentamiento y tiene reps, tiempo o distancia > 0.
- **Sesión válida:** al menos **3** series de trabajo. Las demás no puntúan ni cuentan para frecuencia.
- **Volumen (kg):** Σ reps × kg de las series de trabajo con carga (`WEIGHTED`, o `BODYWEIGHT` con lastre). Cardio y ejercicios por tiempo tienen volumen 0.
- **1RM estimado (Epley):** `peso × (1 + reps / 30)`, solo series de trabajo con 1 a 12 reps y carga > 0.
- **Meta semanal vigente:** la última meta `weekly_frequency` creada hasta esa fecha, limitada a 2..6. Sin meta: **3** sesiones.

## Puntos por sesión

```
puntos = round((base + bonoVolumen + bonoRecords) × multiplicador) + bonoMetas
```

| Componente | Regla | Valor |
|---|---|---|
| **Base** | Primera sesión válida del día. La segunda del mismo día no suma base ni volumen. | 100 |
| **Bono de volumen** | `ratio = volumen / promedio de tus sesiones de los 28 días anteriores`. Requiere ≥ 3 sesiones previas en la ventana. `bono = clamp(round(50 × (ratio − 1)), 0, 50)`. | 0 a 50 |
| **Bono de récords** | +30 por cada ejercicio cuyo 1RM estimado supera tu mejor marca, **solo si ya tenías ≥ 3 sesiones previas con ese ejercicio**. Máximo 2 récords por sesión. La marca se actualiza siempre, puntúe o no. | 0, 30 o 60 |
| **Multiplicador de racha** | `1 + 0.10 × min(racha, 5)`, donde racha = semanas **anteriores completas** consecutivas que alcanzaron su meta semanal. | 1.0 a 1.5 |
| **Bono de meta semanal** | Al registrar la sesión que alcanza la meta de frecuencia de la semana. Una vez por semana. | 100 |
| **Bono de meta de fuerza** | Primera sesión (desde la creación de la meta) con una serie de trabajo de `peso ≥ objetivo` y `reps ≥ minReps`. Una vez por meta. | 200 |

Los bonos de metas no se multiplican por la racha.

### Ejemplos

| Situación | Cálculo | Puntos |
|---|---|---|
| Primera sesión de la vida | 100 × 1.0 | 100 |
| Tercera sesión de la semana (meta 3), sin racha | 100 × 1.0 + 100 | 200 |
| Sesión con volumen 1.5× tu promedio, racha de 2 semanas | (100 + 25) × 1.2 | 150 |
| Dos récords, racha de 5+ semanas | (100 + 0 + 60) × 1.5 | 240 |
| Segunda sesión del mismo día, con un récord | (0 + 0 + 30) × 1.0 | 30 |
| Sesión de 2 series | sesión no válida | 0 |

## Agregados

- **Puntos de la semana / del mes:** suma de los puntos de las sesiones de ese periodo. Es lo que ordena el tablero entre amigos.
- **Racha vigente:** semanas anteriores completas consecutivas con la meta cumplida, más 1 si la semana actual ya la cumplió.
- **Nivel:** `floor(sqrt(total / 500)) + 1`. Nivel 2 a los 500 puntos, 3 a los 2 000, 4 a los 4 500, 5 a los 8 000.

Orientación: alguien que entrena 3 veces por semana con racha estable gana unos 450 a 550 puntos semanales y sube de nivel cada pocas semanas al principio y cada varios meses después.

## Lo que se publica a los amigos

Tras cada sesión registrada se anexa un `score_published` con el `ScoreSummary` de la semana actual (puntos de la semana, sesiones de la semana, total, racha, nivel y `formulaVersion`). Los amigos muestran el último resumen por semana. No reciben sesiones, ejercicios ni pesos.

## Limitaciones conocidas (aceptadas en v1)

- Los datos son autodeclarados; la fórmula dificulta inflar puntos sin mentir, pero no detecta mentiras. Es un juego entre amigos.
- Al compartir solo el puntaje, los amigos **confían** en el resumen firmado del autor en lugar de recalcularlo. Si en el futuro se comparte el resumen de sesiones, podrán recalcularlo localmente con la misma fórmula.
- Archivar una meta de frecuencia recalcula el historial con la meta de defecto. Simplificación consciente.
- Ejercicios por tiempo o distancia solo aportan la base. Un volumen para cardio queda para una fórmula v2.
- Si no hay baseline (primeras 3 sesiones, o vuelta tras más de 28 días), el bono de volumen es 0.

## Cómo cambiar la fórmula

1. Crear `ScoreFormulaV2` con las constantes nuevas y subir `VERSION`.
2. Mantener v1 para interpretar resúmenes antiguos recibidos de amigos.
3. Los tableros comparan solo resúmenes de la misma `formulaVersion`; durante la transición se muestra la versión junto al puntaje.
