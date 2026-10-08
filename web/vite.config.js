import { defineConfig } from 'vite'

/**
 * Lo unico que la pagina puede cargar: lo suyo.
 *
 * Es de donde la gente baja el APK, asi que un script inyectado en la cadena de
 * build --una dependencia comprometida, un CDN que nadie pidio-- podria cambiar
 * el enlace de descarga. Con esto el navegador se niega a ejecutar nada que no
 * venga del propio sitio.
 *
 * Va como `<meta>` porque GitHub Pages no deja poner cabeceras, y por eso falta
 * `frame-ancestors`, que en una meta no cuenta. Solo en el build: en `npm run
 * dev` Vite mete los estilos en linea y esta politica los bloquearia.
 */
const politica = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self'",
  "img-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'"
].join('; ')

const seguridad = {
  name: 'ollin-seguridad',
  apply: 'build',
  transformIndexHtml: () => [
    {
      tag: 'meta',
      attrs: { 'http-equiv': 'Content-Security-Policy', content: politica },
      injectTo: 'head-prepend'
    },
    // Quien llega a GitHub desde aqui no necesita saber de donde venia.
    {
      tag: 'meta',
      attrs: { name: 'referrer', content: 'no-referrer' },
      injectTo: 'head'
    }
  ]
}

/**
 * El sitio vive en https://carlosalbertoxw.com/ollin-finanzas/, que es un
 * subdirectorio: sin `base` los assets se pedirian a la raiz del dominio y la
 * pagina saldria sin estilos. (El github.io de la cuenta redirige a ese dominio
 * propio, y las paginas de proyecto se sirven bajo el mismo.)
 *
 * Se puede sobreescribir con OLLIN_BASE. El flujo de despliegue le pasa la ruta
 * que reporta `actions/configure-pages`, que es quien sabe de verdad donde va a
 * quedar publicado; en local sirve para probarlo en la raiz.
 */
export default defineConfig({
  base: process.env.OLLIN_BASE ?? '/ollin-finanzas/',
  plugins: [seguridad],
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
