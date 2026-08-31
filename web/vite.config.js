import { defineConfig } from 'vite'

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
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
