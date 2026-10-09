package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.finanzas.data.seguridad.ControlBloqueo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * El candado es codigo de seguridad y no tenia ni una prueba: arrancar cerrado,
 * la gracia del selector de archivos --y solo la de el-- y el freno contra la fuerza bruta son
 * justo las reglas donde un error deja la app abierta a quien no debia.
 *
 * Ni base ni Robolectric: el control recibe un flujo de preferencias y un reloj,
 * asi que aqui se le empujan a mano y las esperas no cuestan tiempo real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlBloqueoTest {

    private val preferencias = MutableStateFlow(Ajustes())
    private var fallosGuardados: Int? = null
    private var huellaGuardada: Pair<String, String>? = null
    private var ahora = 1_000_000L

    private val ambitos = mutableListOf<CoroutineScope>()

    /**
     * Scope propio y no el de la prueba: el control se queda coleccionando las
     * preferencias para siempre y `runTest` esperaria por siempre a ese hijo.
     *
     * Con `UnconfinedTestDispatcher` el `collect` corre al construirse y emite
     * el valor que ya trae el StateFlow, asi que cada prueba arranca con las
     * preferencias ya leidas y sin tener que adelantar el reloj virtual.
     */
    private fun TestScope.control(flujo: Flow<Ajustes> = preferencias): ControlBloqueo {
        val ambito = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        ambitos += ambito
        return ControlBloqueo(
            preferencias = flujo,
            guardaFallos = { fallosGuardados = it },
            sello = SELLO,
            guardaHuella = { hash, sal -> huellaGuardada = hash to sal },
            reloj = { ahora },
            ambito = ambito
        )
    }

    @After
    fun cierraLosAmbitos() {
        ambitos.forEach { it.cancel() }
        ambitos.clear()
    }

    private fun conPin() {
        preferencias.value = Ajustes(modoBloqueo = ModoBloqueo.PIN, pinHash = "h", pinSal = "s")
    }

    // ------------------------------------------------------------- candado

    /** Con un flujo que todavia no emite: es el instante antes de leer el disco. */
    @Test
    fun `arranca bloqueado antes de saber si hay candado puesto`() = runTest {
        val bloqueo = control(MutableSharedFlow())

        assertTrue(
            "Equivocarse hacia el lado cerrado solo cuesta un parpadeo",
            bloqueo.bloqueado.value
        )
    }

    @Test
    fun `sin candado configurado se abre sola`() = runTest {
        val bloqueo = control()

        assertFalse(bloqueo.bloqueado.value)
    }

    @Test
    fun `con candado puesto sigue cerrada aunque se lean las preferencias`() = runTest {
        conPin()
        val bloqueo = control()

        assertTrue(bloqueo.bloqueado.value)
    }

    @Test
    fun `volver del selector dentro de la gracia no vuelve a cerrar`() = runTest {
        conPin()
        val bloqueo = control()
        bloqueo.desbloquea()

        bloqueo.esperaVueltaDelSistema()
        bloqueo.alIrAlFondo()
        ahora += ControlBloqueo.GRACIA_MILLIS - 1
        bloqueo.alVolverAlFrente()

        assertFalse("Elegir un .xlsx no debe expulsarte de la app", bloqueo.bloqueado.value)
    }

    @Test
    fun `volver del selector despues de la gracia cierra otra vez`() = runTest {
        conPin()
        val bloqueo = control()
        bloqueo.desbloquea()

        bloqueo.esperaVueltaDelSistema()
        bloqueo.alIrAlFondo()
        ahora += ControlBloqueo.GRACIA_MILLIS
        bloqueo.alVolverAlFrente()

        assertTrue(bloqueo.bloqueado.value)
    }

    /**
     * El caso que el candado quiere cubrir: pulsar Inicio y pasarle el telefono
     * a alguien. Sin una vuelta esperada no hay gracia, ni de un milisegundo.
     */
    @Test
    fun `salir al fondo sin avisar cierra de inmediato`() = runTest {
        conPin()
        val bloqueo = control()
        bloqueo.desbloquea()

        bloqueo.alIrAlFondo()
        bloqueo.alVolverAlFrente()

        assertTrue(bloqueo.bloqueado.value)
    }

    /** La gracia se gasta en el primer regreso: no queda guardada para despues. */
    @Test
    fun `la gracia no se hereda al siguiente viaje`() = runTest {
        conPin()
        val bloqueo = control()
        bloqueo.desbloquea()

        bloqueo.esperaVueltaDelSistema()
        bloqueo.alIrAlFondo()
        bloqueo.alVolverAlFrente()
        assertFalse(bloqueo.bloqueado.value)

        bloqueo.alIrAlFondo()
        ahora += 1
        bloqueo.alVolverAlFrente()

        assertTrue("El segundo viaje no se aviso", bloqueo.bloqueado.value)
    }

    /** Desbloquear tambien olvida un aviso que nadie llego a gastar. */
    @Test
    fun `un aviso sin gastar no sobrevive a desbloquear`() = runTest {
        conPin()
        val bloqueo = control()

        bloqueo.esperaVueltaDelSistema()
        bloqueo.desbloquea()
        bloqueo.alIrAlFondo()
        bloqueo.alVolverAlFrente()

        assertTrue(bloqueo.bloqueado.value)
    }

    /** Sin candado configurado, el tiempo fuera no cierra nada. */
    @Test
    fun `sin candado, tardarse fuera no cierra la app`() = runTest {
        val bloqueo = control()

        bloqueo.alIrAlFondo()
        ahora += ControlBloqueo.GRACIA_MILLIS * 10
        bloqueo.alVolverAlFrente()

        assertFalse(bloqueo.bloqueado.value)
    }

    @Test
    fun `volver al frente sin haber ido al fondo no cambia nada`() = runTest {
        conPin()
        val bloqueo = control()
        bloqueo.desbloquea()

        bloqueo.alVolverAlFrente()

        assertFalse(bloqueo.bloqueado.value)
    }

    /** Girar el telefono no debe poder abrirla: por eso el estado vive aqui. */
    @Test
    fun `el reloj se detiene mientras la app esta al frente`() = runTest {
        conPin()
        val bloqueo = control()
        bloqueo.desbloquea()

        ahora += ControlBloqueo.GRACIA_MILLIS * 5
        bloqueo.alVolverAlFrente()

        assertFalse(bloqueo.bloqueado.value)
    }

    // -------------------------------------------------- freno de fuerza bruta

    @Test
    fun `los primeros fallos no cuestan espera`() {
        for (fallos in 0..ControlBloqueo.FALLOS_DE_GRACIA) {
            assertEquals(
                "Teclear mal el PIN es normal: el fallo $fallos sale gratis",
                0L,
                ControlBloqueo.esperaMillis(fallos)
            )
        }
    }

    @Test
    fun `la espera duplica a partir del quinto fallo y topa`() {
        assertEquals(1_000L, ControlBloqueo.esperaMillis(5))
        assertEquals(2_000L, ControlBloqueo.esperaMillis(6))
        assertEquals(4_000L, ControlBloqueo.esperaMillis(7))
        assertEquals(ControlBloqueo.ESPERA_MAXIMA_MILLIS, ControlBloqueo.esperaMillis(50))
        assertEquals(
            "No se desborda con un numero absurdo",
            ControlBloqueo.ESPERA_MAXIMA_MILLIS,
            ControlBloqueo.esperaMillis(5_000)
        )
    }

    @Test
    fun `fallar cinco veces obliga a esperar y el reloj la va consumiendo`() = runTest {
        conPin()
        val bloqueo = control()

        repeat(5) { bloqueo.registraFalloDePin() }
        assertEquals(1, bloqueo.segundosDeEspera())

        ahora += 1_000
        assertEquals("Cumplida la espera, se puede volver a intentar", 0, bloqueo.segundosDeEspera())
    }

    @Test
    fun `cada fallo queda guardado en disco`() = runTest {
        conPin()
        val bloqueo = control()

        repeat(3) { bloqueo.registraFalloDePin() }

        assertEquals(3, fallosGuardados)
    }

    @Test
    fun `acertar limpia la cuenta y abre la app`() = runTest {
        conPin()
        val bloqueo = control()
        repeat(6) { bloqueo.registraFalloDePin() }
        assertTrue(bloqueo.segundosDeEspera() > 0)

        bloqueo.registraAciertoDePin()

        assertEquals(0, bloqueo.segundosDeEspera())
        assertEquals("El contador en disco tambien se limpia", 0, fallosGuardados)
        assertFalse(bloqueo.bloqueado.value)
    }

    /**
     * Lo que hace que el freno sirva: si el conteo viviera en memoria, cerrar la
     * app de un manotazo lo reiniciaria y probar diez mil PIN volveria a ser
     * gratis. Al arrancar, el control retoma los fallos que venian guardados.
     */
    @Test
    fun `el conteo de fallos sobrevive a que se reinicie el proceso`() = runTest {
        preferencias.value = Ajustes(modoBloqueo = ModoBloqueo.PIN, pinFallos = 5)

        // Una instancia nueva es lo mas parecido a volver a abrir la app.
        val bloqueo = control()
        bloqueo.registraFalloDePin()

        assertEquals(6, fallosGuardados)
        assertEquals(
            "El sexto fallo debe costar lo que cuesta un sexto, no un primero",
            (ControlBloqueo.esperaMillis(6) / 1000L).toInt(),
            bloqueo.segundosDeEspera()
        )
    }

    /**
     * Cerrar la app desde Recientes despues de cada fallo no debe regalar un
     * intento: con los fallos ya guardados, el control nuevo arranca cobrando
     * la espera que les toca.
     */
    @Test
    fun `reabrir la app con fallos guardados arranca con la espera puesta`() = runTest {
        preferencias.value = Ajustes(modoBloqueo = ModoBloqueo.PIN, pinFallos = 6)

        val bloqueo = control()

        assertEquals(
            (ControlBloqueo.esperaMillis(6) / 1000L).toInt(),
            bloqueo.segundosDeEspera()
        )

        ahora += ControlBloqueo.esperaMillis(6)
        assertEquals("Cumplida la espera, se puede volver a intentar", 0, bloqueo.segundosDeEspera())
    }

    @Test
    fun `reabrir con fallos dentro de la gracia no cobra espera`() = runTest {
        preferencias.value = Ajustes(
            modoBloqueo = ModoBloqueo.PIN,
            pinFallos = ControlBloqueo.FALLOS_DE_GRACIA
        )

        val bloqueo = control()

        assertEquals(0, bloqueo.segundosDeEspera())
    }

    @Test
    fun `un libro recien instalado no arranca con espera`() = runTest {
        val bloqueo = control()

        assertEquals(0, bloqueo.segundosDeEspera())
    }

    // ------------------------------------------------------- intentos de PIN

    @Test
    fun `un PIN correcto contra su huella sellada abre sin migrar nada`() = runTest {
        val bloqueo = control()
        val (hash, sal) = bloqueo.huellaNueva("2468")

        assertTrue(hash.startsWith("ks1:"))
        assertEquals(ControlBloqueo.IntentoDePin.Correcto, bloqueo.intentaPin("2468", hash, sal))
        assertEquals(null, huellaGuardada)
        assertFalse(bloqueo.bloqueado.value)
    }

    /**
     * La huella de la 1.2.0 y anteriores es PBKDF2 a secas. Tiene que seguir
     * abriendo, y en cuanto abre se cambia por la sellada con la misma sal.
     */
    @Test
    fun `acertar contra una huella vieja la guarda sellada`() = runTest {
        val bloqueo = control()
        val sal = ClavePin.nuevaSal()
        val vieja = ClavePin.deriva("2468", sal)

        assertEquals(ControlBloqueo.IntentoDePin.Correcto, bloqueo.intentaPin("2468", vieja, sal))

        val (nueva, mismaSal) = huellaGuardada!!
        assertEquals(sal, mismaSal)
        assertEquals(ClavePin.huella("2468", sal, SELLO), nueva)
        assertEquals(
            ControlBloqueo.IntentoDePin.Correcto,
            bloqueo.intentaPin("2468", nueva, sal)
        )
    }

    @Test
    fun `fallar contra una huella vieja no la migra`() = runTest {
        val bloqueo = control()
        val sal = ClavePin.nuevaSal()

        bloqueo.intentaPin("1111", ClavePin.deriva("2468", sal), sal)

        assertEquals(null, huellaGuardada)
        assertEquals(1, fallosGuardados)
    }

    /**
     * Lo que compra el sello: la huella copiada a otro aparato no se puede
     * comprobar alli, porque la llave del sello no salio de este.
     */
    @Test
    fun `una huella sellada no coincide con otro sello`() = runTest {
        val bloqueo = control()
        val (hash, sal) = bloqueo.huellaNueva("2468")

        assertFalse(ClavePin.coincide("2468", hash, sal, OTRO_SELLO))
        assertFalse("Sin sello no se puede comprobar", ClavePin.coincide("2468", hash, sal))
    }

    /**
     * El freno es el mismo para todos los que piden el PIN, Ajustes incluido:
     * en espera ni siquiera se comprueba, aunque el PIN sea el correcto.
     */
    @Test
    fun `en espera no se comprueba ni el PIN correcto`() = runTest {
        val bloqueo = control()
        val (hash, sal) = bloqueo.huellaNueva("2468")

        repeat(5) {
            assertEquals(
                ControlBloqueo.IntentoDePin.Incorrecto,
                bloqueo.intentaPin("0000", hash, sal)
            )
        }

        val enEspera = bloqueo.intentaPin("2468", hash, sal)
        assertTrue("Salio $enEspera", enEspera is ControlBloqueo.IntentoDePin.EnEspera)
        assertEquals("Esperar no cuenta como otro fallo", 5, fallosGuardados)

        ahora += ControlBloqueo.esperaMillis(5)
        assertEquals(ControlBloqueo.IntentoDePin.Correcto, bloqueo.intentaPin("2468", hash, sal))
        assertEquals(0, fallosGuardados)
    }

    private companion object {
        /** El Keystore no existe en la JVM: un HMAC con llave fija hace sus veces. */
        val SELLO = selloCon(1)
        val OTRO_SELLO = selloCon(2)

        fun selloCon(semilla: Byte) = ClavePin.Sello { huella ->
            Mac.getInstance("HmacSHA256")
                .apply { init(SecretKeySpec(ByteArray(32) { semilla }, "HmacSHA256")) }
                .doFinal(huella)
        }
    }
}
