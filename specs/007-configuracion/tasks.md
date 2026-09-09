---

description: "Tareas de la 007 — configuración"
---

# Tareas: Configuración

**Entrada**: [plan.md](plan.md), [spec.md](spec.md), [contracts/pantallas.md](contracts/pantallas.md)

**Pruebas**: incluidas (principio IV y VIII).

## Formato: `[ID] [P?] [Historia] Descripción`

---

## Fase 1: Preparación

- [ ] T001 Comprobar que la rama `007-configuracion` parte de `main` con `./mvnw verify` en verde

## Fase 2: Historia 1 — Llegar a la administración desde la navegación (P1) 🎯 MVP

### Pruebas

- [ ] T002 [P] [US1] `src/test/java/.../web/ConfiguracionContractTest.java`: la pantalla responde a cualquier cuenta activa; contiene los seis enlaces comunes; **la jefa ve `/usuarios` y un abogado no**; un abogado que pide `/usuarios` directamente sigue recibiendo rechazo del servidor
- [ ] T003 [P] [US1] `src/test/java/.../integration/ConfiguracionQueryBudgetIT.java`: `GET /configuracion` cuesta **las mismas consultas que una pantalla sin datos**, y ninguna propia. Medido con `ContadorDeConsultas`

### Implementación

- [ ] T004 [US1] `src/main/java/.../config/SettingsController.java` con `GET /configuracion`. Exige sesión y **no consulta nada**: pasa al modelo solo el título y la marca de sección
- [ ] T005 [US1] `src/main/resources/templates/settings/index.html`: un bloque por destino con su enlace y la línea que dice para qué sirve (RF-006). El bloque de cuentas va envuelto en un `<div th:if="${usuarioActual?.esJefa()}">` — la condición **en un elemento propio**, no junto a un `th:replace`, que tiene precedencia 100 contra 300 y se lo llevaría por delante
- [ ] T006 [US1] Añadir el enlace **Configuración** a `src/main/resources/templates/fragments/navegacion.html`, con su `aria-current`

### Retirar lo que se queda sin objeto

- [ ] T007 [US1] En `src/test/java/.../acceptance/RutasSegunInsumoTest.java`, sustituir `sinInvadirRutasReservadas` —cuyo bucle quedaría sobre una lista vacía y pasaría sin afirmar nada— por `configuracionEnlazaRutasQueExisten`: cada destino que aparece en `settings/index.html` **existe como ruta servida**. El mecanismo que impedía invadir rutas reservadas pasa a impedir enlaces rotos, que es el riesgo nuevo
- [ ] T008 [US1] Comprobar que `FIJADAS_POR_EL_INSUMO` sigue con `hasSize(9)`: `/configuracion` ya estaba en esa lista y no cambia

## Fase 3: Acabado

- [ ] T009 [P] Añadir `/configuracion` a `src/test/java/.../acceptance/AccessibilityAcceptanceTest.java`: los enlaces se alcanzan con el teclado y la página tiene encabezados que un lector pueda recorrer
- [ ] T010 [P] `src/test/java/.../acceptance/RecorridoConfiguracionTest.java`: con navegador, la jefa llega desde el panel a `/tipos-de-pendiente` **sin escribir ninguna URL** y añade un tipo; un abogado no ve el enlace a cuentas
- [ ] T011 Ejecutar `./mvnw verify` completo y comprobar que las 611 pruebas anteriores siguen en verde
- [ ] T012 Anotar en [DESPLIEGUE.md](../../DESPLIEGUE.md) que esta feature **no lleva migración**: se despliega sin tocar la base

## Dependencias

T001 → T002–T008 (T004 antes que T005 y T007) → T009–T012.

T002 y T003 son archivos distintos; T009 y T010 también.

## Estrategia de entrega

Una sola historia. Media pantalla de enlaces no es un incremento entregable, así que no se parte.
