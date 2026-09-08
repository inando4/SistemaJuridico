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

## Despliegues posteriores: un solo comando

Tras la configuración inicial, cada despliegue es:

```sh
./desplegar.sh
```

Empaqueta, aplica las migraciones con la credencial de migración y pide el
redespliegue a Render. La configuración va en `.env.despliegue`, que no se sube
al repositorio:

```sh
cp .env.despliegue.ejemplo .env.despliegue
```

Rellene el host, la referencia del proyecto y la contraseña del migrador. Si añade
además la URL del Deploy Hook de Render (Settings → Deploy Hook), el redespliegue
también es automático.

**Por qué no lo hace Flyway al arrancar.** La aplicación corre con una credencial
que no puede alterar el esquema, y sobre eso se sostiene que nadie pueda borrar el
historial. Si migrara al arrancar, ese usuario necesitaría permisos de esquema y la
garantía desaparecería. El script conserva la separación y quita el trabajo manual:
la credencial de migración vive en su equipo, se usa un momento, y el servicio de
Render nunca llega a verla.

## 2. Aplicar las migraciones (primera vez, paso a paso)

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
`/acceso/canjear` y elige su propia contraseña; nadie más llega a conocerla.

Si se pierde antes de usarlo, la jefa no puede entrar todavía: bórrela y repita
el bootstrap. Después del primer canje, los códigos se regeneran desde `/usuarios`.

## 5. Cargar los catálogos

Con la sesión de la jefa:

1. `/estados-procesales` — Pendiente de actuación, En trámite, Concluido, Archivado
2. `/dias-no-laborables` — los feriados del año, **y confirmar la cobertura**

Sin el paso 2, los plazos dirán «Cálculo no disponible» en vez de contar días.
Es deliberado: un número calculado sobre un calendario que nadie revisó parece
fiable y no lo es.

### Cargar los feriados de ley sin teclearlos

Los dieciséis feriados nacionales se pueden proponer con un comando, en vez de
escribirlos a mano cada año:

```sh
./cargar-feriados.sh
```

Sin más argumentos carga el año anterior, el actual y el siguiente. Para años
concretos: `./cargar-feriados.sh 2027 2028`.

**El 15 de agosto no entra salvo que se pida.** La Ley 24875 lo declara día
cívico no laborable en la provincia de Arequipa, que no es lo mismo que un
feriado nacional: que un plazo procesal se detenga ese día depende de cómo lo
traten el Poder Judicial y la propia entidad. Si la jefatura confirma que su
oficina lo observa, use `./cargar-feriados.sh --arequipa 2027` (sobre un año
todavía vacío) o póngalo a mano desde la pantalla.

**El comando no confirma la cobertura de ningún año, y no debe hacerlo.** Deja
las fechas propuestas; la jefa entra en `/dias-no-laborables`, las revisa y
confirma. Hasta entonces los plazos siguen diciendo «Cálculo no disponible».

**Solo carga años vacíos.** Si el año ya tiene algún día registrado, se deja
entero como está. Así, un día que la jefa retiró no vuelve a aparecer nunca. El
precio es que un año al que ya se añadió un puente a mano hay que completarlo
desde la pantalla.

**Faltan los puentes.** Los días no laborables que el Ejecutivo declara cada año
por decreto supremo no son de ley y no se pueden calcular. Hay que añadirlos a
mano desde la misma pantalla.

No forma parte de `./desplegar.sh`: es una carga anual, no un paso de despliegue.

## Comprobaciones tras desplegar

```sh
curl -s -o /dev/null -w '%{http_code}\n' https://SU-URL/login          # 200
curl -s -o /dev/null -w '%{http_code}\n' https://SU-URL/judiciales # 302 a /login
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
