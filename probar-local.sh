#!/usr/bin/env bash
# Levanta el sistema completo en este equipo para recorrerlo a mano.
#
# Por que en local y no contra el despliegue: la auditoria es inmutable por
# diseno (la aplicacion tiene INSERT y SELECT sobre audit_event, nunca DELETE).
# Cada pendiente de prueba que se registre en produccion deja rastro permanente
# que el area vera. Aqui los datos se tiran con `docker compose down -v`.
#
# Uso:
#   ./probar-local.sh            levantar, migrar, crear la jefatura y arrancar
#   ./probar-local.sh --sembrar  cargar los catalogos, una vez activada la cuenta
#   docker compose down -v       borrar los datos de prueba al terminar
#
# La carga de catalogos va aparte porque necesita una jefatura ACTIVA a quien
# atribuir cada valor, y el bootstrap deja la cuenta pendiente de activacion:
# hasta que alguien canjea el codigo, no hay autor al que imputar el cambio.

set -euo pipefail
cd "$(dirname "$0")"

PERFIL="--spring.profiles.active=local"
JAR="target/sistema-juridico.jar"

empaquetar_si_hace_falta() {
  if [ ! -f "$JAR" ] || [ -n "$(find src/main -newer "$JAR" -print -quit 2>/dev/null)" ]; then
    echo "==> Empaquetando"
    ./mvnw -q -B -DskipTests package
  fi
}

sembrar() {
  local salida
  salida=$(java -jar "$JAR" $PERFIL --app.command=seed-catalogs 2>/dev/null)
  # Se retiran las lineas de log de Spring para que el informe se lea.
  echo "$salida" | grep -vE "^[0-9]{4}-[0-9]{2}-[0-9]{2}T"
  if echo "$salida" | grep -q "No hay ninguna cuenta de jefatura activa"; then
    cat <<'AYUDA'

    La cuenta existe pero sigue pendiente de activacion. Entre en
    http://localhost:8090 con el correo y el codigo, cambie la contrasena, y
    vuelva a ejecutar:

        ./probar-local.sh --sembrar

AYUDA
    return 1
  fi
}

if [ "${1:-}" = "--sembrar" ]; then
  empaquetar_si_hace_falta
  sembrar
  exit $?
fi

echo "==> 1/4  Base de datos local (puerto 5455)"
docker compose up -d
printf "    Esperando a que acepte conexiones"
for _ in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U sistema_juridico_owner \
       -d sistema_juridico >/dev/null 2>&1; then
    echo " lista."; break
  fi
  printf "."; sleep 1
done

echo "==> 2/4  Empaquetando"
./mvnw -q -B -DskipTests package

echo "==> 3/4  Migrando el esquema"
java -jar "$JAR" $PERFIL --app.command=migrate

echo "==> 4/4  Cuenta de jefatura"
# Datos sinteticos a proposito: en el equipo de desarrollo no entran correos ni
# nombres reales del area.
java -jar "$JAR" $PERFIL --app.command=bootstrap \
  --app.bootstrap.email=jefatura@ejemplo.local \
  --app.bootstrap.name="Jefatura de prueba"

echo
echo "    Arrancando en http://localhost:8090"
echo
echo "    1. Entre con jefatura@ejemplo.local y el codigo de arriba."
echo "    2. En otra terminal:  ./probar-local.sh --sembrar"
echo "    3. Ya puede recorrer specs/003-control-pendientes/quickstart.md"
echo
echo "    Ctrl+C para parar. Los datos siguen ahi hasta «docker compose down -v»."
echo
java -jar "$JAR" $PERFIL
