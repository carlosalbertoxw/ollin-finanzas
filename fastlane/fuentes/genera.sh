#!/usr/bin/env bash
# Regenera el icono y la imagen destacada de la ficha de Play a partir de
# icono.html y destacada.html. Las capturas no salen de aqui: se toman del
# telefono con adb (ver docs/publicacion.md).
#
# Requiere Chrome (o Chromium; CHROME=<ruta> si no esta en el lugar de siempre)
# y Python con Pillow. Chrome toma la captura; Pillow la deja en el formato que
# pide Play: el icono, PNG de 32 bits con alfa; la destacada, PNG sin alfa.
set -euo pipefail

aqui="$(cd "$(dirname "$0")" && pwd)"
destino="$aqui/../metadata/android/es-419/images"
mkdir -p "$destino"

if [[ -z "${CHROME:-}" ]]; then
  for c in \
    "/c/Program Files/Google/Chrome/Application/chrome.exe" \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    "$(command -v google-chrome || true)" \
    "$(command -v chromium || true)"; do
    if [[ -n "$c" && -x "$c" ]]; then CHROME="$c"; break; fi
  done
fi
[[ -n "${CHROME:-}" ]] || { echo "No encontre Chrome; pasalo en CHROME=<ruta>." >&2; exit 1; }

temporal="$(mktemp -d)"
trap 'rm -rf "$temporal"' EXIT

# En Windows, Chrome quiere rutas C:/... y no /c/...
ruta() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }

captura() { # captura <html> <ancho> <alto> <png>
  "$CHROME" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --default-background-color=00000000 --virtual-time-budget=5000 \
    --user-data-dir="$(ruta "$temporal/perfil")" \
    --window-size="$2,$3" --screenshot="$(ruta "$4")" "file:///$(ruta "$aqui/$1")" 2>/dev/null
}

captura icono.html 512 512 "$temporal/icono.png"
captura destacada.html 1024 500 "$temporal/destacada.png"

python - "$(ruta "$temporal")" "$(ruta "$destino")" <<'PY'
import sys
from PIL import Image

origen, destino = sys.argv[1], sys.argv[2]
icono = Image.open(f"{origen}/icono.png").convert("RGBA")
destacada = Image.open(f"{origen}/destacada.png").convert("RGB")
assert icono.size == (512, 512), icono.size
assert destacada.size == (1024, 500), destacada.size
icono.save(f"{destino}/icon.png", optimize=True)
destacada.save(f"{destino}/featureGraphic.png", optimize=True)
PY

echo "Listo: $destino/icon.png y featureGraphic.png"
