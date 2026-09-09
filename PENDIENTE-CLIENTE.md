# Lo que hace falta preguntar o hacer

Cosas que no dependen del código: necesitan una decisión de la jefatura o una acción
en el sistema ya desplegado. Se anotan aquí para que no se pierdan entre features.

Actualizado: 2026-09-08, al cerrar la funcionalidad 007.

---

## 1. ¿Qué significa «exportar»? — bloquea la sección 38

La sección 38 del insumo dice «Exportar a Excel» para judiciales, administrativos,
pendientes, cumplidos y resultados filtrados. **Esa frase describe tres trabajos
distintos**, y hasta saber cuál es no se puede especificar la feature:

| Si la jefa quiere… | Lo que hay que construir |
|---|---|
| **Seguir usando Excel en paralelo** | Un volcado fiel: todas las columnas, un registro por fila, sin formato. Lo más simple |
| **Enviar un informe a Gerencia** | Un documento con formato: encabezados, totales, quizá el logo. Hay que preguntarle además qué columnas quiere y cuáles sobran |
| **Tener una copia de respaldo** | No es un Excel: es otra cosa, y probablemente pertenece al despliegue y no al sistema |

**Qué preguntarle**: «Cuando exportes a Excel, ¿qué vas a hacer con el archivo?»
La respuesta a esa pregunta decide las tres cosas.

Una segunda pregunta, si contesta lo primero o lo segundo: **¿exportar lo que se ve en
pantalla con sus filtros, o la tabla entera?** El insumo menciona «resultados filtrados»,
pero conviene confirmarlo.

## 2. ¿Estadísticas ahora o después? — bloquea la sección 39

**El insumo se contradice consigo mismo**, y conviene resolverlo antes de planificar:

- La sección 43 pone «Estadísticas» en la **FASE 2**, junto al calendario y el buscador.
- La sección 39 se titula «ESTADÍSTICAS **FUTURAS**» y solo pide *«dejar preparada la
  información necesaria para calcular posteriormente»*.

**Eso último ya está cumplido.** Todo lo que la sección 39 enumera es derivable de lo que
el sistema guarda: recibidos y cumplidos por mes salen de las fechas de recepción y
cumplimiento, dentro o fuera de plazo de compararlas con la fecha límite, el número de
reprogramaciones del historial, y los expedientes activos o concluidos de su estado. No
falta ningún dato, y por el principio V de la constitución no deben guardarse calculados.

**Qué preguntarle**: «¿Quieres pantallas de estadísticas ahora, o prefieres esperar a
tener unos meses de datos reales?» Con dos semanas de uso, cualquier gráfico dirá poco.

## 3. Confirmar los feriados — afecta a lo que ya está en producción

Los feriados oficiales del Perú están cargados en `/dias-no-laborables`, pero **falta que
la jefa los revise y confirme el año**. Hasta que lo haga, todos los recuentos que
dependen de días hábiles muestran «Cálculo no disponible» en vez de un número: es
deliberado —el sistema avisa en lugar de calcular en silencio con un calendario sin
revisar— pero significa que hoy hay columnas del panel y de la vista de equipo que no
enseñan nada.

**Es lo más urgente de esta lista**, porque no bloquea trabajo futuro: bloquea
funcionalidad ya desplegada.

Conviene que revise en particular el **15 de agosto (aniversario de Arequipa)**, que se
carga solo si se pide expresamente. Que un plazo procesal se detenga ese día depende del
Poder Judicial y de la entidad, no de un programa, así que la decisión es suya.

## 4. Integración continua — decisión tuya, no de la jefa

Quedó aparcado montar GitHub Actions para que las pruebas se ejecuten solas en cada
cambio. Hoy se ejecutan a mano con `./mvnw verify`.

---

## Lo que NO está pendiente de nadie

Para que no se confunda con lo anterior:

- **FASE 3 completa** —notificaciones por correo y de navegador, recordatorios
  automáticos y reportes automáticos— está fuera de alcance por decisión del propio
  insumo, no por falta de respuesta.
- La **sección 37** (migración del Excel actual) el insumo la declara fuera de alcance.
