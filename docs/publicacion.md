# Publicación

Ollin Finanzas se distribuye **fuera de Google Play**: un APK firmado colgado de un lanzamiento de GitHub, y un sitio que lo enseña y explica cómo instalarlo. Todo lo automatiza GitHub Actions; lo único manual es subir la versión y poner el tag.

```
version.properties  ──►  APK (versionName / versionCode)
        │                 │
        │                 └──►  Release de GitHub  ──►  workflow Pages
        └──►  web/public/version.json  ──►  la app pregunta una vez al día
```

## El número de versión

Vive en [`version.properties`](../version.properties), en la raíz, y de ahí salen los tres lugares que tienen que decir lo mismo: el APK, el sitio y el `version.json` que consulta la app.

```properties
versionMayor=1
versionMenor=0
versionParche=0
```

`versionCode = mayor × 10000 + menor × 100 + parche`, así que **menor y parche tienen que quedarse por debajo de 100** — el build falla si no. Android solo exige que el número suba en cada publicación; esta forma además deja leer la versión de un vistazo: `10203` es la 1.2.3.

Para consultarla sin abrir el archivo:

```bash
./gradlew -q :app:imprimeVersion
```

Qué mover:

| Cambio | Sube |
|---|---|
| Corrección, sin nada nuevo | parche |
| Función nueva compatible con lo que ya hay | menor |
| Cambio de esquema con migración, o algo que rompe la costumbre | mayor |

## Los tres flujos

| Flujo | Cuándo | Qué hace |
|---|---|---|
| [`ci.yml`](../.github/workflows/ci.yml) | push a `main`, cada PR | Pruebas unitarias, lint, APK de depuración y build del sitio |
| [`release.yml`](../.github/workflows/release.yml) | tag `vX.Y.Z` | Verifica el tag y las notas, prueba, firma, y publica el APK con su SHA-256 |
| [`pages.yml`](../.github/workflows/pages.yml) | `web/**`, `version.properties`, o al terminar un Release | Construye el sitio y lo publica en Pages |

Los tres usan **JDK 21**: Kotlin 2.1.20 no arranca con 25 ni 26. Ver [desarrollo](desarrollo.md#el-jdk-de-gradle).

`pages.yml` escucha el final de `release.yml` a propósito. El sitio sirve `version.json`, que es de donde la app saca si hay algo más nuevo: si no se reconstruyera, se publicaría una versión de la que nadie se enteraría.

## Preparar el repositorio (una sola vez)

### 1. Los secretos de firma

`Settings → Secrets and variables → Actions → New repository secret`:

| Secreto | Qué es |
|---|---|
| `KEYSTORE_BASE64` | El `.jks` completo en base64 |
| `KEYSTORE_PASSWORD` | La contraseña del almacén |
| `KEY_ALIAS` | El alias de la llave |
| `KEY_PASSWORD` | La contraseña de la llave |

El almacén se convierte a base64 en una línea:

```bash
base64 -w0 ollin-finanzas-release.jks > llave.txt
```

Pega el contenido de `llave.txt` en el secreto y **borra el archivo**. Cómo se crea el `.jks` está en [desarrollo](desarrollo.md#apk-de-producción).

> El `.jks` es irremplazable. Android identifica al autor de la app por esa llave: un APK firmado con otra no se instala encima del anterior, obliga a desinstalar, y con ello borra la base de datos del usuario. Guarda una copia fuera de la máquina y fuera de GitHub.

Sin `KEYSTORE_BASE64` el flujo de release se detiene antes de compilar. Es a propósito: un APK sin firmar no se instala, y publicarlo sería peor que no publicar nada.

### 2. Pages

`Settings → Pages → Source: GitHub Actions`. No hay que crear ninguna rama `gh-pages`: el sitio se sube como artefacto desde el flujo.

El sitio queda en **https://carlosalbertoxw.com/ollin-finanzas/**. La cuenta tiene un dominio propio en su sitio de usuario, así que `carlosalbertoxw.github.io` responde con un 301 hacia él y las páginas de proyecto se sirven bajo el mismo dominio.

Esa dirección vive en tres lugares y los tres tienen que decir lo mismo:

| Dónde | Qué |
|---|---|
| [`web/vite.config.js`](../web/vite.config.js) | `base`, el path del que cuelgan los assets |
| [`web/scripts/genera-version.mjs`](../web/scripts/genera-version.mjs) | `SITIO`, lo que se escribe en `version.json` |
| [`Actualizaciones`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/actualizacion/Actualizaciones.kt) | `SITIO`, a dónde pregunta la app y qué enlaces acepta |

El de la app es el delicado: **queda horneado en cada APK que alguien instale**. Cambiar el dominio después deja a las versiones ya instaladas preguntando a una dirección que ya no contesta —no se rompen, simplemente dejan de enterarse de las actualizaciones—, así que si algún día se abandona `carlosalbertoxw.com` conviene dejar una redirección en pie.

## Publicar una versión

```bash
# 1. Sube el numero en version.properties.
# 2. En CHANGELOG.md, convierte "Sin publicar" en la seccion de la version
#    nueva con su fecha, y deja "Sin publicar" vacio arriba.
git add version.properties CHANGELOG.md && git commit -m "Sube a 1.1.0"

# 3. Etiqueta con la misma version, con v al frente.
git tag v1.1.0 && git push origin main v1.1.0
```

Y ya. El flujo compara el tag contra `version.properties`, busca las notas en el changelog, corre las pruebas, firma, publica el lanzamiento con el APK y su suma, y despierta al flujo de Pages, que reconstruye el sitio con la versión nueva.

Las dos verificaciones van **antes** de compilar, así que un descuido cuesta veinte segundos y no seis minutos más una firma gastada. Si el tag no coincide con la versión, o si falta la sección del changelog, el error lo dice con nombre y apellido. Para rehacer un tag:

```bash
git tag -d v1.1.0 && git push origin :refs/tags/v1.1.0
```

### Las notas salen del changelog

El cuerpo del lanzamiento no es un volcado de commits: es lo que tú escribiste en [`CHANGELOG.md`](../CHANGELOG.md). [`notas-de-version.sh`](../.github/scripts/notas-de-version.sh) saca la sección `## [X.Y.Z]` —parándose en el encabezado siguiente y en el bloque de enlaces del pie— y el flujo le agrega arriba el peso y el build, y abajo las instrucciones de instalación y el `sha256sum`.

Se puede probar antes de etiquetar, que es la gracia de tenerlo en un script y no enterrado en el YAML:

```bash
.github/scripts/notas-de-version.sh 1.1.0
```

## Cómo se entera la app

La app no se instala desde Play, así que nadie le avisa a nadie de una versión nueva: sin esto, quien tenga el APK se queda con él para siempre.

[`Actualizaciones`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/actualizacion/Actualizaciones.kt) pide `version.json` **una vez al día**, al arrancar, y compara el `versionCode` publicado con el instalado. Si hay novedad, *Acerca de* lo dice y ofrece el enlace al sitio.

```json
{
  "versionCode": 10100,
  "versionName": "1.1.0",
  "url": "https://carlosalbertoxw.com/ollin-finanzas/",
  "apk": "https://github.com/carlosalbertoxw/ollin-finanzas/releases/download/v1.1.0/ollin-finanzas-1.1.0.apk",
  "fecha": "2026-09-15"
}
```

Reglas que sostienen esa comprobación, todas probadas en [`ActualizacionesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ActualizacionesTest.kt):

- **Es la única salida a la red de toda la app.** Un `GET` a una dirección fija que no manda nada: ni identificador, ni datos del libro, ni siquiera qué versión traes. El `User-Agent` se sobrescribe con `OllinFinanzas` porque el de fábrica nombra el modelo del teléfono y su versión de Android.
- **Se puede apagar** desde *Acerca de*. Apagada, la app no toca internet en ningún momento.
- **La fecha del último intento solo se guarda cuando hubo respuesta.** Si se guardara siempre, un día sin red gastaría el turno y la app pasaría otras 24 horas sin volver a intentarlo.
- **El reloj movido hacia atrás no la congela.** Si solo se mirara «pasaron 24 horas», adelantar el reloj un año y devolverlo dejaría la comprobación dormida hasta alcanzar esa fecha otra vez.
- **Solo se abre lo que apunte al sitio del proyecto.** El archivo es nuestro, pero de ahí sale un enlace que alguien va a tocar; una `url` a cualquier otro sitio se descarta entera.
- **Nada de esto puede tumbar la app.** Un archivo a medias, un DNS caído o un JSON que no se entiende devuelven «no sé» y se reintenta mañana.

Buscar a mano desde *Acerca de* ignora el intervalo y el interruptor: tocar un botón y que no pase nada se lee como una app rota.

## El sitio

Vite sin framework, en [`web/`](../web). Cuatro archivos de fuente y ninguna dependencia en tiempo de ejecución.

```bash
npm --prefix web install
npm --prefix web run dev      # http://localhost:5173/ollin-finanzas/
npm --prefix web run build    # web/dist
```

`npm run build` genera antes dos archivos desde `version.properties`, ambos ignorados por git porque son derivados:

- `public/version.json` — lo que consulta la app.
- `src/version.js` — lo que pinta la página, horneado en el build. Se hornea en vez de pedirse con `fetch` porque el sitio se reconstruye en cada release: el dato ya es el bueno, no hay parpadeo y la página no depende de que la API de GitHub conteste.

La paleta es la misma de la app —obsidiana, jade, grana, cempasúchil— y el glifo es el mismo SVG. Quien llega a descargar debe reconocer la pantalla que va a abrir.
