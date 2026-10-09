# Seguridad y privacidad

Ollin Finanzas no manda a ningún servidor nada de lo que capturas: no hay cuenta, no hay nube, no hay analítica y no hay publicidad.

Hace **una** llamada a internet, y conviene decirla completa: le pregunta al sitio del proyecto, una vez al día, si existe una versión más nueva. Es un `GET` a un archivo estático que no lleva ningún dato tuyo —ni identificador, ni qué versión traes— y se apaga en *Ajustes*. La app no se instala desde Google Play, así que sin eso nadie se enteraría nunca de una actualización. Los detalles están en [publicación](publicacion.md#cómo-se-entera-la-app).

## Por dónde podría entrar alguien

Lo que se protege es el libro: importes, cuentas, a quién se paga y cuándo. Y, porque la app se distribuye fuera de la tienda, también la confianza en que lo que se instala salió de este repositorio. Estas son las puertas, qué las cierra y qué riesgo se acepta a sabiendas. Cuando se agregue una puerta nueva —un intent que reciba archivos, otra llamada a la red, una dependencia que toque los datos—, va aquí antes que en el código.

| Entrada | Quién | Qué lo detiene | Riesgo aceptado |
|---|---|---|---|
| El teléfono en otras manos, desbloqueado | Alguien cercano | Candado con PIN propio o credencial del sistema; se vuelve a cerrar en cuanto la app sale al fondo; `FLAG_SECURE` sin capturas ni miniatura; freno con espera creciente para todo PIN, también el de *Ajustes* | La vuelta del selector de archivos o de la credencial del sistema tiene un minuto de gracia: es lo que permite elegir un archivo sin que te expulse |
| El teléfono en otras manos, con root o por adb | Alguien con tiempo y herramientas | Base cifrada con una llave del Keystore; huella del PIN sellada con otra; nada de eso entra al respaldo | Con root se puede *usar* el Keystore aunque no copiarlo: dentro de un teléfono comprometido no hay defensa desde la app |
| Un `.xlsx` ajeno que se importa | Quien te mande un «respaldo» | Sin `DOCTYPE` (bomba de entidades); tope de bytes por parte y de partes (zip bomb); tope de filas, columnas y celdas; la importación entera va en una transacción | Lo que el archivo diga se importa: si alguien te convence de importar datos falsos, quedan en tu libro |
| El aviso de versión nueva (`version.json`) | Quien controle la red o el dominio | Solo `https`; un salto como mucho, y solo hacia el sitio; el enlace solo puede ir a las releases de este repositorio o al sitio; la app nunca descarga ni instala | Quien tome la cuenta de GitHub controla las releases y el sitio a la vez; ver la fila siguiente |
| La cadena de publicación | Quien tome la cuenta de GitHub o una acción de terceros | Acciones fijadas por SHA; los secretos de firma van solo en el environment `release`, limitado a tags `v*` (ver [publicación](publicacion.md#los-secretos)); la huella del certificado se comprueba contra el README antes de publicar | La cuenta de GitHub (2FA) y el `.jks` con sus contraseñas son la raíz de todo: con ellos se publica una actualización que Android acepta |
| Las dependencias | Un mantenedor comprometido | Pocas y justificadas; Dependabot; CodeQL; `npm audit` en CI; versiones mínimas forzadas en el build; SBOM de cada release | Lo que llegue firmado por un mantenedor legítimo se compila |

## Cifrado de la base

La base va cifrada con **AES-256 (SQLCipher)**. No hay camino sin cifrar: si SQLCipher no arranca, la app no abre. Es preferible a que un libro de finanzas funcione en claro sin avisar.

```
frase aleatoria de 32 bytes (hex)
        │  envuelta con AES/GCM
        ▼
llave maestra en AndroidKeyStore  ──►  no se puede copiar fuera del teléfono
```

[`LlaveBase`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/LlaveBase.kt) genera la frase una sola vez al azar y la guarda envuelta en `SharedPreferences` (`ollin_llave`). La app pide desenvolverla; nunca ve la llave maestra. Copiar el archivo `ollin.db` por adb o sacarlo de un respaldo no revela un solo importe.

Lo que el Keystore **no** promete: en un teléfono con root, quien controla el sistema puede pedirle que desenvuelva la frase haciéndose pasar por la app, y con ella abrir la base ahí mismo. La llave no sale, pero se puede usar. Contra eso no hay defensa desde la app; el cifrado protege la base fuera del teléfono, no dentro de uno comprometido.

Tres detalles que no son evidentes:

- **Los 32 bytes al azar se representan en hexadecimal a propósito.** La frase viaja como texto: se guarda en `SharedPreferences` y se le entrega a SQLCipher como cadena. En bytes crudos el resultado dependería de por dónde pase —cualquier codificación de por medio los alteraría— y la base no volvería a abrir.
- **La llave del Keystore no exige desbloqueo del usuario** (`setUserAuthenticationRequired(false)`): la base se abre antes de que puedas autenticarte, y exigirlo dejaría la app sin arrancar.
- **La frase nueva se escribe con `commit()` y no `apply()`**: si el proceso muriera antes de persistirla, la base quedaría cifrada con una frase que ya nadie conoce.

La lectura de la frase es síncrona a propósito: Room construye el helper sin corrutinas, y una lectura de preferencias más un desenvuelto de Keystore son microsegundos.

## Bloqueo de la app

Tres modos ([`ModoBloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/prefs/Ajustes.kt)):

| Modo | Con qué se abre |
|---|---|
| `NINGUNO` | Sin bloqueo |
| `SISTEMA` | Patrón, PIN, contraseña o huella del propio teléfono |
| `PIN` | Un PIN exclusivo de Ollin Finanzas, de 4 dígitos en adelante |

[`ControlBloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/ControlBloqueo.kt) vive en el `Contenedor` y no en un ViewModel, porque debe sobrevivir a que la actividad se recree: si el estado se perdiera al girar el teléfono, girarlo sería la forma de saltarse el candado.

Detalles del comportamiento:

- **Arranca bloqueada.** Todavía no se sabe si hay candado puesto, y equivocarse hacia el lado cerrado solo cuesta un parpadeo; hacia el lado abierto enseña tus finanzas a quien no debía.
- **Sin gracia al salir.** Pulsar Inicio, cambiar de app o apagar la pantalla la cierra en cuanto vuelve: es justo el caso que el candado quiere cubrir, el teléfono que pasa a otras manos.
- **Un minuto de gracia solo para las vueltas esperadas.** Importar y exportar abren el selector de archivos del sistema, y antes de Android 11 confirmar con la credencial abre la pantalla de desbloqueo del sistema; las dos mandan la app al fondo, y sin margen elegir un `.xlsx` te expulsaría a medio camino. La pantalla avisa al candado justo antes de abrirlas (`esperaVueltaDelSistema`), y la gracia se gasta en ese regreso: no se hereda al siguiente.
- **Girar el teléfono no es salir.** La actividad se detiene y se recrea, pero `isChangingConfigurations` la distingue de una salida de verdad.
- Se mide con el **reloj monótono** (`elapsedRealtime`): cambiar la hora del teléfono no debe poder alargar la gracia.
- Con candado configurado la ventana lleva `FLAG_SECURE`: ni capturas de pantalla ni miniatura en la vista de apps recientes. Mientras no se sabe, se asume que sí.
- **Cambiar o quitar el candado exige antes la llave que hay puesta.** Sin eso, quien encuentre la app abierta la desprotege en dos toques y el candado solo estorba a su dueño.
- Elegir el modo del teléfono cuando el teléfono no tiene patrón ni PIN no hace nada: avisa que hay que configurarlo en Android primero.

Las transiciones de bloqueo se escriben de golpe en DataStore. Si el modo y el PIN se guardaran por separado podría quedar un "modo PIN" sin PIN, y eso deja la app cerrada sin llave.

**Un modo que no se puede leer no abre la app.** El resto de las preferencias, si no se entienden, vuelven a su valor de fábrica, y para el candado el de fábrica es no tenerlo. Por eso `AjustesRepositorio.leeModoBloqueo` distingue no haber puesto candado (la clave no está, que es lo que deja quitarlo) de no poder leer cuál se puso: un enum renombrado, una clave que cambió de tipo, un archivo dañado. En ese caso lo deduce de lo que sí se lee: si hay huella de PIN, el PIN; si no, la credencial del teléfono. Solo se queda abierta si el teléfono no tiene ningún bloqueo, porque entonces no hay con qué cerrar. Los enums que se guardan por nombre están además fijados en `proguard-rules.pro`, para que R8 no los renombre.

### El PIN propio

[`ClavePin`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/ClavePin.kt) nunca guarda el PIN: guarda **PBKDF2-HMAC-SHA256, 120 000 iteraciones, 256 bits**, con sal aleatoria de 16 bytes distinta por teléfono.

Un PIN de cuatro dígitos tiene diez mil combinaciones; sin un derivado lento bastaría un segundo para probarlas todas contra el archivo de preferencias. La derivación pesa cientos de milisegundos a propósito y corre fuera del hilo principal.

La comparación es en tiempo constante (`MessageDigest.isEqual`): un `==` normal corta en el primer byte distinto, y ese tiempo de más revela cuánto del PIN se acertó.

**La huella va sellada con una llave del Keystore.** Diez mil combinaciones siguen siendo pocas: con el archivo de preferencias en la mano —un teléfono con root— ni seiscientas mil iteraciones aguantarían más que unos minutos. Por eso, además de PBKDF2, la huella pasa por un HMAC-SHA256 cuya llave vive en el Keystore ([`LlaveDelPin`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/LlaveDelPin.kt), alias `ollin_pin`) y no se puede extraer. Sin el teléfono no se puede calcular; en el teléfono, cada intento pasa por la app y su freno. Las huellas selladas llevan el prefijo `ks1:`.

Las de la 1.2.0 y anteriores son PBKDF2 a secas. Siguen abriendo, y en cuanto su dueño acierta se guardan selladas con la misma sal, sin pedirle nada. Si la llave del sello se perdiera, la huella dejaría de coincidir, igual que la base dejaría de abrir: las dos viven y mueren con el Keystore de la app.

**Si eliges PIN propio y lo olvidas, no hay forma de recuperarlo.** Habría que reinstalar la app, y con ella se van los datos que no se hayan exportado.

#### El freno contra la fuerza bruta

PBKDF2 encarece cada intento, pero por sí solo no impide que alguien con el teléfono en la mano siga probando. [`ControlBloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/ControlBloqueo.kt) lleva la cuenta de fallos seguidos, y **todo PIN que se teclea en la app pasa por su `intentaPin`**: el de la pantalla de bloqueo y también el que pide *Ajustes* antes de cambiarlo o quitarlo. Hasta la 1.2.0 ese segundo diálogo lo comprobaba por su cuenta y sin espera, así que quien encontrara la app abierta podía probar los diez mil ahí:

- Los primeros cuatro salen gratis: teclear mal el PIN es normal.
- A partir del quinto la espera **duplica** —1 s, 2 s, 4 s…— hasta un tope de 5 minutos. Más allá, castigar más solo estorbaría al dueño.
- Acertar limpia la cuenta: el freno es contra quien adivina, no contra ti.

**El contador vive en DataStore, no en memoria.** Si viviera en el proceso, cerrar la app de un manotazo lo reiniciaría y probar diez mil PIN volvería a ser gratis.

**Y al arrancar se vuelve a cobrar la espera.** La espera en curso usa el reloj monótono, que no se puede guardar porque se reinicia con el teléfono. Hasta la 1.1.0 eso dejaba un hueco: cerrar la app desde Recientes después de cada fallo daba un intento sin esperar. Ahora, en cuanto el control lee los fallos guardados, fija la espera completa que les toca, contada desde ese momento. Es más estricto que retomar la que quedaba, y no se puede esquivar. La pantalla del candado escucha esa espera en vez de leerla una sola vez, porque las preferencias pueden llegar después de que la pantalla ya se dibujó.

Los intentos van de uno en uno (un `Mutex`): sin eso, dos toques seguidos leerían los dos que no hay espera antes de que el primero registrara su fallo. En espera no se comprueba nada, ni siquiera el PIN correcto.

El control recibe el flujo de preferencias, la función que guarda los fallos, el sello y la que guarda la huella migrada, no el `AjustesRepositorio` entero: es código de seguridad y sus reglas tienen que poder probarse sin levantar DataStore. El reloj también se inyecta, así que las pruebas de espera no cuestan tiempo real.

### La credencial del sistema

[`CredencialDelSistema`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/seguridad/CredencialDelSistema.kt) pide huella, patrón o PIN del teléfono. Desde Android 11 usa `BiometricPrompt` con `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`; antes, el diálogo unificado no admite credencial del dispositivo, así que abre la pantalla de desbloqueo del sistema.

**El éxito no se cree por el callback.** El diálogo avisa con `onAuthenticationSucceeded`, y en un teléfono con root alguien puede invocar esa función a mano —con Frida, por ejemplo— y abrir el candado sin poner el dedo. Por eso el diálogo recibe un cifrador de [`LlaveDeDesbloqueo`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/LlaveDeDesbloqueo.kt): una llave del Keystore que solo se puede usar justo después de una autenticación real (`setUserAuthenticationParameters(0, …)`), y quien lo comprueba es el hardware. Al volver, la app intenta cifrar con ese cifrador; si no puede, nadie se autenticó, diga lo que diga el callback.

Eso obliga a pedir huella de **clase fuerte**: las débiles no pueden habilitar una llave del Keystore. Quien solo tenga una débil —algunos desbloqueos con la cara— entra con el patrón o el PIN del teléfono.

Si una huella nueva o un cambio del bloqueo del teléfono invalida la llave, se crea otra y basta con autenticarse de nuevo. Y si en algún teléfono el Keystore no deja preparar la llave, se cae a la pantalla de desbloqueo del sistema en vez de dejar a nadie fuera de su libro.

La llave no protege la base, que va con la suya y sin exigir autenticación (ver [cifrado de la base](#cifrado-de-la-base)): solo vuelve inútil el atajo de saltarse el diálogo.

Se usa en dos lugares: para entrar, y en Ajustes para confirmar antes de cambiar o quitar el candado.

## Respaldos

El respaldo automático y el traspaso a un teléfono nuevo **excluyen** la base, sus diarios (`-wal`, `-shm`), la envoltura de la llave y las preferencias ([`backup_rules.xml`](../app/src/main/res/xml/backup_rules.xml), [`data_extraction_rules.xml`](../app/src/main/res/xml/data_extraction_rules.xml)).

La razón es física: una llave del Keystore no se puede restaurar ni transferir, así que la copia llegaría ilegible y el usuario creería tener un respaldo que no sirve.

**El respaldo real es la exportación a `.xlsx`**, que el usuario decide dónde guardar. Ver [Excel](excel.md).

### El recordatorio

Como el respaldo no lo hace nadie más, la app lo recuerda: un aviso **cada siete días** si no se ha exportado, y otro **cuando encuentra una versión nueva**, porque actualizar es justo el momento en que conviene tener una copia. Los dos llevan directo a la pantalla de Archivo; un recordatorio que abre el tablero deja el trabajo a medias.

Las reglas están en [`Respaldos`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/notify/Respaldos.kt), en funciones puras sobre instantes:

- **La semana se cuenta desde el último respaldo**, o desde un ancla si nunca hubo ninguno. Avisar a los siete días de instalar a quien exportó ayer sería ruido, y el ruido se apaga.
- **El ancla se pone en el primer arranque que ve esta función**, no en la instalación de la app: quien ya la tenía empieza a contar el día que actualiza, sin recibir un aviso inmediato por no haber exportado nunca.
- **El aviso dice cuántos días llevas sin respaldo.** «Hace 21 días» mueve a alguien a exportar; «acuérdate de respaldar» ya no lo lee nadie a la tercera semana.
- **El de versión nueva se manda una sola vez por versión.** Repetirlo cada día hasta que alguien actualice acaba con el canal silenciado — y con él se irían los avisos de compromisos, que son los que se usan a diario.
- **Se cuelga de la revisión diaria**, a la hora que elijas en Ajustes, en vez de tener su propia alarma: otra alarma sería otra cosa que reprogramar tras cada reinicio para preguntar una vez por semana algo que aquí se responde con una resta.

Se apaga en `Ajustes → Respaldo`. Encenderlo o apagarlo reinicia la cuenta.

#### También en el tablero

La notificación se pierde entre las demás, y una vez descartada no vuelve hasta la semana siguiente. Por eso, mientras toque respaldar, el mismo aviso sale también **arriba del tablero cada vez que se abre la app**, con el mismo texto y con las mismas reglas: si no saldría la notificación, tampoco sale esto. Tocarlo lleva a Archivo, y **en cuanto se exporta desaparece solo**, porque exportar guarda la fecha del último respaldo y el tablero la está escuchando.

La cruz lo quita **solo por esta vez**: «ahora no» no es «nunca». Vuelve la siguiente vez que se abra la app, que aquí significa arrancarla de cero o regresar después de más de un minuto fuera. Es el mismo minuto que el candado le da a la vuelta del selector, y por la misma razón: importar y exportar abren el selector de archivos del sistema, que manda la app al fondo, y volver de ahí no es abrirla otra vez. Las reglas están en [`AvisoDeRespaldo`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/notify/AvisoDeRespaldo.kt), que vive en el contenedor para que girar el teléfono no lo traiga de vuelta.

## Permisos

Solo cuatro, y ninguno da acceso a datos ajenos a la app:

| Permiso | Para qué |
|---|---|
| `INTERNET` | Preguntar una vez al día si hay una versión nueva. Nada más pasa por aquí |
| `USE_BIOMETRIC` | Desbloquear con huella o credencial del teléfono |
| `POST_NOTIFICATIONS` | Avisar de compromisos por vencer y recordar el respaldo |
| `RECEIVE_BOOT_COMPLETED` | Reprogramar la revisión diaria tras reiniciar |

Los archivos se leen y se escriben por el **selector del sistema** (Storage Access Framework), así que no hace falta permiso de almacenamiento: la app solo ve el archivo que el usuario eligió.

La alarma de los recordatorios es **inexacta** a propósito: un recordatorio de finanzas no justifica pedir el permiso de alarma exacta ni gastar batería. El sistema puede correrla unos minutos, y la pantalla de Ajustes lo dice donde se elige la hora — es una hora aproximada, no un despertador.

`POST_NOTIFICATIONS` **se pide en tiempo de ejecución**, desde [`PideAvisos`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/PermisoAvisos.kt). Desde Android 13 nace denegado y declararlo en el manifiesto no basta: sin pedirlo, el aviso diario se construiría y se descartaría en silencio, y la app parecería no tener recordatorios sin que nada lo delatara desde dentro.

Se pide con la app ya desbloqueada y no al arrancar: un diálogo del sistema encima de la pantalla del candado no se entiende, porque todavía no se ha visto de qué app viene. Si no se concede, la app simplemente no notifica — todo lo demás funciona igual.

## Notificaciones

Una notificación vive fuera del candado: la ve quien mire la pantalla de bloqueo o baje la cortina. Android solo oculta su contenido si la persona eligió *ocultar contenido sensible*, y eso no es lo que viene de fábrica.

Por eso, **con candado puesto, los avisos de compromisos no dicen ni el nombre ni el monto**: solo «Tienes un pago por vencer» o «Tienes un pago vencido». Quien le puso candado a sus finanzas no debería encontrarse «Renta · $8,500» en la pantalla del teléfono. Sin candado, el aviso dice qué, cuánto y cuándo, como siempre. Ver [`Recordatorios.textoDelAviso`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/notify/Recordatorios.kt).

Además, todos los avisos llevan una versión pública genérica, que es la que enseña la pantalla de bloqueo cuando sí se eligió ocultar el contenido sensible.

## Manejo de errores

Los mensajes que ve el usuario ocultan los internos a propósito: el texto crudo de una excepción habla de rutas, clases y consultas, no le sirve de nada y de paso enseña cómo está hecha la app. El fallo real va a logcat, sin datos del usuario.

### El informe del último fallo

No hay reporte de fallos, ni lo habrá: nada sale del teléfono. Pero sin ningún rastro, una app que se cierra al abrirse —como la 1.0.1 en los teléfonos que venían de la 1.0.0— solo se descubre cuando alguien lo cuenta, y entonces no hay con qué diagnosticar.

[`RegistroDeFallos`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/diagnostico/RegistroDeFallos.kt) se instala lo primero en `OllinApp.onCreate()` y, si un error cierra la app, guarda el último en `ultimo-fallo.txt`, en el almacenamiento privado: versión de la app, versión de Android, fecha y la traza, recortada a 16 KB. Nada más del teléfono. Se encadena delante del manejador que ya hubiera, así que el sistema sigue cerrando el proceso como siempre. También se guarda ahí, a mano, una base que no abre al arrancar: ese fallo no cierra el proceso —la app enseña una pantalla que lo explica— y es justo el que más importa poder contar después.

*Acerca de* lo enseña tal cual, y la persona decide si lo copia para reportarlo o lo borra. Está excluido del respaldo del sistema, igual que la base.
