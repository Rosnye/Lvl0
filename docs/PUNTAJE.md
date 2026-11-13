# Fórmula de puntaje (v2)

Código: `domain/src/main/kotlin/io/gymtracker/domain/scoring/`. Pruebas: `domain/src/test/.../ScoringEngineTest.kt`.

## Historial de versiones

| Versión | Fecha | Cambio |
|---|---|---|
| v1 | 2026-11-10 | Volumen en kg y récords por 1RM estimado (Epley). |
| v2 | 2026-11-13 | **Sin peso.** Los ejercicios se registran como series × repeticiones. El volumen es el total de repeticiones y un récord es superar tus repeticiones totales de un ejercicio en una sesión. Las metas nuevas son de repeticiones. Las insignias de toneladas pasan a ser de repeticiones. |

Al actualizar la app, todo el historial se recalcula con v2 (el peso registrado antes se ignora). Cada resumen compartido con amigos lleva su `formulaVersion`.

## Objetivos de diseño

- **Premiar la constancia** por encima del rendimiento absoluto: compites contra tu propio historial.
- **Determinista.** Mismo historial + misma fecha = mismo puntaje en cualquier teléfono.
- **Difícil de inflar sin mentir.** Partir una sesión en dos, crear ejercicios nuevos para "romper récords" o meter series vacías no da puntos.
- **Explicable.** Cada sesión tiene un desglose visible.

## Definiciones

- **Semana:** lunes a domingo (ISO), según la fecha local de cada sesión.
- **Serie de trabajo:** no es calentamiento y tiene reps, tiempo o distancia > 0.
- **Sesión válida:** al menos **3** series de trabajo.
- **Volumen:** total de repeticiones de las series de trabajo de ejercicios con peso o de peso corporal. Tiempo y distancia no suman volumen.
- **Repeticiones de un ejercicio en una sesión:** series × repeticiones (suma de sus series de trabajo).
- **Meta semanal vigente:** la última meta de frecuencia creada hasta esa fecha, limitada a 2..6. Sin meta: **3**.

## Puntos por sesión

```
puntos = round((base + bonoVolumen + bonoRecords) × multiplicador) + bonoMetas
```

| Componente | Regla | Valor |
|---|---|---|
| **Base** | Primera sesión válida del día. La segunda del mismo día no suma base ni volumen. | 100 |
| **Bono de volumen** | `ratio = repeticiones / promedio de tus sesiones de los 28 días anteriores` (≥ 3 sesiones previas). `bono = clamp(round(50 × (ratio − 1)), 0, 50)`. | 0 a 50 |
| **Bono de récords** | +30 por cada ejercicio en el que superas tus mejores repeticiones totales en una sesión, si ya tenías ≥ 3 sesiones previas con él. Máximo 2 por sesión. | 0, 30 o 60 |
| **Multiplicador de racha** | `1 + 0.10 × min(racha, 5)`; racha = semanas anteriores completas consecutivas con la meta semanal cumplida. | 1.0 a 1.5 |
| **Bono de meta semanal** | Al registrar la sesión que alcanza la meta de la semana. Una vez por semana. | 100 |
| **Bono de meta de repeticiones** | Primera sesión desde su creación con una serie de al menos esas repeticiones. Una vez por meta. | 200 |

Los bonos de metas no se multiplican por la racha.

### Ejemplos

| Situación | Cálculo | Puntos |
|---|---|---|
| Primera sesión de la vida | 100 × 1.0 | 100 |
| Tercera sesión de la semana (meta 3), sin racha | 100 + 100 | 200 |
| 1.5× tus repeticiones habituales, racha de 2 semanas | (100 + 25) × 1.2 | 150 |
| Dos récords, racha de 5+ semanas | (100 + 60) × 1.5 | 240 |
| Sesión de 2 series | no válida | 0 |

## Agregados

- **Puntos de la semana / total:** suma de los puntos de las sesiones. Es lo que ordena el tablero entre amigos.
- **Racha vigente:** semanas anteriores completas consecutivas con la meta cumplida, más 1 si la actual ya la cumplió.
- **Nivel:** `floor(sqrt(total / 500)) + 1`. Nivel 2 a los 500 puntos, 3 a los 2 000, 4 a los 4 500.

## Política de metas

- **Meta semanal:** cambiarla crea una meta nueva con la fecha de hoy; las semanas pasadas conservan la suya.
- **Meta de repeticiones:** solo se puede eliminar mientras no esté cumplida. Cumplida, queda como trofeo.
- **Metas de fuerza antiguas** (con peso, creadas antes de v2) se siguen mostrando; ya no se pueden crear.

## Insignias

Se derivan del mismo cálculo y son privadas.

| Insignia | Condición |
|---|---|
| Primer paso / Constante / Habitual / Centenario | 1 / 10 / 50 / 100 sesiones válidas |
| Nuevo récord / Rompe récords | 1 / 10 récords que puntúan |
| Semana cumplida | Alcanzar la meta semanal una vez |
| Un mes sin fallar / Trimestre de hierro | Racha de 4 / 12 semanas |
| Objetivo logrado | Cumplir una meta de repeticiones |
| Mil repeticiones / Diez mil repeticiones | 1 000 / 10 000 repeticiones acumuladas |
| Nivel 5 / Nivel 10 | Alcanzar ese nivel |
| Maratón | Una sesión válida de 90 minutos o más |

## Limitaciones conocidas

- Los datos son autodeclarados: es un juego entre amigos, no hay anti-trampas.
- Los amigos confían en el resumen firmado del autor en lugar de recalcularlo.
- Tiempo y distancia solo aportan la base y cuentan como series de trabajo.
