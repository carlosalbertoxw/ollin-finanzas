import './estilo.css'
import { version } from './version.js'

// La version viene horneada del build (scripts/genera-version.mjs), no de un
// fetch: el sitio se reconstruye en cada release, asi que el dato ya es el
// bueno y la pagina no parpadea con un "cargando" que nadie necesita.

const enlaceDeLanzamientos = `https://github.com/${version.repositorio}/releases`

const textos = {
  version: [`versión ${version.nombre}`, tamano(version.tamanoBytes)].filter(Boolean).join(' · '),
  'version-pie': `versión ${version.nombre}`,
  fecha: formateaFecha(version.fecha)
}

for (const [id, texto] of Object.entries(textos)) {
  const nodo = document.getElementById(id)
  if (nodo) nodo.textContent = texto
}

const enlaces = {
  descarga: version.apk,
  notas: `${enlaceDeLanzamientos}/tag/${version.tag}`,
  lanzamientos: enlaceDeLanzamientos,
  repo: `https://github.com/${version.repositorio}`
}

for (const [id, url] of Object.entries(enlaces)) {
  const nodo = document.getElementById(id)
  if (nodo) nodo.href = url
}

/** "30 de agosto de 2026", que es como lo lee una persona. */
function formateaFecha (iso) {
  if (!iso) return '—'
  // Con la hora pegada a mano para que no se lea como UTC y se corra un dia
  // hacia atras en cualquier huso al oeste de Greenwich.
  const fecha = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(fecha.getTime())) return iso
  return fecha.toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' })
}

/** Megabytes con un decimal. Nadie decide nada con los bytes exactos. */
function tamano (bytes) {
  if (!bytes) return null
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
