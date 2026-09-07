# Guía de validación — Funcionalidad 003

Presupone las 001 y 002 desplegadas: esta funcionalidad no trae acceso, calendario ni
auditoría propios.

## Requisitos previos

```sh
docker compose up -d
./mvnw -DskipTests package
java -jar target/sistema-juridico.jar --spring.profiles.active=local --app.command=migrate
```

Las V1 a V8 ya estarán y no se reejecutan.

## Suite automática

```sh
./mvnw verify
```

PostgreSQL 17 real por Testcontainers. **H2 está prohibido**: no reproduce restricciones ni
concurrencia, que es justo lo que estas pruebas comprueban.

## Recorrido manual

Con sesión iniciada y el calendario del año **cargado y confirmado**:

1. `/tipos-de-pendiente`, `/prioridades`, `/estados-de-pendiente` — cargar los valores del área.
2. `/pendientes/nuevo` — registrar indicando solo el título. Debe guardarse.
3. Registrar otro vinculado a un expediente judicial, y otro a uno administrativo. Ambos deben
   mostrar de qué expediente cuelgan.
4. Registrar uno **sin vínculo**, tipo «comprar tóner». Debe ser válido.
5. Marcar uno como cumplido: sale de la lista activa y aparece en `/cumplidos`.
6. **Revertirlo sin motivo**: debe rechazarse. Con motivo: vuelve a la lista activa, sale de
   cumplidos, y el historial muestra **las dos** entradas.
7. Declarar «no cumplido» uno programado para un viernes: debe pasar al lunes, o más allá si
   el lunes es feriado.
8. Reprogramar manualmente: el historial debe decir «del X al Y».
9. Registrar uno sin fecha límite con recepción de hace más de quince días hábiles: debe
   mostrar el aviso de pendiente sin plazo.
10. `/pendientes/hoy` — debe incluir los de hoy y los vencidos que sigan activos.

## Comprobaciones que no se ven en pantalla

**El vínculo es excluyente.** Intentar guardar uno con expediente judicial y administrativo a
la vez debe fallar, y la base debe impedirlo aunque se salte la aplicación.

**Sin calendario no se reprograma a ciegas.** Retirar la confirmación de cobertura del año y
declarar «no cumplido»: debe avisar y **no** cambiar la fecha.

**El historial sigue siendo inmutable** tras ampliar la restricción:

```sql
SET ROLE sistema_juridico_app;
DELETE FROM sistema_juridico.audit_event;
RESET ROLE;
```

Debe fallar con `permission denied`.

**Ningún valor derivado persistido.** Ninguna columna nueva debe llamarse `antiguedad`,
`dias_restantes`, `reprogramaciones` ni equivalente. Se comprueba sobre `information_schema`.

**Los cinco catálogos siguen siendo independientes** tras unificar su código: crear el mismo
nombre en varios no debe producir conflicto, y ninguno debe ver las filas de otro.

## Antes de desplegar

Aplicar la migración V9 contra Supabase con la credencial de **migración** y después
redesplegar, en ese orden. Con `./desplegar.sh` es un solo paso, pero `main` debe estar al día
antes: el Deploy Hook tira de ahí.
