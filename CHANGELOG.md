# Historial de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado según [SemVer](https://semver.org/lang/es/).

**Este archivo manda.** No es un resumen escrito después: es de donde salen las tres cosas que definen una publicación.

1. [`app/build.gradle.kts`](app/build.gradle.kts) lee de aquí el `versionName` y deriva el `versionCode`. No hay ningún número de versión escrito a mano en el build.
2. El flujo de [publicación](.github/workflows/publicacion.yml) toma la sección de la versión etiquetada y la usa como cuerpo de la release de GitHub.
3. El [sitio](docs/sitio.md) publica el `version.json` que consulta la app para avisar de una actualización.

Por eso un tag `v1.2.0` sin su `## [1.2.0]` aquí arriba **falla antes de compilar nada**. Es a propósito: una versión sin notas es una versión que nadie sabe si le conviene instalar.

Lo que todavía no se publica se va acumulando bajo `## [Sin publicar]`; al etiquetar, esa sección se renombra con el número y la fecha.

Los enlaces van con dirección completa: el mismo texto se lee en GitHub, en el sitio y en el cuerpo de la release, y una ruta relativa solo funcionaría en uno de los tres.

## [Sin publicar]

## [1.1.0] - 2026-09-19

### Añadido

- **Periodicidad «Único» para lo que se paga una sola vez.** La colegiatura de este agosto, el depósito del departamento, la reparación ya cotizada: cosas que no se repiten y que antes había que dar de alta como un plan mensual de un solo pago para que no volvieran a aparecer. Se modela como lo que es —un plan de un pago— así que lo cierra el mismo contador que cierra un MSI en su última mensualidad. Descartarlo también lo cierra: no tiene un pago siguiente al que correrse, y sin esto se habría quedado pendiente para siempre en la misma fecha, repitiendo cada día el aviso de algo ya decidido. No entra en la carga fija mensual, porque no se siente todos los meses.

- **Tocar una cuenta en el tablero abre sus movimientos.** La pregunta que sigue a ver un saldo raro es siempre la misma —de dónde salió— y hasta ahora había que ir a Movimientos y armar el filtro a mano. Ahora el renglón de la cuenta lleva directo a la lista ya filtrada por ella.

- **Archivo de compromisos.** Lo que ya no pide nada —el pago único resuelto, el plan a plazos que llegó a su última mensualidad— sale de la lista de pendientes. El botón de la caja, en la barra superior y al lado del de agregar, **cambia la pantalla entre las dos listas**: o lo pendiente o lo cerrado, nunca revueltos, con el título de la barra diciendo en cuál estás. Está siempre, aunque no hayas archivado nada todavía, y con el archivo vacío la pantalla dice qué va a caer ahí. Lo archivado sigue siendo tuyo —se abre, se edita, se borra— y junto al nombre se lee cómo terminó, **Cumplido** o **Descartado**. Las dos cifras de arriba y el recordatorio diario ya solo miran lo pendiente, que es lo que siempre miraron; lo que cambia es que ahora la lista se lee igual que ellos.

- **Licencia MIT.** El código queda bajo [MIT](https://github.com/carlosalbertoxw/ollin-finanzas/blob/main/LICENSE): cualquiera puede usarlo, copiarlo, modificarlo y redistribuirlo, incluso con fines comerciales, mientras conserve el aviso de copyright.

### Cambiado

- **Archivo deja de ser pestaña y se muda a Ajustes.** Importar y exportar se hace de vez en cuando; las cuatro pestañas que quedan —Tablero, Movimientos, Presupuesto, Analítica— se miran a diario, y una barra reservada a lo cotidiano se lee de un vistazo. Entra por **Ajustes → Importar y exportar**, y el aviso semanal de respaldo —y el de versión nueva— la siguen abriendo de un toque desde la barra de notificaciones, que es cuando de verdad urge.

- **El filtro de cuenta de Movimientos es un desplegable.** Era un chip que ciclaba: para llegar a la octava cuenta había que tocarlo ocho veces, y pasarse obligaba a dar la vuelta entera hasta volver a empezar. Ahora se abre la lista completa y se elige de una, con **Todas las cuentas** arriba para quitar el filtro. El chip de traspasos se queda a su lado, igual que antes.

- **La prueba de actualización vuelve a bloquear la publicación.** Salió del flujo el 3 de septiembre porque bloqueando daba falsos negativos contra una versión que abre bien en un teléfono real; arreglados los tres —todos del andamiaje, ninguno de la app— pasó en verde contra la 1.0.3 y regresa a donde sirve de algo: si la versión nueva no abre encima de la anterior, no se firma nada ni se crea la release.

### Arreglado

- La prueba de actualización acusaba a la app de no abrirse cuando quien no arrancaba nada era el propio script. Desde Android 13 la versión anterior pide el permiso de notificaciones nada más abrirse, y ese diálogo lo dibuja el sistema: `am force-stop` se lleva la app pero deja el diálogo encima de la tarea, así que el `am start` siguiente entregaba el intent sin levantar ningún proceso. Ahora el permiso se concede antes de abrir, y la prueba distingue «no arrancó porque nadie lo intentó» de «no arrancó porque se cerró». Era el tercero de los tres falsos negativos que la sacaron del flujo de publicación.

- Las cuatro pruebas de `NavegacionTest` fallaban en un emulador de API 34 por la misma razón: el diálogo tapaba la actividad y Compose no encontraba ninguna jerarquía que mirar. Se concede el permiso antes de arrancarla, con el orden de las reglas declarado. En API 26 pasaban porque ahí ese permiso todavía no existe.

## [1.0.3] - 2026-08-31

### Añadido

- **Recordatorio de respaldo.** Un aviso cada siete días si no has exportado tu libro, y otro cuando aparece una versión nueva — que es justo cuando conviene tener una copia. Los dos abren la pantalla de Archivo. La semana se cuenta desde tu último respaldo, así que exportar calla el aviso; el de versión nueva se manda una sola vez por versión. Se apaga en `Ajustes → Respaldo`.

- Una prueba de actualización sobre emulador: instala la versión de la última etiqueta, la abre para que escriba sus preferencias, instala la nueva encima sin desinstalar y comprueba que sigue abriéndose. Es la que habría atrapado el fallo de la 1.0.1. Vive en `actualizacion.yml`. Por ahora informa sin bloquear: al ponerla a detener publicaciones dio tres falsos negativos seguidos contra una versión que abre bien en un teléfono real, y una puerta que detiene releases buenas se acaba ignorando. Vuelve a bloquear en cuanto se le vea pasar contra una versión conocida buena.

## [1.0.2] - 2026-08-31

### Arreglado

- **La app se cerraba al abrirla, después de actualizar a la 1.0.1.** La 1.0.0 guardaba la versión disponible como entero bajo la clave `version_publicada` y la 1.0.1 pidió ese mismo nombre como texto. DataStore guarda el tipo junto al valor, así que la lectura lanzaba `ClassCastException` dentro del flujo que alimenta el arranque, y eso cierra el proceso. Solo pasaba al instalar encima de la versión anterior: una instalación nueva no tiene nada guardado y por eso ni las pruebas ni un teléfono limpio lo veían. **No se pierde nada de lo capturado**: la 1.0.2 lee lo que dejó la 1.0.0 y sigue adelante.

### Cambiado

- Las lecturas de preferencias comprueban el tipo en tiempo de ejecución y tratan como ausente lo que no cuadre, así que un valor heredado no puede volver a cerrar la app. La regla —una clave no cambia de tipo nunca— y su prueba de actualización quedan en [modelo de datos](https://github.com/carlosalbertoxw/ollin-finanzas/blob/main/docs/modelo-de-datos.md).
- El trabajo de arranque —sembrar el catálogo, programar el aviso, buscar versión nueva— ya no puede tumbar la app: falla, se anota en el log y la app abre igual.

## [1.0.1] - 2026-08-31

### Cambiado

- **La app consulta `github.io` y no el dominio propio.** La dirección queda compilada dentro de cada APK y no se puede corregir en los que ya están instalados: `carlosalbertoxw.github.io` dura lo que el repositorio, mientras que un dominio se renueva cada año y se puede perder. El comprobador sigue **un** salto de redirección, y solo si el destino también es `https`.
- El repositorio adopta las convenciones de [Ollin Actividades](https://github.com/carlosalbertoxw/ollin-actividades): la versión sale de este archivo, la firma se toma de variables de entorno, los flujos se llaman `pruebas`, `publicacion` y `sitio`, y el sitio le pregunta a GitHub por la release en vez de dar por hecho lo que se compiló.

## [1.0.0] - 2026-08-30

Primera versión pública.

### Añadido

- **El libro.** Entradas, salidas, transferencias y saldos de apertura, con cuentas y categorías de listas cerradas. Los importes viven como centavos enteros, así que un saldo en cero es exactamente cero y las conciliaciones cuadran.
- **Transferencias en una captura.** Mover dinero entre tus cuentas escribe los dos renglones ligados por un mismo grupo. Borrar uno borra el otro: nunca queda media transferencia.
- **Patrimonio aparte del consumo.** Comprar un terreno o cripto es trasladar patrimonio, no gastarlo, y los tableros lo cuentan por separado. Por eso la tasa de ahorro no se desploma el mes que compraste algo grande.
- **Compromisos.** Mensualidades, suscripciones, la renta, el seguro anual, con periodicidad de semanal a anual. El plan solo avanza cuando lo decides —cumplir o descartar— y las dos se deshacen devolviendo el plan justo a donde estaba, incluso uno que cae en día 31.
- **Aviso diario de lo que vence**, a la hora que elijas en Ajustes.
- **Salud de los datos.** Nueve revisiones que corren en continuo: tipos que contradicen al signo del importe, transferencias sin pareja, saldos iniciales duplicados, movimientos sin categoría, medios incoherentes y descripciones casi idénticas. Tres se reparan con un botón, dentro de una sola transacción y sin tocar nunca el importe.
- **Presupuesto y analítica.** Meta contra realidad por categoría y mes, con las metas copiables al mes siguiente, y tendencia mensual.
- **Exportación e importación en Excel.** Un `.xlsx` con fórmulas vivas (`SUMIFS`), escrito y leído sin dependencias externas. Lo que sale puede volver a entrar, catálogos incluidos, y la importación es atómica: o entra todo o no entra nada.
- **Base cifrada.** AES-256 con SQLCipher y la frase envuelta en el Keystore de Android. No hay camino sin cifrar.
- **Bloqueo opcional.** Con la credencial del teléfono (patrón, PIN, huella) o con un PIN propio de Ollin, con espera creciente ante los intentos fallidos.
- **Aviso de actualizaciones.** Ollin consulta una vez al día si hay una versión más nueva publicada y lo enseña en *Acerca de*. Se apaga en Ajustes. Ver [seguridad y privacidad](https://github.com/carlosalbertoxw/ollin-finanzas/blob/main/docs/seguridad.md).
- **Sitio de descarga** en GitHub Pages, con el APK firmado, su huella y las instrucciones de instalación fuera de la tienda.

[Sin publicar]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.3...v1.1.0
[1.0.3]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/carlosalbertoxw/ollin-finanzas/releases/tag/v1.0.0
