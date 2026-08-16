# Seguridad y privacidad

Ollin Finanzas no manda nada a ningún servidor: no hay cuenta, no hay nube, no hay analítica y no hay publicidad.

## Cifrado de la base

La base va cifrada con **AES-256 (SQLCipher)**. No hay camino sin cifrar: si SQLCipher no arranca, la app no abre. Es preferible a que un libro de finanzas funcione en claro sin avisar.

```
frase aleatoria de 32 bytes (hex)
        │  envuelta con AES/GCM
        ▼
llave maestra en AndroidKeyStore  ──►  no sale del dispositivo, ni con root
```

[`LlaveBase`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/LlaveBase.kt) genera la frase una sola vez al azar y la guarda envuelta en `SharedPreferences` (`ollin_llave`). La app pide desenvolverla; nunca ve la llave maestra. Copiar el archivo `ollin.db` por adb o sacarlo de un respaldo no revela un solo importe.

Tres detalles que no son evidentes:

- **La frase se representa en hexadecimal a propósito.** SQLCipher puede recibirla por más de un camino —Room la pasa como bytes, un `ATTACH ... KEY` la pega dentro del SQL— y con texto imprimible los dos derivan exactamente la misma llave. Con bytes crudos no coincidirían.
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
- **Un minuto de gracia** al volver del fondo. Importar y exportar abren el selector de archivos del sistema, que manda la app al fondo; sin ese margen, elegir un `.xlsx` te expulsaría a medio camino.
- Se mide con el **reloj monótono** (`elapsedRealtime`): cambiar la hora del teléfono no debe poder alargar la gracia.
- Con candado configurado la ventana lleva `FLAG_SECURE`: ni capturas de pantalla ni miniatura en la vista de apps recientes. Mientras no se sabe, se asume que sí.
- **Cambiar o quitar el candado exige antes la llave que hay puesta.** Sin eso, quien encuentre la app abierta la desprotege en dos toques y el candado solo estorba a su dueño.
- Elegir el modo del teléfono cuando el teléfono no tiene patrón ni PIN no hace nada: avisa que hay que configurarlo en Android primero.

Las transiciones de bloqueo se escriben de golpe en DataStore. Si el modo y el PIN se guardaran por separado podría quedar un "modo PIN" sin PIN, y eso deja la app cerrada sin llave.

### El PIN propio

[`ClavePin`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/seguridad/ClavePin.kt) nunca guarda el PIN: guarda **PBKDF2-HMAC-SHA256, 120 000 iteraciones, 256 bits**, con sal aleatoria de 16 bytes distinta por teléfono.

Un PIN de cuatro dígitos tiene diez mil combinaciones; sin un derivado lento bastaría un segundo para probarlas todas contra el archivo de preferencias. La derivación pesa cientos de milisegundos a propósito y corre fuera del hilo principal.

La comparación es en tiempo constante (`MessageDigest.isEqual`): un `==` normal corta en el primer byte distinto, y ese tiempo de más revela cuánto del PIN se acertó.

**Si eliges PIN propio y lo olvidas, no hay forma de recuperarlo.** Habría que reinstalar la app, y con ella se van los datos que no se hayan exportado.

### La credencial del sistema

[`CredencialDelSistema`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/seguridad/CredencialDelSistema.kt) pide huella, patrón o PIN del teléfono. Desde Android 11 usa `BiometricPrompt` con `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`; antes, el diálogo unificado no admite credencial del dispositivo, así que abre la pantalla de desbloqueo del sistema.

Se usa en dos lugares: para entrar, y en Ajustes para confirmar antes de cambiar o quitar el candado.

## Respaldos

El respaldo automático y el traspaso a un teléfono nuevo **excluyen** la base, sus diarios (`-wal`, `-shm`), la envoltura de la llave y las preferencias ([`backup_rules.xml`](../app/src/main/res/xml/backup_rules.xml), [`data_extraction_rules.xml`](../app/src/main/res/xml/data_extraction_rules.xml)).

La razón es física: una llave del Keystore no se puede restaurar ni transferir, así que la copia llegaría ilegible y el usuario creería tener un respaldo que no sirve.

**El respaldo real es la exportación a `.xlsx`**, que el usuario decide dónde guardar. Ver [Excel](excel.md).

## Permisos

Solo tres, y ninguno da acceso a datos ajenos a la app:

| Permiso | Para qué |
|---|---|
| `USE_BIOMETRIC` | Desbloquear con huella o credencial del teléfono |
| `POST_NOTIFICATIONS` | Avisar de compromisos por vencer |
| `RECEIVE_BOOT_COMPLETED` | Reprogramar la revisión diaria tras reiniciar |

Los archivos se leen y se escriben por el **selector del sistema** (Storage Access Framework), así que no hace falta permiso de almacenamiento: la app solo ve el archivo que el usuario eligió.

La alarma de los recordatorios es **inexacta** a propósito: un recordatorio de finanzas no justifica pedir el permiso de alarma exacta ni gastar batería. Si el permiso de notificaciones no está concedido, la app simplemente no notifica.

## Manejo de errores

Los mensajes que ve el usuario ocultan los internos a propósito: el texto crudo de una excepción habla de rutas, clases y consultas, no le sirve de nada y de paso enseña cómo está hecha la app. El fallo real va a logcat, sin datos del usuario.
