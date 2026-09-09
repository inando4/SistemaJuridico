# Comprobación manual: Pendientes relacionados

Recorrido para verificar la feature 008 en el navegador. Unos diez minutos.

**Antes de empezar**

```bash
./mvnw spring-boot:run
```

Entrar en <http://localhost:8080> con una cuenta de abogado (no la de la jefa: parte del recorrido comprueba justamente qué pasa con expedientes ajenos).

---

## 1. El bloque aparece en la ficha judicial

1. Ir a `/judiciales` y abrir cualquier expediente que tenga pendientes.
2. Bajar hasta **«Pendientes relacionados»**.

**Se espera**: una lista donde cada fila muestra título, responsable, estado, prioridad y fecha límite. Si el expediente tiene pendientes de otra persona, salen, con su nombre.

**Falla si**: el bloque no está, o solo salen los pendientes propios.

---

## 2. Los cumplidos se ven; los archivados no

1. En el mismo expediente, marcar un pendiente como cumplido y volver a la ficha.
2. Comprobar que **sigue en la lista**, marcado como cumplido, después de los que quedan por hacer.
**Por qué**: lo cumplido es historia del expediente y se queda.

> **La otra mitad no se puede hacer desde la pantalla.** El bloque también debe ocultar
> los pendientes **archivados**, y hoy la aplicación **no ofrece ninguna forma de
> archivar un pendiente**: el filtro «Ocultos» del listado existe desde la
> funcionalidad de pendientes, pero nada puede producir ese estado. No es un fallo de
> esta feature. El comportamiento está comprobado con datos puestos directamente en la
> base (`PendingTaskVisibilityContractTest`, `ExpedientePendientesContractTest`); lo que
> falta es la acción para llegar ahí. Ver la nota del final.

---

## 3. La lista vacía no desaparece

1. Abrir un expediente sin ningún pendiente vinculado.

**Se espera**: el bloque está, con un mensaje de que no hay ninguno. No un hueco en blanco ni una sección que se esfuma.

---

## 4. Crear un pendiente ya vinculado

1. En la ficha de un expediente, pulsar **«+ Crear nuevo pendiente relacionado»**.
2. Mirar el desplegable **«Expediente judicial relacionado»**.

**Se espera**: viene con ese expediente ya elegido.

3. Rellenar el título y guardar.
4. Volver a la ficha del expediente.

**Se espera**: el pendiente nuevo está en el bloque.

---

## 5. El vínculo se puede quitar

1. Repetir el paso 4, pero antes de guardar poner el desplegable en vacío.
2. Guardar.

**Se espera**: el pendiente se crea **sin vínculo**. El pre-seleccionado era una sugerencia, no una imposición.

---

## 6. El aviso cuando el expediente es de otra persona

1. Abrir la ficha de un expediente **de otro abogado**.
2. Pulsar «+ Crear nuevo pendiente relacionado».

**Se espera**: junto a «El responsable será usted» aparece un aviso nombrando a quien lleva el expediente, **antes** de guardar.

**Por qué**: el pendiente quedará a nombre de quien lo registra, no del dueño del expediente. Que se sepa antes, no después.

---

## 7. El expediente archivado no pierde el vínculo

Es el caso que más fácilmente se rompe en silencio.

1. Ir a `/judiciales`, poner «Registros: Ocultos» y abrir un expediente **archivado**.

> Si la lista sale vacía, es por lo mismo del paso 2: **tampoco hay pantalla para
> archivar un expediente**. La ruta existe en el servidor pero ningún botón la usa. Para
> probar este paso hace falta marcar un expediente como oculto directamente en la base:
>
> ```sql
> UPDATE sistema_juridico.judicial_case SET active = false WHERE case_number = 'EXP-…';
> ```
>
> El paso merece probarse igualmente: es el que esconde un fallo silencioso.
2. Pulsar «+ Crear nuevo pendiente relacionado».

**Se espera**: el desplegable trae ese expediente aunque esté archivado.

3. Guardar y abrir la ficha del pendiente creado.

**Se espera**: el vínculo está.

**Falla si**: el desplegable sale vacío o el pendiente se guarda sin expediente. Ese es exactamente el fallo silencioso: no da error, simplemente pierde el dato.

---

## 8. «Ver todos» muestra lo mismo, y más

1. Buscar (o crear) un expediente con **más de 25 pendientes**.
2. Abrir su ficha.

**Se espera**: la lista se corta y avisa de que hay más, con un enlace **«Ver todos»**.

3. Seguir el enlace.

**Se espera**: el listado de pendientes con ese expediente ya filtrado, el nombre del expediente visible en pantalla, y las primeras filas **en el mismo orden** que en la ficha.

---

## 9. El filtro sobrevive

Desde el listado filtrado del paso anterior:

1. Pasar a la **página siguiente** → sigue filtrado.
2. Cambiar el **orden** → sigue filtrado.
3. Cambiar «Registros» a «Todos» y pulsar **«Aplicar filtros»** → **sigue filtrado**.

**Falla si**: en el paso 3 el filtro desaparece. Ese paso es el que importa: el formulario de filtros va por un camino distinto que los enlaces de paginación.

4. Pulsar **«Quitar filtros»** → vuelven todos los pendientes.

---

## 10. Un identificador inventado no rompe nada

1. En la barra del navegador: `/pendientes?judicialCaseId=00000000-0000-0000-0000-000000000000`

**Se espera**: lista vacía con un aviso. No una página de error.

2. Ahora: `/pendientes/nuevo?judicialCaseId=00000000-0000-0000-0000-000000000000`

**Se espera**: se rechaza con un mensaje. No un error 500, y desde luego no un formulario que al guardar produciría un vínculo roto.

---

## 11. Lo mismo en la ficha administrativa

Repetir los pasos 1, 3, 4 y 8 en `/administrativos/{id}`. El comportamiento es el mismo.

---

## 12. La ficha no se ha vuelto lenta

1. Abrir la ficha de un expediente con muchos pendientes.

**Se espera**: se muestra sin espera perceptible (el objetivo medido es por debajo de medio segundo con 50 vínculos).

**Falla si**: se nota más lenta que la de un expediente con dos o tres. Eso apuntaría a una consulta por fila, que es justo lo que el diseño evita y lo que comprueba la prueba de presupuesto.

*Ya medido*: con 50 vínculos, p95 = 1 ms de servidor, y el mismo número de consultas
—5— que con dos. Este paso es para confirmar la sensación, no para cronometrar.

---

## Si algo falla

Anotar el paso y lo que se vio. Los pasos 7, 9.3 y 10 son los tres que cubren fallos silenciosos —los que no dan error pero pierden datos o filtros—, así que conviene informarlos con detalle.

---

## Nota: no se puede archivar nada desde la interfaz

Salió al ejecutar este recorrido y **es anterior a esta feature**, pero conviene decidirlo:

| Entidad | Filtro «Ocultos» en pantalla | Ruta en el servidor | Botón que la use |
|---|---|---|---|
| Expediente judicial | sí | `POST /judiciales/{id}/visibilidad` | **ninguno** |
| Procedimiento administrativo | sí | `POST /administrativos/{id}/visibilidad` | **ninguno** |
| Pendiente | sí | **no existe** | — |

Las tres pantallas ofrecen filtrar por un estado que nada puede producir. El de
pendientes viene de la funcionalidad de pendientes; los otros dos, de las de
expedientes. Resolverlo es su propia feature —hay que decidir quién puede archivar, si
se pide motivo y qué queda en el historial—, no un añadido a esta.
