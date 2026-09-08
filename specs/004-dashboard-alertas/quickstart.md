# Guía de validación — Funcionalidad 004

## Requisitos previos

- Docker en marcha, para la base local y para Testcontainers.
- El sistema levantado con `./probar-local.sh`, catálogos cargados y sesión iniciada.
- El calendario del año **cargado y confirmado** en `/dias-no-laborables`. Sin eso, dos
  tarjetas mostrarán el aviso en lugar de una cifra —que es correcto, pero no es lo que se
  quiere comprobar aquí.

## Suite automática

```bash
./mvnw verify
```

Cubre las cuentas, los niveles, el presupuesto de consultas, el rendimiento con 5.000
pendientes y el recorrido con navegador. Lo de abajo es lo que conviene mirar con los ojos.

## Recorrido manual

1. **Iniciar sesión**. Debe aterrizar en el dashboard, no en expedientes judiciales.
2. Registrar pendientes que cubran las seis situaciones: uno con plazo de ayer, uno con plazo
   hoy, uno programado para hoy, uno con plazo dentro de dos días hábiles, uno sin plazo
   recibido hace más de tres semanas, y uno ya cumplido este mes.
3. Abrir el dashboard: **cada tarjeta debe contar los suyos**.
4. **Pulsar cada tarjeta**: el listado al que lleva debe contener exactamente los pendientes
   que la tarjeta contaba. Es la comprobación que más fallos destapa.
5. Abrir `/alertas`: deben salir los cinco tipos, cada uno con su etiqueta, en el orden de los
   niveles.
6. **El pendiente vencido que además está programado para hoy debe salir una sola vez**, como
   vencido. Si sale dos veces, el `CASE` no está evaluando en orden.
7. Marcar uno como cumplido y volver: debe desaparecer de las alertas y de las tarjetas de
   urgencia, y sumar en «Cumplidos este mes».
8. Entrar con otra cuenta: **ninguna cifra debe incluir pendientes ajenos**.
9. Volver al dashboard desde tres pantallas distintas usando la navegación.

## Comprobaciones que no se ven en pantalla

**Consultar no escribe historial.** Abrir el dashboard y las alertas veinte veces no debe
añadir ninguna fila a `audit_event`.

**El presupuesto de consultas se cumple.** Con `ContadorDeConsultas`: el dashboard no puede
pasar de 4 consultas ni las alertas de 5, con 5.000 pendientes en la base. Seis tarjetas no
son seis consultas.

**Nada derivado se persiste.** Ninguna columna nueva en `pending_task`, y ninguna tabla de
alertas. Se comprueba sobre `information_schema`.

**El cruce de año se cuenta bien.** Con un pendiente recibido en diciembre y la fecha del
sistema en enero, la antigüedad debe dar un número, no el aviso de calendario sin cobertura.
Es el fallo que esta funcionalidad corrige y que solo se manifiesta en enero: la prueba usa
fechas absolutas a ambos lados del cambio de año, no relativas a «hoy».

**Sin calendario no se inventa.** Retirar la confirmación de cobertura del año: «Próximos
vencimientos» y «Sin plazo +15 días» deben avisar; «Vencidos», «Urgentes hoy», «Pendientes
activos» y «Cumplidos este mes» deben seguir dando su número, porque no dependen del
calendario.

**Sin JavaScript sigue siendo utilizable.** Cortando la descarga de HTMX, ambas pantallas
deben cargar y sus enlaces funcionar.

## Antes de desplegar

Esta funcionalidad **no lleva migración**: no hay nada que aplicar contra Supabase. Basta con
poner `main` al día y redesplegar con `./desplegar.sh`, que detectará que no hay migraciones
pendientes y seguirá con el resto de pasos.
