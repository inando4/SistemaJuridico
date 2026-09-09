# Fase 1 — Contrato de pantallas

## `GET /configuracion`

Sin parámetros.

**Respuesta**: `settings/index.html`.

| Bloque | Destino | Quién lo ve |
|---|---|---|
| Días no laborables y feriados | `/dias-no-laborables` | Cualquier cuenta activa |
| Tipos de pendiente | `/tipos-de-pendiente` | Cualquier cuenta activa |
| Prioridades | `/prioridades` | Cualquier cuenta activa |
| Estados de pendiente | `/estados-de-pendiente` | Cualquier cuenta activa |
| Estados procesales | `/estados-procesales` | Cualquier cuenta activa |
| Estados de procedimientos administrativos | `/estados-administrativos` | Cualquier cuenta activa |
| Cuentas de usuario | `/usuarios` | **Solo la jefatura** |

Cada bloque lleva una línea que dice para qué sirve la pantalla (RF-006).

**Sin sesión**: redirección al acceso, como el resto del sistema.

**La asimetría de la última fila es real y comprobada**, no una decisión de estilo: `CatalogController.listado(...)` no exige jefatura —cualquiera lee los catálogos y la plantilla oculta el formulario de alta—, mientras que `UserAdminController` la exige ya en el `GET`. Enseñar un enlace que siempre devuelve rechazo sería peor que no enseñarlo.

**Ocultar el enlace no es la autorización.** `/usuarios` sigue rechazando a quien no es jefa exactamente como hoy, y hay prueba de ello: es la lección que la 005 dejó cuando `th:replace` se comió un `th:if` y un formulario apareció para quien no debía.

## Cambio en la navegación

`fragments/navegacion.html` gana un enlace **Configuración**, marcado con `aria-current` cuando es la sección en curso, igual que los demás.

## Presupuesto

| Operación | Presupuesto |
|---|---|
| `GET /configuracion` | **Cero consultas propias.** Solo las de sesión y plantilla común |

Es el único presupuesto del sistema expresado como un cero. Si algún día sube, será porque alguien decidió pintar los enlaces leyendo algo de la base, y eso es exactamente lo que esta prueba tiene que impedir.
