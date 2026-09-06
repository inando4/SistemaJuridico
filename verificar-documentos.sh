#!/usr/bin/env bash
# Comprueba que los datos repetidos entre documentos sigan coincidiendo.
#
# speckit-analyze cruza requisitos contra tareas, pero no mira los metadatos de
# cabecera: conteos, versiones, nombre de rama. Todo dato escrito en dos sitios
# acaba discrepando, y esto lo caza en dos segundos.
#
# Uso:  ./verificar-documentos.sh
# Sale con código 1 si algo no cuadra, para poder encadenarlo antes de un commit.

set -uo pipefail
cd "$(dirname "$0")"

FEATURE_DIR="$(ls -d specs/*/ 2>/dev/null | head -1)"
FEATURE_DIR="${FEATURE_DIR%/}"
CONST=".specify/memory/constitution.md"
FALLOS=0

ok()    { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
falla() { printf '  \033[31mFALLA\033[0m %s\n' "$1"; FALLOS=$((FALLOS+1)); }
info()  { printf '\n\033[1m%s\033[0m\n' "$1"; }

if [ -z "$FEATURE_DIR" ] || [ ! -d "$FEATURE_DIR" ]; then
  echo "No encuentro ninguna funcionalidad en specs/. Nada que comprobar."
  exit 0
fi

SPEC="$FEATURE_DIR/spec.md"
PLAN="$FEATURE_DIR/plan.md"
TASKS="$FEATURE_DIR/tasks.md"

echo "Funcionalidad: $FEATURE_DIR"

# ---------------------------------------------------------------- rama de git
info "Rama"
if RAMA=$(git branch --show-current 2>/dev/null) && [ -n "$RAMA" ]; then
  for f in "$SPEC" "$PLAN"; do
    [ -f "$f" ] || continue
    CITADA=$(grep -o '\*\*Rama Git actual\*\*: `[^`]*`' "$f" 2>/dev/null | head -1 | sed 's/.*`\(.*\)`/\1/')
    if [ -z "$CITADA" ]; then
      continue
    elif [ "$CITADA" = "$RAMA" ]; then
      ok "$(basename "$f") cita la rama correcta ($RAMA)"
    else
      falla "$(basename "$f") dice rama '$CITADA' pero estás en '$RAMA'"
    fi
  done
else
  ok "sin repositorio git; se omite"
fi

# --------------------------------------------------- versión de la constitución
info "Constitución"
if [ -f "$CONST" ]; then
  VER=$(grep -o '^\*\*Versión\*\*: [0-9]\+\.[0-9]\+\.[0-9]\+' "$CONST" | head -1 | awk '{print $2}')
  if [ -z "$VER" ]; then
    falla "no encuentro la línea de versión en $CONST"
  else
    ok "versión vigente: $VER"
    VIEJAS=$(grep -rhno 'constituci[oó]n [0-9]\+\.[0-9]\+\.[0-9]\+\|Constitución vigente: [0-9]\+\.[0-9]\+\.[0-9]\+' \
      "$FEATURE_DIR"/*.md "$FEATURE_DIR"/contracts/*.md "$FEATURE_DIR"/checklists/*.md 2>/dev/null \
      | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' | sort -u | grep -v "^${VER}$" || true)
    if [ -z "$VIEJAS" ]; then
      ok "todas las citas apuntan a $VER"
    else
      falla "hay citas a versiones antiguas: $(echo "$VIEJAS" | tr '\n' ' ')"
      grep -rn "$(echo "$VIEJAS" | head -1)" "$FEATURE_DIR"/*.md "$FEATURE_DIR"/contracts/*.md \
        "$FEATURE_DIR"/checklists/*.md 2>/dev/null | grep -v '\.bak' | sed 's/^/        /' | head -5
    fi
  fi
  PH=$(grep -c '\[[A-Z_]\{3,\}\]' "$CONST" || true)
  [ "$PH" -eq 0 ] && ok "sin marcadores sin rellenar" || falla "$PH marcador(es) [MAYUSCULAS] sin rellenar"
else
  falla "no existe $CONST"
fi

# ------------------------------------------------------------ conteo de requisitos
info "Requisitos"
if [ -f "$SPEC" ]; then
  FR=$(grep -c '^- \*\*FR-' "$SPEC" || true)
  SC=$(grep -c '^- \*\*SC-' "$SPEC" || true)
  ok "spec.md tiene $FR requisitos y $SC criterios de éxito"
  if [ -f "$PLAN" ]; then
    CITADO=$(grep -o '[0-9]\+ requisitos' "$PLAN" | head -1 | awk '{print $1}')
    if [ -z "$CITADO" ]; then
      ok "plan.md no repite el conteo (bien: lo que no se copia no se desincroniza)"
    elif [ "$CITADO" = "$FR" ]; then
      ok "plan.md cita $CITADO requisitos, coincide"
    else
      falla "plan.md dice '$CITADO requisitos' pero spec.md tiene $FR"
    fi
    CITADO_SC=$(grep -o '[0-9]\+ criterios de' "$PLAN" | head -1 | awk '{print $1}')
    if [ -n "$CITADO_SC" ] && [ "$CITADO_SC" != "$SC" ]; then
      falla "plan.md dice '$CITADO_SC criterios' pero spec.md tiene $SC"
    fi
  fi
else
  falla "no existe $SPEC"
fi

# ----------------------------------------------------------------- tareas
info "Tareas"
if [ -f "$TASKS" ]; then
  TOTAL=$(grep -c '^- \[[ x]\] T' "$TASKS" || true)
  ok "$TOTAL tareas"

  MALAS=$(grep '^- \[[ x]\]' "$TASKS" | grep -vc '^- \[[ x]\] T[0-9][0-9][0-9] ' || true)
  [ "$MALAS" -eq 0 ] && ok "todas con checkbox, ID de tres cifras y descripción" \
                     || falla "$MALAS tarea(s) mal formadas"

  DUP=$(grep -o '^- \[[ x]\] T[0-9]*' "$TASKS" | sort | uniq -d)
  [ -z "$DUP" ] && ok "sin IDs duplicados" \
                || falla "IDs duplicados: $(echo "$DUP" | grep -o 'T[0-9]*' | tr '\n' ' ')"

  ROTA=$(grep -o '^- \[[ x]\] T[0-9]*' "$TASKS" | grep -o '[0-9]*' \
         | awk 'NR!=$1+0 {print "posición "NR" es T"$1; exit}')
  [ -z "$ROTA" ] && ok "secuencia T001..T$(printf '%03d' "$TOTAL") sin saltos" \
                 || falla "secuencia rota: $ROTA"

  # referencias TXXX en prosa que apunten a una tarea inexistente
  HUERFANAS=""
  for ref in $(grep -o 'T[0-9][0-9][0-9]' "$TASKS" | sort -u); do
    grep -q "^- \[[ x]\] $ref " "$TASKS" || HUERFANAS="$HUERFANAS $ref"
  done
  [ -z "$HUERFANAS" ] && ok "las referencias cruzadas apuntan a tareas existentes" \
                      || falla "referencias a tareas inexistentes:$HUERFANAS"

  if [ -f "$PLAN" ]; then
    CITADO_T=$(grep -o '[0-9]\+ tareas' "$PLAN" | head -1 | awk '{print $1}')
    if [ -n "$CITADO_T" ] && [ "$CITADO_T" != "$TOTAL" ]; then
      falla "plan.md dice '$CITADO_T tareas' pero tasks.md tiene $TOTAL"
    fi
  fi
else
  ok "aún no hay tasks.md; se omite"
fi

# ----------------------------------------------------------------- resultado
echo
if [ "$FALLOS" -eq 0 ]; then
  printf '\033[32mTodo cuadra.\033[0m\n'
  exit 0
else
  printf '\033[31m%d comprobación(es) fallida(s).\033[0m\n' "$FALLOS"
  exit 1
fi
