# Correcciones de la auditoría de código y UX/UI

**Sistema Jurídico · 27 de septiembre de 2026 · America/Lima**

Se corrigieron los **11 hallazgos** de la [auditoría inicial](auditoria-2026-09-27.md). La revisión empleó Graphify para las relaciones del código, Impeccable para recuperación de errores y adaptación de la interfaz, y Playwright MCP para comprobar los recorridos reales.

## Resultado por hallazgo

| ID | Corrección | Comprobación |
|---|---|---|
| F01 | Cancelar y devolver abren una transacción que incluye el cambio y su auditoría. También se protegió la entrada de alta administrativa con responsable explícito. | Tres regresiones con fallo inducido en PostgreSQL; cancelación desde el navegador con respuesta 500 y estado, versión e historial intactos. |
| F02 | Las altas judiciales y administrativas conservan los candidatos y el responsable seleccionado al corregir errores. Un destinatario no disponible produce un error explícito. | Error por número duplicado → corrección → guardado: ambos registros quedaron a nombre de la abogada elegida. |
| F03 | La edición ofrece el valor histórico deshabilitado, identificado como tal. El servidor permite conservarlo y rechaza nuevas asociaciones a valores retirados o inexistentes. | Conservación del tipo al editar observaciones en MCP; regresiones para tipo, prioridad, estado del pendiente y estados judicial/administrativo. Se aplicó también a actividades. |
| F04 | La edición fallida de una actividad conserva ID, versión, valores, errores y formulario abierto. El alta permanece independiente. | Reintento completo en MCP y regresión HTTP; consulta posterior confirmó una sola actividad corregida. |
| F05 | Tablas en regiones identificadas y accesibles con teclado, columnas con ancho mínimo, calendario legible y desplazamiento contenido. Se añadieron acceso al contenido y tamaños táctiles en móvil. | 168 visitas: sin desbordamiento del documento, cabeceras legibles, regiones nombradas y controles visibles etiquetados en ambos modos. Desplazamiento de la tabla comprobado. |
| F06 | Errores junto a cada campo, asociaciones ARIA, límites de longitud y resumen enlazado que dirige el foco. El texto ingresado se conserva. | Descripción de 10.001 caracteres: explicación específica, texto íntegro y foco en el campo al activar el enlace. Regresiones adicionales de longitud. |
| F07 | El retorno acepta la codificación de espacios y conserva la consulta en fichas, edición, historial y acciones, con destinos locales fijos. | Búsqueda con espacios después de cancelar y contexto después de editar; regresiones para espacios, tildes y caracteres codificados. |
| F08 | Página general de errores en español, con recuperación y código HTTP original, sin detalles técnicos. | Respuestas reales 400, 404 y 500 en MCP; siete casos HTTP automatizados. |
| F09 | El desplazamiento de paginación se calcula como `long`, sin multiplicación intermedia de enteros. | Página 100.000.000 devuelve un listado vacío con HTTP 200; pruebas hasta el mayor entero admitido. |
| F10 | Fechas y horas de historial/cumplimiento en hora de Perú; etiquetas comprensibles para cancelar y devolver. | Un instante UTC se mostró en el día local anterior; historial real con ambas acciones traducidas. |
| F11 | La ayuda explica la asignación disponible según el rol y el recorrido de reasignación. | Textos verificados durante ambas altas con responsable elegido. |

## Pruebas automatizadas

Comando de comprobación completa, incluyendo las pruebas visuales opcionales:

```sh
./mvnw -B -q -Dux.audit=true -Dui.capture=true verify
```

| Grupo | Clases | Casos | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|---:|
| Surefire | 61 | 441 | 0 | 0 | 0 |
| Failsafe | 82 | 370 | 0 | 0 | 0 |
| **Total** | **143** | **811** | **0** | **0** | **0** |

Se añadieron **32 casos** respecto de los 779 de la auditoría inicial. La compilación y el empaquetado forman parte de `verify`. [Detalle por clase](correcciones-2026-09-27/test-summary.json).

Regresiones incorporadas:

- [AuditoriaAtomicidadIT.java](../src/test/java/pe/org/beneficencia/legalcontrol/integration/AuditoriaAtomicidadIT.java): cancelación, devolución y alta administrativa ante un fallo de auditoría.
- [AuditoriaRegresionesTest.java](../src/test/java/pe/org/beneficencia/legalcontrol/web/AuditoriaRegresionesTest.java): responsables, catálogos, recuperación de edición, validación, navegación, paginación y errores HTTP.
- [AuditoriaUtilidadesTest.java](../src/test/java/pe/org/beneficencia/legalcontrol/shared/AuditoriaUtilidadesTest.java): cálculo de desplazamiento, codificación del retorno y conversión de fecha/hora.
- [ModosPresentacionTest.java](../src/test/java/pe/org/beneficencia/legalcontrol/acceptance/ModosPresentacionTest.java): tablas pobladas a 320, 390 y 1440 px en ambos modos, incluidas las fichas.

Se actualizaron tres expectativas anteriores para reconocer los mensajes localizados, el error específico de fecha y los atributos accesibles del selector. La suite conserva sus verificaciones de comportamiento.

## Exploración con Playwright MCP

Servidor MCP `1.63.0-alpha-2026-08-31`, Chromium, aplicación local y PostgreSQL temporal independiente, con datos sintéticos. El barrido final cubrió **28 rutas × 3 anchuras × 2 modos = 168 visitas**, todas HTTP 200. Las pruebas negativas de errores HTTP se ejecutaron por separado.

- **0** excepciones JavaScript durante el barrido.
- **0** desbordamientos horizontales del documento.
- **0** tablas sin una región identificada y accesible con teclado.
- **0** controles visibles sin etiqueta en las páginas examinadas.
- Revisión visual de capturas de escritorio, móvil, validación y corrección de actividad.

[Métricas de las 168 visitas](correcciones-2026-09-27/pantallas.json) · [Resultados funcionales](correcciones-2026-09-27/resumen-playwright.json).

Dos comprobaciones iniciales del ejecutor usaban `URL` fuera del contexto de página. Se corrigió la ejecución y se repitieron satisfactoriamente; el resumen conserva esta incidencia del instrumento. La inspección inicial de las correcciones detectó una ficha sin contenedor de tabla; se añadió y el barrido final confirmó todas las rutas.

### Evidencias seleccionadas

| Comprobación | Evidencia |
|---|---|
| Responsable retenido | [Judicial](correcciones-2026-09-27/judiciales-responsable.png), [administrativo](correcciones-2026-09-27/administrativos-responsable.png) |
| Catálogo histórico | [Tipo conservado](correcciones-2026-09-27/catalogo-conservado.png) |
| Edición corregible | [Actividad abierta con sus errores](correcciones-2026-09-27/actividad-corregible.png) |
| Validación comprensible | [Campo, explicación y resumen](correcciones-2026-09-27/validacion-explicada.png) |
| Tablas móviles | [Estándar](correcciones-2026-09-27/estandar-390-cumplidos.png), [Rendimiento](correcciones-2026-09-27/rendimiento-390-cumplidos.png), [desplazamiento hasta las últimas columnas](correcciones-2026-09-27/estandar-390-cumplidos-derecha.png) |
| Ficha móvil | [Pendiente](correcciones-2026-09-27/estandar-390-ficha-pendiente.png) |
| Calendario móvil | [Estándar](correcciones-2026-09-27/estandar-390-calendario.png), [Rendimiento](correcciones-2026-09-27/rendimiento-390-calendario.png) |
| Historia local y recuperación | [Historial](correcciones-2026-09-27/historial-localizado.png), [error controlado](correcciones-2026-09-27/error-controlado.png) |
| Atomicidad real | [Antes](correcciones-2026-09-27/atomicidad-antes.txt), [después](correcciones-2026-09-27/atomicidad-despues.txt): `active=true`, `version=1`, cero eventos `CANCEL` en ambos estados |

## Alcance del cierre

Los once hallazgos reproducidos quedan corregidos y comprobados en el entorno local descrito. Se conservan los dos modos visuales existentes y no se añadieron dependencias de interfaz. La evidencia del navegador corresponde a Chromium; no se realizó una certificación completa de accesibilidad ni una prueba de carga.

Al terminar se retiraron los disparadores de fallo, se cerró el navegador MCP y se detuvieron la aplicación y el contenedor de PostgreSQL temporales.
