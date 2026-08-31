#!/usr/bin/env bash
#
# Instala la version anterior, la usa, instala la nueva encima y comprueba que
# abre. Es la prueba que faltaba cuando la 1.0.1 se cerraba al arrancar en los
# telefonos que venian de la 1.0.0.
#
# Lo corre el emulador de .github/workflows/pruebas-instrumentadas.yml, con los
# dos APK ya compilados en $RUNNER_TEMP. Se puede correr en local contra un
# telefono o un emulador conectado:
#
#   RUNNER_TEMP=/tmp .github/scripts/actualiza-y-abre.sh
#
# Lo que se afirma es deliberadamente pobre: que el proceso siga vivo y que no
# haya una excepcion mortal. No mira la pantalla. Un fallo de arranque se
# manifiesta como el proceso que desaparece, y eso se ve sin depender de
# animaciones ni de esperar a que se dibuje nada.

set -euo pipefail

PAQUETE="com.carlosalbertoxw.ollin.finanzas.debug"
ANTERIOR="${RUNNER_TEMP:?falta RUNNER_TEMP}/anterior.apk"
NUEVA="$RUNNER_TEMP/nueva.apk"

# Cuanto se le da a la app para arrancar y escribir sus preferencias. Generoso
# a proposito: en un emulador frio el primer arranque es lento, y una espera
# corta convertiria la prueba en intermitente, que es peor que no tenerla.
ESPERA=25

for apk in "$ANTERIOR" "$NUEVA"; do
  [ -f "$apk" ] || { echo "::error::Falta $apk"; exit 1; }
done

abre() {
  adb shell monkey -p "$PAQUETE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep "$ESPERA"
}

# Devuelve el pid, o vacio si el proceso no esta.
pid() {
  adb shell pidof "$PAQUETE" 2>/dev/null | tr -d '\r'
}

echo "--- La version anterior"
adb install -r "$ANTERIOR"
abre

# Que la anterior haya llegado a escribir sus preferencias es lo que da sentido
# a todo esto: el fallo que motivo la prueba estaba en leer lo que ella dejo, no
# en instalar por instalar. La comprobacion de actualizaciones del primer
# arranque es la que escribe, y necesita red.
if [ -z "$(pid)" ]; then
  echo "::error::La version anterior no se mantuvo abierta. Algo mas esta roto."
  adb logcat -d -b crash > "$RUNNER_TEMP/logcat-anterior.txt" || true
  exit 1
fi

echo "Preferencias que dejo escritas:"
adb shell run-as "$PAQUETE" ls -1 files/datastore 2>/dev/null || \
  echo "  (no se pudieron listar; no es motivo para fallar)"

# Cerrarla a mano y no matar el emulador: se quiere el estado en disco, que es
# lo que la version nueva va a encontrarse.
adb shell am force-stop "$PAQUETE"

echo "--- La version nueva, encima"
# Sin desinstalar: -r conserva los datos, que es exactamente lo que hace quien
# instala el APK nuevo sobre el que ya tenia.
adb install -r "$NUEVA"

adb logcat -c
abre

vivo="$(pid)"
adb logcat -d > "$RUNNER_TEMP/logcat-nueva.txt" || true
choque="$(adb logcat -d -b crash | grep -F "$PAQUETE" || true)"

if [ -n "$choque" ]; then
  echo "::error::La version nueva se cerro al abrirse sobre la anterior."
  echo "$choque" | head -40
  exit 1
fi

if [ -z "$vivo" ]; then
  echo "::error::La version nueva no se mantuvo abierta sobre la anterior."
  tail -60 "$RUNNER_TEMP/logcat-nueva.txt" || true
  exit 1
fi

echo "Abre y se mantiene (pid $vivo)."
