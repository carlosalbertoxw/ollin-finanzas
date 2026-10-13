#!/usr/bin/env bash
#
# Comprueba que un version.json se lea en la app como se lee de verdad.
#
#   bash .github/scripts/comprueba-version-json.sh web/dist/version.json
#
# El version.json es un contrato con cada APK ya instalado, que trae compilada
# su manera de leerlo (ComprobadorActualizaciones.lee y DestinosPermitidos) y no
# se puede corregir desde fuera. Si algo no le gusta, la app descarta el archivo
# entero en silencio, y el aviso de version nueva —el unico canal de
# correcciones de una app que no esta en la tienda— se apaga en todas las
# instalaciones sin que nada falle. Por eso esto replica las reglas de la app,
# no unas parecidas:
#
#   - `version` es X.Y.Z (Version.de).
#   - `apk` es una descarga de las releases de este repositorio
#     (DestinosPermitidos.apk): empieza por DESCARGAS_OFICIALES, que ya fija
#     esquema, host y que no haya usuario ni puerto, y el resto de la ruta solo
#     trae letras, cifras y `. _ - /`, sin `..`. Eso deja fuera los `%2e`, las
#     barras invertidas, la consulta y el fragmento.
#
# Lo usan pruebas.yml, sobre el sitio de cada cambio, y sitio.yml, sobre el que
# se va a publicar con los datos de la release: es en ese donde un cambio de
# formato de GitHub apagaria el aviso.
#
# Si cambia DestinosPermitidos en la app, cambia tambien aqui.

set -euo pipefail

archivo="${1:?Uso: comprueba-version-json.sh <version.json>}"

DESCARGAS_OFICIALES="https://github.com/carlosalbertoxw/ollin-finanzas/releases/download/"

if ! jq -e --arg prefijo "$DESCARGAS_OFICIALES" '
  (.version | type == "string" and test("^[0-9]+\\.[0-9]+\\.[0-9]+$")) and
  (.apk | type == "string"
    and startswith($prefijo)
    and (ltrimstr($prefijo) | test("^[A-Za-z0-9._/-]*$"))
    and (contains("..") | not))
' "$archivo" > /dev/null; then
  echo "::error::$archivo no se leería en la app: version tiene que ser X.Y.Z y apk una descarga de $DESCARGAS_OFICIALES"
  echo "::error::La app descartaría el archivo entero y el aviso de versión nueva se apagaría en todas las instalaciones."
  cat "$archivo"
  exit 1
fi

echo "$archivo en regla:"
cat "$archivo"
