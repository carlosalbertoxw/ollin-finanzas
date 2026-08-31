// Genera los dos archivos de version del sitio a partir de CHANGELOG.md.
//
// El numero de version vive en un solo lugar del repositorio. De ahi salen el
// APK, el tag de release, las notas y esto, asi que la pagina no puede quedar
// anunciando una version que ya no es la que se publica.
//
//   public/version.json  ->  lo que consulta la app una vez al dia
//   src/version.js       ->  lo que la pagina pinta, ya horneado en el build
//
// Los dos estan en .gitignore: son derivados, no fuentes.
//
// Los datos del artefacto —tamano, huella, fecha de publicacion— llegan por
// variables de entorno desde el flujo de despliegue, que le pregunta a GitHub
// por la ultima release. Sin ellas el script sigue funcionando con la version
// del CHANGELOG y la direccion que *tendra* la descarga: asi `npm run dev` en
// local produce un sitio coherente sin haber publicado nada.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = dirname(fileURLToPath(import.meta.url))
const raiz = resolve(aqui, '../..')

const REPOSITORIO = process.env.OLLIN_REPO ?? 'carlosalbertoxw/ollin-finanzas'

/**
 * Siempre https, aunque `configure-pages` diga otra cosa.
 *
 * Con un dominio propio y "Enforce HTTPS" todavia sin activar, GitHub reporta
 * la direccion como `http://`. Publicarla asi haria que la app rechazara el
 * campo `sitio` —solo acepta https, y con razon— y que la pagina ofreciera un
 * enlace en claro. El certificado ya existe en cuanto el dominio resuelve; lo
 * que falta es la casilla, no el cifrado.
 */
const SITIO = (process.env.OLLIN_SITIO ?? 'https://carlosalbertoxw.com/ollin-finanzas/')
  .replace(/^http:\/\//i, 'https://')

/**
 * La ultima version publicada y su entradilla, del CHANGELOG.
 *
 * Se ignora `## [Sin publicar]` a proposito: no casa con el patron de version,
 * y anunciar como disponible algo que nadie ha etiquetado mandaria a la gente a
 * una descarga que no existe.
 */
function leeVersion () {
  const texto = readFileSync(resolve(raiz, 'CHANGELOG.md'), 'utf8')
  const encabezado = /^##\s+\[(\d+)\.(\d+)\.(\d+)](?:\s*[-–]\s*(\S+))?\s*$/gm

  const marcas = [...texto.matchAll(encabezado)]
  if (marcas.length === 0) {
    throw new Error('CHANGELOG.md no tiene ningun encabezado `## [x.y.z]`.')
  }

  const primera = marcas[0]
  const [, mayor, menor, parche, fecha] = primera

  const hasta = marcas.length > 1 ? marcas[1].index : texto.length
  const cuerpo = texto.slice(primera.index + primera[0].length, hasta)

  return {
    nombre: `${mayor}.${menor}.${parche}`,
    // La misma cuenta que hace app/build.gradle.kts. Si una de las dos cambia,
    // la app compararia contra un numero que no significa lo mismo.
    codigo: Number(mayor) * 10000 + Number(menor) * 100 + Number(parche),
    fecha: fecha ?? null,
    resumen: entradilla(cuerpo)
  }
}

/**
 * La frase con la que abre la version, sin marcas de Markdown.
 *
 * La tarjeta de Acerca de tiene sitio para dos o tres renglones, no para un
 * changelog entero: quien quiera el detalle tiene el enlace a la release.
 */
function entradilla (markdown) {
  // Si la version abre con un parrafo antes del primer `###`, ese parrafo es el
  // resumen que alguien escribio a mano y siempre lee mejor que nada generado.
  const suyo = markdown.split(/^###\s+/m)[0].trim()

  // Si no lo hay, el primer punto de la lista. Aplanar la seccion entera pega
  // el titulo del apartado a la primera frase —"Arreglado El aviso no
  // llegaba..."— y encadena cambios sin relacion en un parrafo ilegible.
  const primerPunto = markdown
    .split('\n')
    .map((l) => l.trim())
    .find((l) => l.startsWith('- ') || l.startsWith('* '))

  const crudo = suyo || primerPunto || ''

  const plano = crudo
    .replace(/^[-*]\s+/gm, '')
    .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/[*`]/g, '')
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .join(' ')

  return plano.length > 280 ? `${plano.slice(0, 279).trimEnd()}…` : plano
}

const { nombre, codigo, fecha, resumen } = leeVersion()

const version = process.env.OLLIN_VERSION?.replace(/^v/, '') || nombre
const tag = `v${version}`
const apk = process.env.OLLIN_APK_URL ||
  `https://github.com/${REPOSITORIO}/releases/download/${tag}/ollin-finanzas-${version}.apk`

/**
 * El contrato con la app. Los nombres de estos campos los lee
 * `ComprobadorActualizaciones.lee()`, y cada APK ya instalado trae compilada su
 * manera de leerlos: cambiar uno rompe el aviso de todas las versiones que
 * estan por ahi, que por definicion no se pueden actualizar para arreglarlo.
 * Se agregan campos, no se renombran.
 */
const paraLaApp = {
  version,
  publicada: process.env.OLLIN_PUBLICADA || fecha || null,
  apk,
  sitio: SITIO,
  tamanoBytes: Number(process.env.OLLIN_APK_BYTES || 0) || null,
  sha256: process.env.OLLIN_APK_SHA256 || null,
  notas: resumen,
  release: `https://github.com/${REPOSITORIO}/releases/tag/${tag}`
}

/**
 * Lo que pinta la pagina. Horneado en el build en vez de pedido por fetch: el
 * sitio se reconstruye en cada release, asi que ya sale con el dato puesto y se
 * ve bien aunque el navegador tarde en ejecutar nada.
 */
const paraLaPagina = `// Generado por scripts/genera-version.mjs. No lo edites a mano.
export const version = ${JSON.stringify({
  nombre: version,
  codigo,
  tag,
  apk,
  repositorio: REPOSITORIO,
  fecha: paraLaApp.publicada,
  tamanoBytes: paraLaApp.tamanoBytes,
  sha256: paraLaApp.sha256
}, null, 2)}
`

mkdirSync(resolve(aqui, '../public'), { recursive: true })
mkdirSync(resolve(aqui, '../src'), { recursive: true })
writeFileSync(resolve(aqui, '../public/version.json'), `${JSON.stringify(paraLaApp, null, 2)}\n`)
writeFileSync(resolve(aqui, '../src/version.js'), paraLaPagina)

console.log(`version ${version} (${codigo}) -> public/version.json, src/version.js`)
