#!/usr/bin/env bash
# Despliegue en un solo paso: migra y luego pide a Render que redespliegue.
#
# Por que no lo hace Flyway al arrancar: la aplicacion corre con una credencial
# que NO puede alterar el esquema, y en eso se sostiene que nadie pueda borrar el
# historial. Si migrara al arrancar, ese usuario necesitaria permisos de esquema y
# la garantia desapareceria.
#
# Este script conserva la separacion y quita el trabajo manual: la credencial de
# migracion vive en tu equipo, se usa un momento, y el servicio nunca la ve.
#
# Uso:
#   cp .env.despliegue.ejemplo .env.despliegue   (una sola vez, y rellenarlo)
#   ./desplegar.sh

set -euo pipefail
cd "$(dirname "$0")"

CONFIG=".env.despliegue"

if [ ! -f "$CONFIG" ]; then
  cat <<'AYUDA'
No encuentro .env.despliegue.

Cree el archivo a partir del ejemplo y rellene sus valores:

  cp .env.despliegue.ejemplo .env.despliegue

Ese archivo esta en .gitignore y no se sube nunca.
AYUDA
  exit 1
fi

# shellcheck source=/dev/null
set -a; . "./$CONFIG"; set +a

for v in SJ_HOST SJ_REF SJ_MIGRATION_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "Falta $v en $CONFIG"; exit 1
  fi
done

URL="jdbc:postgresql://${SJ_HOST}:5432/postgres?sslmode=require&currentSchema=sistema_juridico"
USUARIO="sistema_juridico_migrator.${SJ_REF}"

echo "==> 1/3  Empaquetando"
./mvnw -q -B -DskipTests package

echo "==> 2/3  Aplicando migraciones con la credencial de migracion"
java -jar target/sistema-juridico.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="$URL" \
  --spring.datasource.username="$USUARIO" \
  --spring.datasource.password="$SJ_MIGRATION_PASSWORD" \
  --spring.flyway.url="$URL" \
  --spring.flyway.user="$USUARIO" \
  --spring.flyway.password="$SJ_MIGRATION_PASSWORD" \
  --app.public-base-url=http://localhost \
  --app.rate-limit-key=solo-para-migrar \
  --app.command=migrate

echo "==> 3/3  Redesplegando en Render"
if [ -n "${RENDER_DEPLOY_HOOK:-}" ]; then
  curl -fsS -X POST "$RENDER_DEPLOY_HOOK" > /dev/null
  echo "    Despliegue solicitado. Siga el progreso en el panel de Render."
else
  cat <<'AYUDA'
    RENDER_DEPLOY_HOOK no esta configurado, asi que no se pidio el redespliegue.

    Para automatizarlo: en Render, Settings, Deploy Hook, copie la URL y anadala
    a .env.despliegue como RENDER_DEPLOY_HOOK.

    Mientras tanto, pulse «Manual Deploy» en el panel.
AYUDA
fi

echo
echo "Listo. El esquema esta al dia y la aplicacion arranca con la credencial"
echo "de runtime, que sigue sin poder alterarlo."
