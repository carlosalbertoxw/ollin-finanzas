// Genera los dos archivos de version del sitio a partir de version.properties.
//
// El numero de version vive en un solo lugar del repositorio. De ahi salen el
// APK, el tag de release y esto, asi que la pagina no puede quedar anunciando
// una version que ya no es la que se publica.
//
//   public/version.json  ->  lo que consulta la app una vez al dia
//   src/version.js       ->  lo que la pagina pinta, ya horneado en el build
//
// Los dos estan en .gitignore: son derivados, no fuentes.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = dirname(fileURLToPath(import.meta.url))
const raiz = resolve(aqui, '../..')

const REPOSITORIO = 'carlosalbertoxw/ollin-finanzas'
const SITIO = 'https://carlosalbertoxw.com/ollin-finanzas/'

function leeVersion() {
  const texto = readFileSync(resolve(raiz, 'version.properties'), 'utf8')
  const campo = (clave) => {
    const linea = texto.split('\n').find((l) => l.trim().startsWith(`${clave}=`))
    const valor = linea && Number.parseInt(linea.split('=')[1].trim(), 10)
    if (!Number.isInteger(valor)) {
      throw new Error(`version.properties no trae ${clave}, o no es un numero.`)
    }
    return valor
  }

  const mayor = campo('versionMayor')
  const menor = campo('versionMenor')
  const parche = campo('versionParche')

  return {
    nombre: `${mayor}.${menor}.${parche}`,
    // La misma cuenta que hace app/build.gradle.kts. Si una de las dos cambia,
    // la app compararia contra un numero que no significa lo mismo.
    codigo: mayor * 10000 + menor * 100 + parche
  }
}

const { nombre, codigo } = leeVersion()
const tag = `v${nombre}`
const apk = `https://github.com/${REPOSITORIO}/releases/download/${tag}/ollin-finanzas-${nombre}.apk`

// Lo que lee la app. `url` apunta al sitio y no al APK a proposito: la app solo
// abre enlaces de su propio sitio, y aqui es donde estan las instrucciones de
// instalacion que alguien va a necesitar antes de bajar nada.
const paraLaApp = {
  versionCode: codigo,
  versionName: nombre,
  url: SITIO,
  apk,
  fecha: new Date().toISOString().slice(0, 10)
}

// Lo que pinta la pagina. Horneado en el build en vez de pedido por fetch: el
// sitio se reconstruye en cada release, asi que ya sale con el dato puesto y
// se ve bien aunque el navegador no ejecute nada.
const paraLaPagina = `// Generado por scripts/genera-version.mjs. No lo edites a mano.
export const version = ${JSON.stringify({ nombre, codigo, tag, apk, repositorio: REPOSITORIO, fecha: paraLaApp.fecha }, null, 2)}
`

mkdirSync(resolve(aqui, '../public'), { recursive: true })
mkdirSync(resolve(aqui, '../src'), { recursive: true })
writeFileSync(resolve(aqui, '../public/version.json'), JSON.stringify(paraLaApp, null, 2) + '\n')
writeFileSync(resolve(aqui, '../src/version.js'), paraLaPagina)

console.log(`version ${nombre} (${codigo}) -> public/version.json, src/version.js`)
