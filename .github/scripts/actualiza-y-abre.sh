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

# Saca a anotacion del run lo que el log diga de nuestro proceso.
#
# Un artefacto de 164 KB que hay que descargar con token no lo lee nadie, y sin
# leerlo no se distingue un fallo de la app de uno de la prueba --que ya costo
# dos ciclos--. Solo lineas de nuestro paquete o de AndroidRuntime: filtrar por
# "died" a secas trae las trazas del ActivityManager, que hablan de cualquiera.
anota() {
  local archivo="$1"
  local motivos
  motivos="$(
    grep -E "$PAQUETE|AndroidRuntime|FATAL EXCEPTION" "$archivo" 2>/dev/null       | head -15       || true
  )"
  [ -z "$motivos" ] && { echo "::error::El log no dice nada de $PAQUETE."; return 0; }
  echo "$motivos" | while IFS= read -r linea; do
    echo "::error::${linea:0:200}"
  done
}

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

# Concede el permiso de avisos sin pasar por el dialogo del sistema.
#
# La app lo pide nada mas abrir, y quien dibuja el dialogo es
# com.google.android.permissioncontroller, no nuestro paquete: `am force-stop`
# se lleva por delante a la app pero deja ese dialogo en pie encima de la tarea.
# En el arranque siguiente `am start -S` --que solo cierra el paquete destino--
# encuentra arriba una instancia ajena, entrega el intent sin levantar nada y
# contesta TotalTime 0. La prueba media entonces un proceso que nunca existio y
# lo apuntaba como una regresion al actualizar: eso fue el fallo del 7 de
# septiembre de 2026, con los dos APK llevando la misma app.
#
# Concederlo es ademas lo que hace la mayoria de la gente, asi que el escenario
# no se falsea. Va tras cada instalacion desde cero; al actualizar con `-r` el
# permiso se conserva, que es justo lo que le pasa a quien ya lo habia dado.
concede_avisos() {
  local salida
  if salida="$(adb shell pm grant "$PAQUETE" android.permission.POST_NOTIFICATIONS 2>&1)"; then
    if [ -n "$salida" ]; then echo "$salida" | sed 's/^/    /'; fi
  else
    echo "    (no se pudo conceder el permiso de avisos: $salida)"
  fi
  return 0
}

# Abrir no puede tumbar el script: si el arranque no ocurre, lo que importa es
# el diagnostico de despues, no el codigo de salida de quien lanzo el intent.
#
# `-S` fuerza a cerrar la app antes de arrancarla, y no es opcional aqui.
# Instalar encima deja la tarea viva en recientes aunque el proceso este muerto,
# y entonces `am start` a secas contesta "intent has been delivered to currently
# running top-most instance" sin levantar nada: la prueba media un proceso que
# nunca arranco y culpaba a la app. Ademas es lo que se quiere medir, un
# arranque en frio sobre los datos de la version anterior.
#
# `-W` espera a que termine de arrancar y dice como fue, en vez de dejarlo a
# una espera a ciegas.
abre() {
  echo "Abriendo $ACTIVIDAD"
  local salida
  salida="$(adb shell am start -S -W -n "$ACTIVIDAD" 2>&1 || true)"
  echo "$salida" | sed 's/^/    /'

  # `am start` contesta "Status: ok" aunque no haya levantado nada: si encuentra
  # algo arriba se limita a entregarle el intent, y lo dice en un aviso que
  # hasta ahora se imprimia y se tiraba. Sin leerlo, la prueba no distingue "no
  # arranco porque nadie lo intento" de "no arranco porque la app se cerro", y
  # ahi esta toda la diferencia entre un fallo de esta prueba y una regresion
  # que debe bloquear una publicacion.
  if echo "$salida" | grep -q "Activity not started"; then
    echo "::error::El intent no arranco nada: se entrego a lo que ya estaba encima."
    echo "::error::Falla esta prueba, no la app. Mira que quedo arriba de la tarea."
    vuelca "sin-arranque"
    exit 1
  fi

  # No es motivo para fallar --puede ser un dialogo legitimo dibujado encima--
  # pero deja dicho a quien lea el log que lo que se mide despues quiza no sea
  # nuestra activity.
  local encima
  encima="$(echo "$salida" | sed -n 's/^ *Activity: *//p' | sed 's/[[:space:]]*$//' | head -1)"
  case "$encima" in
    "" | "$PAQUETE"/*) ;;
    *) echo "::warning::Arriba quedo $encima, no $ACTIVIDAD." ;;
  esac

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
concede_avisos
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
  anota "$RUNNER_TEMP/logcat-nueva.txt"

  # El experimento de control, y la razon de ser de este bloque: sin el, una
  # compilacion que no arranca en ningun sitio se lee igual que una que solo
  # muere al actualizar, y son problemas distintos --uno es de la version, el
  # otro de lo que dejo escrito la anterior--. Aqui se borra todo y se instala
  # de cero: si tampoco asi arranca, la actualizacion no tiene nada que ver.
  echo "--- Control: la misma version, sin datos de la anterior"
  adb uninstall "$PAQUETE" 2>&1 | sed 's/^/    /' || true
  adb install "$NUEVA" 2>&1 | sed 's/^/    /'
  concede_avisos
  adb logcat -c || true
  abre
  limpio="$(pid)"
  vuelca "limpia"

  if [ -z "$limpio" ]; then
    echo "::error::Tampoco arranca en una instalacion limpia: no es un problema de actualizar."
    anota "$RUNNER_TEMP/logcat-limpia.txt"
  else
    echo "::error::En limpio si arranca (pid $limpio). El problema esta en los datos que dejo la version anterior."
  fi

  exit 1
fi

echo "Abre y se mantiene (pid $vivo)."
