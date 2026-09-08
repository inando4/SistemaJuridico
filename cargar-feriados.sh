#!/usr/bin/env bash
# Carga los feriados de ley del Peru en la base de produccion.
#
# Aparte de ./desplegar.sh a proposito: es una tarea anual, no un paso del
# despliegue. Un despliegue que escribiera datos del calendario legal cada vez
# que se sube codigo seria un mal valor por omision.
#
# No confirma la cobertura de ningun ano. Deja las fechas propuestas; la jefa
# entra en /dias-no-laborables, las revisa y confirma. Hasta entonces los plazos
# siguen diciendo «Calculo no disponible», que es lo correcto.
#
# Uso:
#   ./cargar-feriados.sh                    # ano anterior, actual y siguiente
#   ./cargar-feriados.sh 2027 2028          # los anos que se indiquen
#   ./cargar-feriados.sh --arequipa 2027    # anadiendo el 15 de agosto
#
# El 15 de agosto (Ley 24875) es dia civico no laborable en la PROVINCIA DE
# AREQUIPA, no un feriado nacional. Solo se carga si se pide, porque que un
# plazo procesal se detenga ese dia depende de como lo traten el Poder Judicial
# y la propia entidad.

set -euo pipefail
cd "$(dirname "$0")"

CONFIG=".env.despliegue"

if [ ! -f "$CONFIG" ]; then
  echo "No encuentro $CONFIG. Vea DESPLIEGUE.md." >&2
  exit 1
fi

# shellcheck source=/dev/null
set -a; . "./$CONFIG"; set +a

for v in SJ_HOST SJ_REF SJ_MIGRATION_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "Falta $v en $CONFIG" >&2; exit 1
  fi
done

AREQUIPA="false"
ANOS=""
for arg in "$@"; do
  case "$arg" in
    --arequipa) AREQUIPA="true" ;;
    *[!0-9]*)   echo "Argumento no reconocido: $arg" >&2; exit 1 ;;
    *)          ANOS="${ANOS:+$ANOS,}$arg" ;;
  esac
done

URL="jdbc:postgresql://${SJ_HOST}:5432/postgres?sslmode=require&currentSchema=sistema_juridico"
USUARIO="sistema_juridico_migrator.${SJ_REF}"

if [ ! -f target/sistema-juridico.jar ]; then
  echo "==> Empaquetando"
  ./mvnw -q -B -DskipTests package
fi

echo "==> Cargando feriados${ANOS:+ de $ANOS}"
java -jar target/sistema-juridico.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="$URL" \
  --spring.datasource.username="$USUARIO" \
  --spring.datasource.password="$SJ_MIGRATION_PASSWORD" \
  --spring.flyway.enabled=false \
  --app.public-base-url=http://localhost \
  --app.rate-limit-key=solo-para-sembrar \
  --app.command=seed-holidays \
  --app.arequipa="$AREQUIPA" \
  ${ANOS:+--app.years="$ANOS"}
