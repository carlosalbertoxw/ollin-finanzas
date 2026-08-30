#!/usr/bin/env bash
#
# Saca de CHANGELOG.md la seccion de una version, para que el lanzamiento de
# GitHub diga lo que tu escribiste y no un volcado de commits.
#
#   .github/scripts/notas-de-version.sh 1.1.0
#
# Falla si la seccion no existe o esta vacia. Es a proposito: el flujo de
# release lo llama antes de compilar, asi que una version sin notas se detiene
# ahi y no despues de firmar el APK.

set -euo pipefail

version="${1:-}"
archivo="${2:-CHANGELOG.md}"

if [ -z "$version" ]; then
  echo "uso: $(basename "$0") <version> [changelog]" >&2
  exit 2
fi

if [ ! -f "$archivo" ]; then
  echo "No encuentro $archivo." >&2
  exit 2
fi

# Comparacion literal por prefijo en vez de expresion regular: los puntos de
# "1.1.0" son comodines en regex y "1.1.0" acabaria empatando con "1a1b0".
notas="$(
  awk -v v="$version" '
    BEGIN { objetivo = "## [" v "]" }
    substr($0, 1, length(objetivo)) == objetivo { dentro = 1; next }
    dentro && substr($0, 1, 4) == "## [" { exit }
    # La ultima seccion no termina con otro encabezado, sino con el bloque de
    # enlaces del pie: sin esta linea, se colarian en sus notas.
    dentro && $0 ~ /^\[[^]]+\]:[ \t]/ { exit }
    dentro { print }
  ' "$archivo"
)"

# Quita los renglones en blanco de arriba y de abajo, que sobran al pegarlo.
notas="$(printf '%s\n' "$notas" | sed -e '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')"

if [ -z "$notas" ]; then
  echo "::error::$archivo no tiene una seccion '## [$version]' con contenido." \
       "Agregala antes de publicar: es lo que va a leer quien descargue esta version." >&2
  exit 1
fi

printf '%s\n' "$notas"
