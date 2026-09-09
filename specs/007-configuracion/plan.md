# Plan de implementación: Configuración

**Rama**: `007-configuracion` | **Fecha**: 2026-09-08 | **Spec**: [spec.md](spec.md)

## Resumen

Una pantalla de enlaces en `/configuracion`, un enlace en la navegación, y la retirada de una comprobación que se queda sin objeto.

**Sin migración, sin consultas, sin cambios en ninguna pantalla de destino.** Es la feature más pequeña del proyecto, y su valor está en el hallazgo que la motiva: cinco pantallas de administración no tienen hoy ningún enlace entrante.

## Contexto técnico

**Lenguaje**: Java 21, Spring Boot 4.1.1. **Plantillas**: Thymeleaf.

**Almacenamiento**: ninguno. La pantalla no lee ni escribe.

**Pruebas**: Surefire para `*Test.java`, Failsafe para `*IT.java`; Playwright para el recorrido.

**Presupuesto**: `GET /configuracion` con **cero consultas propias**. Es el único sitio del sistema donde el número correcto es cero y cualquier otro significa que algo se coló —una lista de catálogos leída para pintar los enlaces, por ejemplo—.

## Comprobación de la constitución

| Principio | Cómo lo cumple |
|---|---|
| **I. Idioma** | Ruta y textos en español (`/configuracion`); clase en inglés (`SettingsController`). |
| **II. Multiusuario y atribución** | Todos leen la pantalla. El enlace a cuentas se oculta a quien no es jefa **por cortesía**; la autorización sigue en `UserAdminController`, que no se toca. Hay prueba de que el rechazo del servidor sigue en pie. |
| **III. Índice físico y datos** | No toca datos. |
| **IV. Ligereza** | Cero consultas, con prueba. |
| **V. Sin derivados persistidos** | No persiste nada. |
| **VI. Días hábiles centralizados** | No calcula nada. |
| **VII. Trazabilidad** | No hay operaciones que auditar. Las de las pantallas de destino siguen auditándose donde ya lo hacían. |
| **VIII. Un solo proceso** | Ninguna dependencia nueva. |

**Resultado: pasa.** Sin violaciones.

## Estructura del proyecto

```text
src/main/java/pe/org/beneficencia/legalcontrol/
└── config/SettingsController.java          # NUEVO — GET /configuracion

src/main/resources/templates/
├── settings/index.html                     # NUEVO
└── fragments/navegacion.html               # un enlace mas

src/test/java/pe/org/beneficencia/legalcontrol/
├── web/ConfiguracionContractTest.java      # NUEVO
├── integration/ConfiguracionQueryBudgetIT.java  # NUEVO
└── acceptance/RutasSegunInsumoTest.java    # la reserva se invierte
```

**Decisión de estructura**: el controlador va en `config`, junto a `ClockConfig`, `SeedCatalogsCommand` y `SeedHolidaysCommand` — es donde vive lo que configura el sistema. No merece paquete propio: una clase sin repositorio ni servicio no es un dominio.

## Lo que hay que quitar, no solo añadir

`RutasSegunInsumoTest.sinInvadirRutasReservadas` recorre hoy `List.of("/configuracion")`, la última reserva viva. Al ocupar la ruta, ese bucle quedaría **sobre una lista vacía**: no afirmaría nada y pasaría en silencio, que es peor que no tenerlo.

Se sustituye por una comprobación que sí dice algo: que cada destino que enlaza `/configuracion` **existe como ruta servida**. Así el mecanismo que impedía invadir rutas reservadas se convierte en el que impide dejar enlaces rotos, que es el riesgo que esta pantalla introduce.

## Seguimiento de complejidad

Sin violaciones que justificar.

## Nota fuera de alcance

De la **FASE 2** quedan §38 (exportación) y §39 (estadísticas). Las dos necesitan una respuesta del cliente antes de especificarse, por los motivos anotados en el informe al usuario. No se empiezan sobre una respuesta supuesta.
