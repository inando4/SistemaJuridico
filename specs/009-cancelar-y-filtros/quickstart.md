# Comprobación manual: Cancelar registros y los filtros que faltan

Recorrido para verificar la funcionalidad 009 en el navegador. Unos quince minutos.

**Antes de empezar**

```bash
./mvnw spring-boot:run
```

Entrar en <http://localhost:8080>. Hace falta **una cuenta de abogado y la de la jefa**:
parte del recorrido comprueba justamente qué ve cada una.

---

## 1. Cancelar un pendiente desde su ficha

1. Abrir un pendiente propio en `/pendientes`.
2. Usar **«Cancelar»**.

**Se espera**: vuelve a la ficha o al listado y el pendiente ya no está en la lista de
trabajo.

**Falla si**: pide un motivo (no debe), o si desaparece sin dejar rastro (paso 3).

---

## 2. Sigue existiendo y se puede devolver

1. En `/pendientes`, poner **«Registros: Ocultos»**.
2. Buscar el pendiente cancelado.

**Se espera**: está ahí.

3. Devolverlo a la lista.
4. Quitar el filtro de ocultos.

**Se espera**: ha vuelto al listado de trabajo.

**Por qué importa**: cancelar es una corrección, no una condena. Si no se pudiera
deshacer, nadie se atrevería a usarlo.

---

## 3. Queda constancia de quién y cuándo

1. Abrir el historial del pendiente que acaba de ir y volver.

**Se espera**: dos entradas nuevas, la cancelación y la devolución, cada una con su autor
y su fecha.

**Falla si**: no aparecen. La constitución no permite retirar nada sin rastro.

---

## 4. Cancelar desde la fila, sin abrir el pendiente

1. Ir a `/pendientes` y **aplicar algún filtro** —por ejemplo, ordenar por título.
2. Pasar a la **página 2**, si la hay.
3. Cancelar un pendiente **desde su fila**, sin abrirlo.

**Se espera**: vuelve a **la página 2 con el mismo filtro y el mismo orden**.

**Falla si**: vuelve a la página 1, o pierde los filtros. Es el fallo silencioso de este
recorrido: no da error, sólo obliga a recomponerlo todo cada vez.

---

## 5. Si esa era la última fila, la página puede quedar vacía

1. Colocarse en la última página de un listado filtrado.
2. Cancelar o cumplir la única fila que quede.

**Se espera**: vuelve a esa misma página, ahora vacía, con su enlace para quitar filtros o
volver atrás.

**No es un error**: el listado muestra el trabajo que queda, y ese ya no queda. Se
comprueba para que nadie lo denuncie como fallo.

---

## 6. Cada persona ve sólo lo que puede hacer

1. Entrar **como abogado**.
2. Buscar en `/pendientes` uno cuyo responsable sea **otra persona** (filtrando por
   responsable, que es nuevo).

**Se espera**: en esa fila **no** aparecen los botones de cumplir ni cancelar. El título
sí enlaza: leer es compartido.

3. Entrar **como jefa** y mirar la misma fila.

**Se espera**: ahí sí aparecen.

---

## 7. Un pendiente ya cumplido no se ofrece cumplir otra vez

1. Localizar un pendiente cumplido (en `/cumplidos`, o filtrando).

**Se espera**: su fila no ofrece «Marcar como cumplido».

---

## 8. Ocultar un expediente, y que sus pendientes no se enteren

1. Abrir la ficha de un expediente judicial que **tenga pendientes activos**.
2. Anotar cuántos hay en su bloque «Pendientes relacionados».
3. Usar la acción de **ocultar** el expediente.
4. Ir a `/pendientes`.

**Se espera**: esos pendientes **siguen en la lista de trabajo**, intactos.

**Falla si**: han desaparecido. Ocultar un expediente no puede retirar en cascada el
trabajo de otras personas.

5. Volver a `/judiciales` con «Registros: Ocultos» y **volver a mostrarlo**.

---

## 9. Estado «Archivado» y visibilidad son cosas distintas

1. Abrir un procedimiento administrativo y ponerle el estado **«Archivado»**.
2. Volver a `/administrativos` con los filtros por defecto.

**Se espera**: **sigue apareciendo**. El estado describe el trámite; la visibilidad dice
si estorba en la lista. Son dos ejes.

---

## 10. Los filtros de la sección 27

En `/judiciales`, comprobar que se puede filtrar **sin tocar la barra de direcciones** por:

- Abogado responsable
- Estado procesal
- Materia
- Sólo los vencidos
- Con fecha límite / sin fecha límite

1. Aplicar **dos a la vez** (por ejemplo, una responsable y sólo vencidos).

**Se espera**: se cumplen los dos.

2. Pasar de página y cambiar el orden.

**Se espera**: los filtros siguen puestos.

3. Usar **«Quitar filtros»**.

**Se espera**: vuelve el listado completo.

---

## 11. Una cuenta desactivada sigue siendo buscable

Es el caso que más fácilmente se pasa por alto.

1. Desactivar una cuenta que **tenga expedientes o pendientes** (desde `/usuarios`, como
   jefa).
2. Ir a `/judiciales` y abrir el desplegable de responsable.

**Se espera**: esa persona **sigue en la lista**, marcada como desactivada.

3. Filtrar por ella.

**Se espera**: aparecen sus expedientes.

**Falla si**: no está en el desplegable. Entonces su trabajo se ha vuelto inencontrable y
parece de nadie.

---

## 12. Lo mismo en las otras dos listas

Repetir el paso 10 en `/administrativos` (responsable, estado, vencidos) y en
`/pendientes` (responsable, tipo, prioridad, estado, vencidos).

---

## 13. Las listas no se han vuelto lentas

1. Abrir `/pendientes` con bastantes registros.

**Se espera**: se muestra sin espera perceptible.

**Falla si**: se nota más lenta que antes de esta funcionalidad. Los desplegables de
filtro se leen de una vez por pantalla, no uno por desplegable; si eso se rompiera, se
notaría aquí y lo delataría la prueba de presupuesto.

---

## Si algo falla

Anotar el paso y lo que se vio. Los pasos **4, 8 y 11** son los que cubren fallos
silenciosos —los que no dan error pero pierden el contexto, retiran de más o esconden
trabajo—, así que conviene informarlos con detalle.
