#!/usr/bin/env bash
#
# Instala la version anterior, la usa, instala la nueva encima y comprueba que
# abre. Es la prueba que faltaba cuando la 1.0.1 se cerraba al arrancar en los
# telefonos que venian de la 1.0.0.
#
# Lo corre el emulador de .github/workflows/actualizacion.yml, con los dos APK
# ya compilados en $RUNNER_TEMP. Se puede correr en local contra un telefono o
# un emulador conectado:
#
#   RUNNER_TEMP=/tmp .github/scripts/actualiza-y-abre.sh
#
# Lo que se afirma es deliberadamente pobre: que el proceso siga vivo y que no
# haya una excepcion mortal. No mira la pantalla. Un fallo de arranque se
# manifiesta como el proceso que desaparece, y eso se ve sin depender de
# animaciones ni de esperar a que se dibuje nada.
#
# Todo paso dice lo que hace y todo fallo deja el logcat. La primera version de
# este script moria en silencio si `monkey` devolvia un codigo distinto de cero
# --sin mensaje, sin log, sin forma de saber si la culpa era de la app o del
# emulador--, y eso convierte una puerta que bloquea publicaciones en una
# moneda al aire.

set -euo pipefail

PAQUETE="com.carlosalbertoxw.ollin.finanzas.debug"
ACTIVIDAD="$PAQUETE/com.carlosalbertoxw.ollin.finanzas.MainActivity"
ANTERIOR="${RUNNER_TEMP:?falta RUNNER_TEMP}/anterior.apk"
NUEVA="$RUNNER_TEMP/nueva.apk"

# Cuanto se le da a la app para arrancar y escribir sus preferencias. Generoso
# a proposito: en un emulador frio el primer arranque es lento, y una espera
# corta convertiria la prueba en intermitente, que es peor que no tenerla.
ESPERA=25

# Cualquier fallo deja rastro, incluido el de una linea que no lo esperaba.
trap 'echo "::error::La prueba de actualizacion fallo en la linea $LINENO."; vuelca "fallo"' ERR

vuelca() {
  local etiqueta="$1"
  adb logcat -d > "$RUNNER_TEMP/logcat-$etiqueta.txt" 2>/dev/null || true
  echo "--- ultimas lineas del log ($etiqueta) ---"
  tail -40 "$RUNNER_TEMP/logcat-$etiqueta.txt" 2>/dev/null || echo "  (no se pudo leer el log)"
}

# El pid, o vacio si el proceso no esta. Nunca falla: la ausencia es un dato.
pid() {
  adb shell pidof "$PAQUETE" 2>/dev/null | tr -d '\r' || true
}

# Abrir no puede tumbar el script: si el arranque no ocurre, lo que importa es
# el diagnostico de despues, no el codigo de salida de quien lanzo el intent.
abre() {
  echo "Abriendo $ACTIVIDAD"
  adb shell am start -n "$ACTIVIDAD" 2>&1 | sed 's/^/    /' || true
  sleep "$ESPERA"
}

for apk in "$ANTERIOR" "$NUEVA"; do
  [ -f "$apk" ] || { echo "::error::Falta $apk"; exit 1; }
done

echo "--- Dispositivo"
adb devices -l
adb shell getprop ro.build.version.sdk | sed 's/^/  API /'

echo "--- La version anterior"
adb install -r "$ANTERIOR" 2>&1 | sed 's/^/    /'
abre

# Que la anterior haya llegado a escribir sus preferencias es lo que da sentido
# a todo esto: el fallo que motivo la prueba estaba en leer lo que ella dejo, no
# en instalar por instalar.
if [ -z "$(pid)" ]; then
  echo "::error::La version anterior no se mantuvo abierta. El problema no es la actualizacion."
  vuelca "anterior"
  exit 1
fi

echo "Preferencias que dejo escritas:"
adb shell run-as "$PAQUETE" ls -1 files/datastore 2>/dev/null | sed 's/^/    /' || \
  echo "    (no se pudieron listar; no es motivo para fallar)"

# Cerrarla a mano y no matar el emulador: se quiere el estado en disco, que es
# lo que la version nueva va a encontrarse.
adb shell am force-stop "$PAQUETE" || true

echo "--- La version nueva, encima"
# Sin desinstalar: -r conserva los datos, que es exactamente lo que hace quien
# instala el APK nuevo sobre el que ya tenia.
adb install -r "$NUEVA" 2>&1 | sed 's/^/    /'

adb logcat -c || true
abre

vivo="$(pid)"
vuelca "nueva"
choque="$(adb logcat -d -b crash 2>/dev/null | grep -F "$PAQUETE" || true)"

if [ -n "$choque" ]; then
  echo "::error::La version nueva se cerro al abrirse sobre la anterior."
  echo "$choque" | head -40
  exit 1
fi

if [ -z "$vivo" ]; then
  echo "::error::La version nueva no se mantuvo abierta sobre la anterior."
  exit 1
fi

echo "Abre y se mantiene (pid $vivo)."
