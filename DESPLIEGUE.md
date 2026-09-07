# Despliegue en Render y Supabase

Pasos en orden. Los tres primeros se hacen una sola vez.

## 1. Supabase: crear la base y los roles

Cree el proyecto **en la misma región que elegirá en Render**. Cada milisegundo
de latencia entre ambos se paga en cada consulta de cada pantalla.

En el editor SQL, ejecute [`docs/supabase-roles.sql`](docs/supabase-roles.sql)
**cambiando antes las dos contraseñas** por unas generadas al azar.

Guarde ambas en el gestor de contraseñas:

| Rol | Para qué | ¿Va en Render? |
| --- | --- | --- |
| `sistema_juridico_migrator` | Aplicar migraciones | **No.** Se usa a mano |
| `sistema_juridico_app` | Ejecutar la aplicación | Sí |

Las tablas van a un esquema propio, `sistema_juridico`, **no a `public`**: la API
REST automática de Supabase expone `public` a quien tenga la clave anónima, que
es pública por diseño. Con las tablas ahí, los expedientes serían legibles desde
fuera sin pasar por la aplicación.

## 2. Aplicar las migraciones

Desde su equipo, con la credencial de **migración**:

```sh
export DB_MIGRATION_URL='jdbc:postgresql://HOST:5432/postgres?sslmode=require&currentSchema=sistema_juridico'
export DB_MIGRATION_USERNAME='sistema_juridico_migrator'
export DB_MIGRATION_PASSWORD='...'

./mvnw -DskipTests package
java -jar target/sistema-juridico.jar --spring.profiles.active=prod \
  --spring.datasource.url="$DB_MIGRATION_URL" \
  --spring.datasource.username="$DB_MIGRATION_USERNAME" \
  --spring.datasource.password="$DB_MIGRATION_PASSWORD" \
  --spring.flyway.url="$DB_MIGRATION_URL" \
  --spring.flyway.user="$DB_MIGRATION_USERNAME" \
  --spring.flyway.password="$DB_MIGRATION_PASSWORD" \
  --app.command=migrate
```

Debe informar de **7 migraciones aplicadas**. Es un paso explícito y no ocurre al
arrancar: así la credencial con la que corre el sistema nunca puede tocar el
esquema.

## 3. Render: crear el servicio

**New → Web Service**, apuntando al repositorio. Detecta el `Dockerfile` solo.

Variables de entorno:

| Variable | Valor |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST:5432/postgres?sslmode=require&currentSchema=sistema_juridico` |
| `SPRING_DATASOURCE_USERNAME` | `sistema_juridico_app` |
| `SPRING_DATASOURCE_PASSWORD` | La del rol de aplicación |
| `APP_PUBLIC_BASE_URL` | La URL que le asigne Render |
| `APP_RATE_LIMIT_KEY` | Que la genere Render |

**No ponga aquí la credencial de migración.** Si la aplicación pudiera migrar,
también podría alterar el esquema y borrar el historial.

## 4. Crear la primera cuenta

Una sola vez, desde la consola de Render (**Shell**) o desde su equipo apuntando
a Supabase:

```sh
java -jar app.jar --spring.profiles.active=prod --app.command=bootstrap \
  --app.bootstrap.email=jefa@correo.real --app.bootstrap.name="Nombre de la jefa"
```

**Anote el código que imprime.** No se vuelve a mostrar. La jefa lo canjea en
`/access/redeem` y elige su propia contraseña; nadie más llega a conocerla.

Si se pierde antes de usarlo, la jefa no puede entrar todavía: bórrela y repita
el bootstrap. Después del primer canje, los códigos se regeneran desde `/users`.

## 5. Cargar los catálogos

Con la sesión de la jefa:

1. `/procedural-statuses` — Pendiente de actuación, En trámite, Concluido, Archivado
2. `/non-working-days` — los feriados del año, **y confirmar la cobertura**

Sin el paso 2, los plazos dirán «Cálculo no disponible» en vez de contar días.
Es deliberado: un número calculado sobre un calendario que nadie revisó parece
fiable y no lo es.

## Comprobaciones tras desplegar

```sh
curl -s -o /dev/null -w '%{http_code}\n' https://SU-URL/login          # 200
curl -s -o /dev/null -w '%{http_code}\n' https://SU-URL/judicial-cases # 302 a /login
```

Y desde el editor SQL de Supabase, que la aplicación no pueda tocar la evidencia:

```sql
SET ROLE sistema_juridico_app;
DELETE FROM sistema_juridico.audit_event;   -- debe fallar: permission denied
RESET ROLE;
```

Si ese `DELETE` funciona, **algo está mal configurado**: pare y revise los roles
antes de meter datos reales.

## Sobre el plan gratuito de Render

El servicio se duerme tras un rato sin uso y la primera petición tarda unos
30 segundos en despertar. Para probar sirve; para el uso diario del área,
conviene el plan de pago.
