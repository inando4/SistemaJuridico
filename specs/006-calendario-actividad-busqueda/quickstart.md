# Fase 1 — Recorrido de validación

Cómo comprobar que la feature funciona de punta a punta. `RecorridoAgendaTest` recorre esto mismo con navegador.

## Requisitos previos

```bash
./probar-local.sh --sembrar
```

Levanta PostgreSQL local, migra hasta la **V10**, crea la jefa e imprime el código de canje; con `--sembrar` carga los catálogos una vez activada la cuenta.

Hacen falta, como mínimo:

- La jefa activa y **dos abogados más**.
- Un expediente judicial con una palabra distintiva **solo en la materia**, y otro con esa palabra **solo en las observaciones**.
- Un procedimiento administrativo con esa palabra **solo en las observaciones**.
- Un expediente judicial **archivado** que contenga la palabra distintiva.
- Un pendiente con esa palabra **solo en las observaciones**.
- Pendientes de la abogada A repartidos por el mes: uno programado, uno que vence, y uno de tipo **Audiencia** con fecha programada.
- Un expediente judicial con **fecha de última actuación** dentro del mes.
- Dos pendientes de A **cumplidos hoy**, uno de ellos cumplido después de las 19:00 hora de Lima.

## 1. El buscador encuentra por los campos que faltaban

Abrir **Buscar** y escribir la palabra distintiva.

**Se espera**: cinco resultados en tres grupos —**tres** judiciales, uno administrativo, uno de pendientes—, cada grupo con su encabezado. El tercer judicial es el **archivado**, y sale **señalado como tal** (RF-011).

**Fallo característico**: solo aparecen los que coinciden por número o por nombre. Se amplió la consulta del buscador pero no la del listado, o al revés. Es exactamente lo que RF-012 y CE-003 existen para impedir.

## 2. La misma palabra da lo mismo en el listado

Ir a `/judiciales?q=<la palabra>`.

**Se espera**: los **dos activos**, y **no** el archivado. Los tres se ven con `/judiciales?q=<la palabra>&visibility=all`.

Esto es lo que CE-003 afirma y lo que no: coinciden **en los activos**, y difieren en visibilidad porque el buscador alcanza los archivados a propósito y el listado los oculta por omisión.

**Fallo característico**: el listado devuelve **un solo** expediente. Se amplió la condición `ILIKE` en el buscador y no en el repositorio del listado; es el fallo que RF-012 existe para impedir, y solo se ve si el registro que coincide lo hace **por materia o por observaciones**, no por número.

**Segundo fallo característico**: el buscador devuelve dos y no tres. Reutilizó la consulta completa del listado en vez de solo su condición de texto, y heredó `visibility=active`.

## 3. Un término corto no consulta

Escribir dos letras.

**Se espera**: mensaje explicando el mínimo de 3 caracteres. **Ninguna consulta ejecutada** — comprobable en el registro de SQL.

## 4. Los caracteres especiales son texto

Buscar `100%`.

**Se espera**: encuentra los registros que contienen literalmente `100%`, no todos. El escape se aplica también a los campos nuevos.

## 5. La actividad del día sale sola

Entrar como **A** y abrir **Actividad diaria**.

**Se espera**: los dos pendientes cumplidos hoy, **incluido el de después de las 19:00**.

**Fallo característico**: falta el de las 19:00 y aparece en el día siguiente. Se usó `CAST(completed_at AS date)`, que resuelve en la zona del servidor. La frontera se calcula en Java con `ClockConfig.ZONA` y llega como dos marcas de tiempo (`research.md`, decisión 4).

## 6. La actividad manual acepta las tres formas de tipo

Registrar tres actividades: una **sin tipo**, una con **«Informe legal»** del catálogo, y una con **«Otro» → «Reunión con Contabilidad»**.

**Se espera**: las tres se guardan y se listan; la de tipo libre muestra ese texto.

**Fallo característico**: la de sin tipo o la de tipo libre no aparece. Se usó `JOIN` en vez de `LEFT JOIN` sobre el catálogo, y desaparecieron justo las que no tienen referencia.

Después, abrir `/tipos-de-pendiente`.

**Se espera**: «Reunión con Contabilidad» **no está ahí**. Escribir un tipo no amplía el catálogo (RF-015b).

## 7. Un tipo usado por una actividad manual no se puede borrar

Como jefa, intentar borrar el tipo **«Informe legal»**, usado ahora solo por la actividad manual del paso 6.

**Se espera**: rechazo con el mensaje de **que está en uso**, y la sugerencia de deshabilitarlo.

**Fallo característico**: o bien dice «aparece en el historial» —el mensaje equivocado, porque `enUsoActual` sigue mirando una sola tabla—, o bien **aparece una traza de error de integridad**, que es lo que pasa si además se olvidó la referencia de catálogo al auditar. Las dos las arregla la decisión 1 de `research.md`.

## 8. Retirar no borra

Retirar una de las actividades manuales y abrir su historial.

**Se espera**: desaparece de la lista del día, pero el historial conserva el alta y la retirada con lo que decía antes (RF-021, RF-022).

## 9. Solo el autor o la jefa modifican

Como abogado **B**, enviar el `POST` de edición de una actividad de **A**, sin pasar por la interfaz.

**Se espera**: rechazo del servidor.

## 10. Una fecha futura se rechaza

Registrar una actividad con fecha de mañana.

**Se espera**: rechazo con mensaje. Es un registro de lo ya hecho.

## 11. El calendario muestra los cinco orígenes

Abrir **Calendario** en vista **mes**.

**Se espera**: el pendiente programado, el que vence, la **audiencia con su nombre de tipo**, la actuación judicial y los vencimientos de expedientes, cada uno en su día y distinguibles entre sí.

**Fallo característico**: la audiencia sale sin distinguir, o se buscó con `tipo.name = 'Audiencia'`. Se muestra el nombre del tipo de cada pendiente, sin comparar con ninguna cadena (`research.md`, decisión 6).

## 12. Las tres vistas coinciden

Cambiar a **semana** y luego a **día** sobre fechas que contengan eventos conocidos.

**Se espera**: cada vista muestra los eventos de su rango; ninguno aparece ni desaparece al cambiar de vista.

## 13. Sin calendario confirmado, el calendario sigue

En `/dias-no-laborables`, retirar la confirmación del año. Volver a **Calendario**.

**Se espera**: **la rejilla y todos los eventos siguen ahí**; solo se pierde el sombreado de días no laborables, y se avisa.

Después, volver a confirmar y **navegar a un mes de un año anterior**.

**Se espera**: ese mes sale con su sombreado y sin aviso, si su año está confirmado.

**Fallo característico**: sale sin sombreado y avisando en falso. Se pidió el calendario con `paraListado(hoy)`, que arranca en el año en curso, en vez de `instantanea(desde.getYear(), hasta.getYear())`. Misma familia que el fallo que la 003 dejó en producción (`research.md`, decisión 10).

**Fallo característico**: la pantalla se bloquea. El calendario no calcula días hábiles: sus eventos son fechas guardadas (RF-027).

Volver a confirmar el año antes de seguir.

## 14. Un pendiente con dos fechas sale dos veces

Comprobar el pendiente que tiene programación **y** vencimiento dentro del mes.

**Se espera**: aparece en **dos** días distintos, con dos etiquetas distintas. No es un duplicado: son dos hechos.

## 15. Todo el equipo ve todo

Como abogado **B**, consultar la actividad diaria de **A** y su calendario.

**Se espera**: las ve. El filtro por persona es comodidad, no permiso (principio II). En la actividad de A, **B no ve el formulario de alta**.

## Presupuestos

Se miden con 5 personas y 5.000 pendientes.

| Operación | Invariante — **lo que de verdad se defiende** | Techo (provisional) | Tiempo p95 |
|---|---|---|---|
| `GET /buscar` | **las mismas consultas con 300 resultados que con 3** | ≤ 6 | ≤ 500 ms |
| `GET /actividad-diaria` | **las mismas con 40 actividades que con 1** | ≤ 5 | ≤ 300 ms |
| `GET /calendario` | **las mismas en mes que en día** | ≤ 5 | ≤ 400 ms |

Los techos son estimaciones anteriores a la medición y se corregirán con el número real: en la 005 el plan dijo 6 y la medición dio 7, ninguna redundante, y se corrigió el documento.

**Las invariantes son otra cosa.** Que un mes cueste lo mismo que un día es lo que distingue una consulta por rango de treinta y una consultas por día que nadie nota hasta que el área lleva tres años de datos.
