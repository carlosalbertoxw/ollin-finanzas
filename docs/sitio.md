# El sitio

`https://carlosalbertoxw.github.io/ollin-finanzas/`

Una sola página, en [`web/`](../web/), construida con Vite y publicada en GitHub Pages desde este mismo repositorio. Hace dos cosas:

1. **Reparte el APK.** Ollin no está en ninguna tienda, así que este es el único sitio de descarga oficial. Lleva la versión, su tamaño y las instrucciones para instalar fuera de la tienda.
2. **Publica `version.json`**, que es lo que la app consulta una vez al día para saber si hay algo más nuevo. Ver [actualizaciones](actualizaciones.md).

La estructura, la paleta y el vocabulario de clases son los de [Ollin Finanzas](https://github.com/carlosalbertoxw/ollin-finanzas), que se publica igual y desde el mismo dominio. Son dos apps de la misma casa: quien llega a una y luego a la otra debe reconocer que salieron del mismo sitio.

**El historial de cambios no se pinta aquí.** Vive en el `CHANGELOG.md` y en las notas de cada release, que es donde GitHub ya lo enseña bien y con enlaces permanentes. Duplicarlo en la página obligaba a mantener un renderizador de Markdown propio para algo que nadie lee antes de descargar; la página enlaza a la de lanzamientos, que además lleva las sumas SHA-256.

## Por qué Vite y no un HTML suelto

Por el hash de los assets. La página se sirve desde un CDN que cachea con ganas; sin nombres versionados, cambiar el CSS deja a media gente viendo el anterior durante horas. Vite lo resuelve solo y de paso agrupa y minifica.

Lo que **no** hay es framework. Es una página de descarga: el DOM lo toca un archivo de un centenar de líneas ([`src/main.js`](../web/src/main.js)) y no hay estado que gestionar. Es el mismo criterio con el que la app escribe sus `.xlsx` a mano en vez de traer Apache POI.

## Estructura

```
web/
├── index.html                La página entera, con el texto ya escrito
├── src/
│   ├── main.js               Rellena los huecos por id con la versión horneada
│   ├── version.js            Generado. La versión, para la página
│   └── estilo.css            Paleta de la app, tema claro y oscuro
├── scripts/
│   └── genera-version.mjs    Escribe los dos archivos de versión
├── public/
│   ├── glifo.svg             El mismo glifo que el icono de la app
│   └── version.json          Generado. La versión, para la app
└── vite.config.js
```

### Los dos archivos de versión no se versionan

`public/version.json` y `src/version.js` los escribe [`genera-version.mjs`](../web/scripts/genera-version.mjs) en cada compilación, y están en el `.gitignore`. Tenerlos en git sería tener la versión en dos lugares y uno de ellos siempre atrasado, y ese desajuste es justo el que rompe el aviso de la app.

Son dos porque tienen dos lectores con necesidades distintas:

| Archivo | Quién lo lee | Cómo |
|---|---|---|
| `public/version.json` | La app, una vez al día | Petición HTTP. Sus nombres de campo son un contrato: se agregan, no se renombran |
| `src/version.js` | La página | Importado en el build, ya horneado |

La página **no** pide su propia versión por `fetch`. El sitio se reconstruye en cada release, así que el dato ya es el bueno cuando se sirve el HTML: pedirlo otra vez solo añadiría un parpadeo de «cargando» y un modo de fallo —red lenta, JSON caído— para algo que ya se sabía.

### De dónde salen los datos

| Dato | Origen |
|---|---|
| Versión, fecha y resumen | `CHANGELOG.md` |
| Dirección del APK, tamaño, SHA-256 | La release de GitHub, consultada con `gh` durante el despliegue |
| Dirección base del sitio | `actions/configure-pages`, que sabe dónde va a quedar publicado |

El flujo de despliegue **descarga el APK publicado** para medirlo y sacarle la huella, en vez de arrastrar esos datos desde la compilación. Es a propósito: la huella que se publica es la del archivo que la gente va a bajar, que es lo único que hace que comprobarla signifique algo.

Si todavía no hay ninguna release, el script no falla: se apoya solo en el `CHANGELOG` y el botón apunta a la dirección que *tendrá* la descarga. Así `npm run dev` produce un sitio coherente sin haber publicado nada.

## Cuándo se despliega

[`sitio.yml`](../.github/workflows/sitio.yml) corre en tres momentos:

- **Al publicar una versión.** El último job de [`publicacion.yml`](../.github/workflows/publicacion.yml) lo lanza con `gh workflow run sitio.yml --ref main`. Es el despliegue importante: reescribe el `version.json` que consultan las instalaciones que ya están por ahí.
- **Al empujar a `main`** algo bajo `web/` o el `CHANGELOG.md`.
- **A mano**, desde la pestaña Actions.

No recibe nada por parámetro: le pregunta a GitHub cuál es la última release. Por eso se puede relanzar en cualquier momento —tras corregir una errata, tras borrar una release equivocada— y siempre publica datos que corresponden con la realidad.

### Siempre desde `main`, nunca desde la etiqueta

El flujo de publicación lo **lanza** en vez de invocarlo como flujo reutilizable, y eso cambia el ref con el que corre. Hay dos razones y apuntan al mismo sitio.

La práctica: el entorno `github-pages` solo admite despliegues desde la rama por omisión. Invocado desde el flujo de publicación, correría en el ref de la etiqueta y GitHub lo rechaza con *«Tag v1.0.0 is not allowed to deploy to github-pages due to environment protection rules»*.

La de fondo: el contenido del sitio —el HTML, los estilos, los textos— debe salir de `main` y no del árbol al que apunte una etiqueta. Si saliera de la etiqueta, relanzar la publicación de una versión vieja republicaría el sitio de entonces y se llevaría por delante cualquier corrección posterior.

Los datos de la descarga no se pierden por lanzarlo desde `main`: no viajan por el ref, sino que salen de preguntarle a GitHub cuál es la última release.

Como contrapartida, el despliegue corre **aparte** y el flujo de publicación no lo espera: termina en verde en cuanto lo lanza. Si el sitio fallara, se ve en su propia ejecución.

## Trabajar en el sitio

```bash
cd web
npm install
npm run dev
```

Queda en `http://localhost:5173/ollin-finanzas/`. **Con la ruta**, no en la raíz: `base` lleva el nombre del repositorio porque Pages no sirve desde la raíz del dominio, y sin esa ruta la página carga sin estilos.

```bash
npm run build     # a web/dist
npm run preview   # sirve lo compilado
```

Para probarlo en la raíz (otro alojamiento, un dominio propio):

```bash
OLLIN_BASE=/ npm run build
```

## El resumen de la versión

`version.json` lleva un campo `notas` con una frase, que es lo que la app enseña en la tarjeta de *Acerca de*. Sale del `CHANGELOG`: el párrafo con el que abre la versión si lo tiene, y si no, su primer punto.

Se toma el primer punto y no la sección aplanada porque aplanarla pega el título del apartado a la primera frase —*«Arreglado El aviso no llegaba…»*— y encadena cambios sin relación en un párrafo ilegible. La tarjeta tiene sitio para dos o tres renglones; quien quiera el detalle tiene el enlace a la release.

## Privacidad del sitio

Sin analítica, sin cookies, sin rastreadores, sin fuentes remotas —la tipografía es la del sistema— y sin peticiones más allá de sus propios assets: la versión viene horneada en el build.

Lo sirve GitHub Pages, que como cualquier servidor ve la dirección IP de quien pide una página. Está dicho en la propia página, en la sección de privacidad: sería incoherente prometer que el libro no sale del teléfono y callar lo que sí se puede saber por visitar el sitio.
