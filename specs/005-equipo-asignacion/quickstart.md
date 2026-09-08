# Fase 1 — Recorrido de validación

Cómo comprobar que la feature funciona de punta a punta. `RecorridoEquipoTest` recorre esto mismo con navegador.

## Requisitos previos

```bash
./probar-local.sh
```

Levanta PostgreSQL local, migra, crea la jefa e imprime el código de canje. Con `--sembrar` carga además los catálogos, una vez que la jefa haya activado su cuenta.

Hacen falta, como mínimo:

- La jefa activa, y **dos abogados más** (`/usuarios`). La reasignación necesita origen y destino.
- El calendario del año cargado y **confirmado** en `/dias-no-laborables`. Sin confirmarlo, el paso 4 comprueba la degradación en vez del recuento.
- Un expediente judicial de la abogada A con **tres pendientes activos y dos cumplidos**.
- Un pendiente **suelto** de A, sin vínculo a ningún expediente.
- Un pendiente colgado del expediente de A pero **registrado por la abogada C**, para el caso del tercero.

## 1. La vista de equipo muestra la carga

Entrar como cualquiera y abrir **Equipo** en la navegación.

**Se espera**: una fila por cuenta activa, la jefa incluida; la persona con más `vencidos + esta semana` arriba; esa suma visible en su columna; y bajo el título, el rango de fechas de la semana.

**Fallo característico**: si alguien sin pendientes no aparece, la consulta usó `JOIN` en vez de `LEFT JOIN`. Si aparece con 1 en vez de 0, se usó `count(*)` en lugar de `count(t.id)`.

## 2. Cada número lleva a su lista

Pulsar el recuento de «Vence esta semana» de un abogado.

**Se espera**: el listado de pendientes filtrado por esa persona, con **exactamente** tantas filas como decía el número.

**Fallo característico**: el listado muestra los de todo el mundo. Faltan `responsable` o `visibilidad` en el enlace — el mismo desajuste que la 004 corrigió en sus tarjetas.

## 3. La jefa aparece en la lista

Comprobar que la fila de la jefa está.

**Fallo característico**: se filtró por `role = 'LAWYER'`. El criterio es `status = 'ACTIVE'` (`research.md`, decisión 6).

## 4. Sin calendario, solo se degrada una columna

En `/dias-no-laborables`, retirar la confirmación del año. Volver a **Equipo**.

**Se espera**: «Sin plazo, antiguos» muestra `Faltan días no laborables por revisar`; **«Vencidos» y «Vence esta semana» siguen mostrando su número**, porque comparan fechas y no dependen de días hábiles.

**Fallo característico**: la pantalla entera se bloquea, o la columna muestra `0`. Un cero afirma que nadie lleva mucho esperando, y sería falso.

Volver a confirmar el año antes de seguir.

## 5. El aviso previo dice lo que va a pasar

Como jefa, abrir la ficha del expediente de A y desplegar la reasignación.

**Se espera**: antes de confirmar, el texto dice cuántos pendientes se traspasan, que incluye los cumplidos, y **nombra a la abogada C** como tercera persona que perderá el acceso de edición.

**Fallo característico**: no se menciona a C. Se está por quitarle trabajo a alguien en silencio, que es justo lo que RF-004b prohíbe.

## 6. La reasignación mueve el expediente completo

Elegir al abogado B y confirmar.

**Se espera**: el mensaje indica el número de pendientes traspasados. En la ficha, el responsable es B. Los **cinco** pendientes de A —tres activos y dos cumplidos— y el de C están ahora a nombre de B.

**Fallo característico**: los cumplidos siguen a nombre de A. El filtro se limitó a los activos; el insumo dice «el expediente viaja completo».

## 7. El nuevo responsable manda, el anterior no

Entrar como **B** y revertir uno de los cumplidos que había marcado A. Entrar como **A** e intentar editar cualquiera de esos pendientes.

**Se espera**: B puede revertirlo indicando el motivo. A recibe un rechazo: conserva la lectura, ha perdido la escritura.

## 8. El historial conserva el responsable anterior real

Abrir el historial del expediente y el del pendiente que era de C.

**Se espera**: en el expediente, `A → B`, con la fecha y la jefa como autora. En el pendiente de C, el anterior es **C**, no A.

**Fallo característico**: dice A. Se asumió que todos los pendientes eran del responsable saliente, y no lo eran (`research.md`, decisión 5).

## 9. El pendiente suelto se reasigna solo

Como jefa, abrir el pendiente suelto de A y reasignarlo.

**Se espera**: cambia de responsable y su historial lo registra.

Después, abrir un pendiente **vinculado** al expediente.

**Se espera**: no hay formulario de reasignación; hay un enlace al expediente del que cuelga.

## 10. Reasignar a quien ya lo tiene no escribe nada

Reasignar el expediente a **B**, que ya es su responsable.

**Se espera**: aviso de que ya está a su nombre, y **ninguna entrada nueva** en el historial (principio VII).

## 11. Un abogado no puede reasignar

Como abogado, enviar el `POST` de reasignación directamente, sin pasar por la interfaz.

**Se espera**: rechazo del servidor. Ocultar el botón no es autorización.

## 12. La transacción es de todo o nada

Cubierto por `ReasignacionAtomicaIT`, no a mano: con un fallo forzado a mitad, ni el expediente ni ninguno de sus pendientes cambia de responsable.

## Presupuestos

Se miden con 5 personas y 5.000 pendientes (`EquipoQueryBudgetIT`, `ReasignacionQueryBudgetIT`):

| Operación | Consultas | Tiempo p95 |
|---|---|---|
| `GET /equipo` | ≤ 4, y **el mismo número con 15 personas que con 5** | ≤ 400 ms |
| Reasignar expediente | ≤ 6, y **el mismo con 50 pendientes que con 5** | ≤ 500 ms |
| Reasignar pendiente suelto | ≤ 4 | ≤ 300 ms |

Que el número no cambie al doblar el equipo o los pendientes es la comprobación que importa: es lo que distingue una consulta agrupada de un N+1 que todavía no duele.
