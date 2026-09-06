# Contrato de interfaz web

**Alcance**: HTML servido por el JAR para navegadores. No API REST JSON, cliente SPA,
servicio de frontend ni endpoints para asignación/pendientes. Rutas y parámetros en inglés;
etiquetas, títulos, validaciones y mensajes en español.

## Convenciones de navegación y respuesta

- GET devuelve HTML completo. Con `HX-Request: true`, devuelve el fragmento de destino;
  `HX-History-Restore-Request` siempre recupera documento completo. Declarar `Vary` correcto.
- POST con `application/x-www-form-urlencoded`, token `_csrf` y `version` en edición.
  Éxito HTMX: 200 con formulario/ficha/listado actualizado y mensaje, una sola petición.
  Éxito sin HTMX: 303 a un GET permitido. No redirecciones externas tomadas del formulario.
- 422: errores de campos, sin cambio parcial; conservar valores no secretos.
  409: versión obsoleta, duplicado, estado referenciado, calendario cambiado o última JEFA.
  Mostrar causa y acción permitida, sin sobrescribir datos automáticamente.
- 401 o sesión vencida: para HTMX respuesta con `HX-Redirect: /login`; navegación normal
  redirige al acceso. 403 para permisos o CSRF rechazados; 404 para entidad inexistente.
  429 con `Retry-After` en login/admin; recuperación pública conserva respuesta genérica
  equivalente para todo correo, incluidos límites. 503 para fallo temporal sin confirmar guardado.
- Configurar HTMX para intercambiar 409/422 como HTML válido y presentar 403/503 en el
  área de errores mediante adaptación JS mínima externa. No confiar en sus defaults.
  La pérdida de red conserva el formulario y permite reintentar; no afirma éxito.
- Solo GET no muta dominio; leer listados, fichas y evidencia inserta cero historial.
  No-op POST no cambia versión/updated_at ni evidencia. CSRF y mantenimiento de sesión
  son metadatos de seguridad, independientes del historial de negocio.
- `Cache-Control: no-store` en documentos/fragmentos privados y enlaces de acceso;
  HTMX sin caché local de historial. Al restaurar desde bfcache se revalida acceso antes
  de mostrar datos privados. Recursos estáticos versionados sí admiten caché prolongada.
- HTML semántico, labels asociados, teclado, foco visible, mensaje de error conectado al
  campo y región de confirmación accesible. Alertas temporales siempre textuales; no
  depender de color. Idioma `es-PE`, fechas visibles `dd/MM/yyyy`, valores de formulario ISO.

## Acceso y cuentas

| Método / ruta | Permiso | Entrada y salida |
| --- | --- | --- |
| GET `/login` | Público | Correo, contraseña, recuperar acceso; nunca registro público |
| POST `/login` | Público + CSRF | `email`, `password`; sesión nueva o error genérico |
| POST `/logout` | Identificado + CSRF | Invalida sesión y cookie; vuelve a login |
| GET `/access/reset` | Público | Formulario de correo |
| POST `/access/reset` | Público + CSRF | `email`; respuesta genérica «Si la cuenta permite recuperar el acceso, recibirás instrucciones» |
| GET `/access/activate?token=…` | Enlace | Presenta contraseña y confirmación sin consumir token |
| POST `/access/activate` | Token + CSRF | `token`, `password`, `passwordConfirmation`; activa cuenta e invita a ingresar |
| GET `/access/reset/complete?token=…` | Enlace | Formulario de contraseña nueva |
| POST `/access/reset/complete` | Token + CSRF | Mismos campos; cambia contraseña e invalida sesiones |
| GET `/access/reactivate?token=…` | Enlace | Formulario, contraseña distinta de la última |
| POST `/access/reactivate` | Token + CSRF | Activa cuenta pendiente, consume token y exige login |
| GET `/users` | HEAD | Nombre, correo, rol, condición; acciones según estado |
| GET `/users/new` | HEAD | Formulario de alta |
| POST `/users` | HEAD + CSRF | `name`, `email`, `role` (`LAWYER`/`HEAD`); cuenta pendiente, envío solicitado |
| POST `/users/{id}/resend-invitation` | HEAD + CSRF | `version`; solo PENDING_ACTIVATION |
| POST `/users/{id}/deactivate` | HEAD + CSRF | `version`, confirmación; estado INACTIVE salvo última HEAD activa |
| POST `/users/{id}/reactivate` | HEAD + CSRF | `version`; solo INACTIVE, pasa a PENDING_REACTIVATION |
| POST `/users/{id}/resend-reactivation` | HEAD + CSRF | `version`; solo PENDING_REACTIVATION |

Los GET de enlaces no cambian estado ni consumen autorización. Escáneres de correo no
activan cuentas. Tokens no se incluyen en encabezados Referer: `Referrer-Policy: no-referrer`;
no recursos externos en estas páginas, logs excluyen query strings y cuerpos secretos.
Caducidad y único uso según [modelo](../data-model.md). Error genérico de token no expone cuenta.
No exponer CRUD general de cuentas ni cambio de rol; no hay eliminación de usuarios.
Desactivación/solicitud de reactivación piden confirmación informando el efecto, conservando datos.

## Procesos judiciales

| Método / ruta | Permiso | Contrato |
| --- | --- | --- |
| GET `/judicial-cases` | Ambos roles activos | Listado y filtros, página de 25 |
| GET `/judicial-cases/new` | Ambos | Formulario; responsable mostrado como usuario actual |
| POST `/judicial-cases` | Ambos + CSRF | Alta manual; propietario fijado por servidor |
| GET `/judicial-cases/{id}` | Ambos | Datos, responsable, interpretación de fechas, primera página de historia |
| GET `/judicial-cases/{id}/edit` | Propietario o HEAD | Formulario con version |
| POST `/judicial-cases/{id}` | Propietario o HEAD + CSRF | Edición validada; 409 por conflicto/duplicado |
| POST `/judicial-cases/{id}/visibility` | Propietario o HEAD + CSRF | `active`, `version`; no altera estado procesal |
| GET `/judicial-cases/{id}/history` | Ambos | Historia paginada, solo lectura |

Filtros GET: `q` (número/partes), `ownerId`, `proceduralStatusId`, `subject`,
`deadlinePresence=any|with|without`, `overdue=true|false`, `visibility=active|inactive|all`,
`sort=caseNumber|owner|deadline`, `direction=asc|desc`, `page` desde cero.
Predeterminado: todos los responsables, activos, cualquier estado, número ascendente.
`Concluido` es una selección de `proceduralStatusId`, no alias de visibilidad.
Resolver filtros en una sola petición al enviar; conservarlos al paginar y volver a listar.
Página fuera de rango devuelve vacío recuperable; valores de filtro no válidos devuelven 422.

Formularios: `sequenceNumber`, `caseNumber`, `claimant`, `respondent`, `subject`,
`proceduralStatusId`, `lastProceduralAction`, `lastActionDate`, `nextProceduralAction`,
`deadline`, `amount`, `propertyAddress`, `notes`, `managementActions`, `active`, `version`.
En alta active=true. `ownerId`, ids técnicos y tiempos no son entradas editables;
rechazar intentos de asignación, no ignorar silenciosamente una transferencia solicitada.
Campo repetido/corrupto o texto fuera del presupuesto se rechaza, sin truncar.
Monto admite coma o punto decimal en pantalla, sin separadores de miles ambiguos;
normalizar a BigDecimal exacto, máximo dos decimales, moneda PEN visible.

La ficha muestra todos los datos opcionales, con ausencia explícita cuando proceda.
Para fecha límite: fecha exacta y «Vence hoy», «Vencido», número hábil o «Sin fecha límite».
Si falta cobertura, «Cálculo no disponible: revisar días no laborables» y años faltantes;
comparación hoy/pasado se conserva. Límite no hábil no se desplaza. Fecha de referencia
visible en listado y ficha; fechas de actos/auditoría incluyen relación temporal pertinente.
No enlaces de archivo, pendientes, exportación, asignación o dashboard de equipo.

## Días no laborables y revisión anual

| Método / ruta | Permiso | Contrato |
| --- | --- | --- |
| GET `/non-working-days?year=2026` | Ambos | Lista del año, cantidad calculada y estado de revisión |
| GET `/non-working-days/new?year=2026` | HEAD | Formulario |
| POST `/non-working-days` | HEAD + CSRF | `day`, `description`, `kind`; alta de fecha única |
| GET `/non-working-days/{id}/edit` | HEAD | Formulario con version |
| POST `/non-working-days/{id}` | HEAD + CSRF | Mismos campos + version; invalida revisiones afectadas |
| POST `/non-working-days/{id}/delete` | HEAD + CSRF | version + confirmación; retira fecha, conserva evidencia |
| GET `/non-working-days/{year}/review` | HEAD | Año, cantidad y revisión técnica observada |
| POST `/non-working-days/{year}/review` | HEAD + CSRF | `revision`, `fullYearReviewed`, `lowCountAcknowledged` |

`fullYearReviewed` se muestra sin marcar junto a «He revisado el calendario completo de
este año». Cero entradas impide confirmar. De una a cuatro se muestra aviso de cantidad
inusualmente baja y reconocimiento adicional sin marcar. Cinco o más no elimina la
obligación de revisión. El servidor calcula la cantidad y revalida la revisión; no recibe
un total como autoridad. Si cambió el calendario, 409 con versión actual y declaración
sin seleccionar. HEAD debe revisar nuevamente. La lista es administración de días, no un
calendario gráfico de actividades. Tipo se representa en inglés internamente y español en vista.

## Estados procesales

| Método / ruta | Permiso | Contrato |
| --- | --- | --- |
| GET `/procedural-statuses` | Ambos | Catálogo vacío o lista con disponibilidad |
| GET `/procedural-statuses/new` | HEAD | Formulario |
| POST `/procedural-statuses` | HEAD + CSRF | `name`, `description`, `enabled` |
| GET `/procedural-statuses/{id}` | Ambos | Detalle |
| GET `/procedural-statuses/{id}/edit` | HEAD | Edición |
| POST `/procedural-statuses/{id}` | HEAD + CSRF | Campos + version |
| POST `/procedural-statuses/{id}/availability` | HEAD + CSRF | `enabled`, `version` |
| POST `/procedural-statuses/{id}/delete` | HEAD + CSRF | version + confirmación; solo sin usos actuales/históricos de procesos |

Rechazar nombre duplicado; 409 al eliminar referenciado con opción de deshabilitar.
Un estado deshabilitado continúa apareciendo en ficha/filtro e historia y puede conservarse
en un proceso existente. No se ofrece como nueva elección; otro formulario que lo
seleccionó antes de deshabilitarlo se revalida al guardar. No renombrar snapshots de historia.

## Pruebas de contrato

Verificar ambas representaciones HTML, CSRF, 401/403, 409/422 visibles con HTMX, no-op,
ausencia de endpoints fuera de alcance y matriz de permisos por solicitud directa.
Medir exactamente una petición por filtro/página/guardado HTMX; los enlaces de login y
saltos a otra pantalla pueden navegar normalmente. La autorización nunca depende de HTMX.
