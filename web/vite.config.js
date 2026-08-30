import { defineConfig } from 'vite'

/**
 * El sitio vive en https://carlosalbertoxw.com/ollin-finanzas/, que es un
 * subdirectorio: sin `base` los assets se pedirian a la raiz del dominio y la
 * pagina saldria sin estilos. (El github.io de la cuenta redirige a ese dominio
 * propio, y las paginas de proyecto se sirven bajo el mismo.)
 */
export default defineConfig({
  base: '/ollin-finanzas/',
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
