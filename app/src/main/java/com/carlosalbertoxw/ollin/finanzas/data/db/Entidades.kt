package com.carlosalbertoxw.ollin.finanzas.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Periodicidad
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import java.time.LocalDate

/**
 * Las entidades de Room son tambien el modelo de dominio. Para una app de un
 * solo modulo, duplicarlas en una capa aparte solo agrega mapeo sin ganancia.
 */

@Entity(
    tableName = "cuenta",
    indices = [Index(value = ["nombre"], unique = true)]
)
data class Cuenta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val tipo: TipoCuenta,
    /** Medio sugerido al capturar en esta cuenta. Evita el error de marcar la cartera como electronica. */
    val medioPorDefecto: Medio = if (tipo == TipoCuenta.EFECTIVO) Medio.EFECTIVO else Medio.ELECTRONICO,
    /**
     * Cierto cuando por esta cuenta no puede pasar dinero en mano: una tarjeta
     * no se cobra en efectivo. Un prestamo familiar registrado con el mismo tipo
     * si puede, y por eso la regla es de la cuenta y no del tipo.
     */
    val soloElectronico: Boolean = tipo == TipoCuenta.CREDITO || tipo == TipoCuenta.CREDITO_MSI,
    /** Limite de credito en centavos, solo para tarjetas. Permite mostrar % de uso. */
    val limiteCentavos: Long? = null,
    val incluirEnPatrimonio: Boolean = true,
    val archivada: Boolean = false,
    val orden: Int = 0,
    val colorHex: String? = null,
    val notas: String? = null
)

@Entity(
    tableName = "categoria",
    indices = [Index(value = ["nombre", "padreId"], unique = true), Index("padreId")],
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["padreId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** null = categoria raiz. Con un nivel de anidamiento basta para el analisis. */
    val padreId: Long? = null,
    val tipo: TipoCategoria,
    /** Distingue lo que no puedes dejar de pagar de lo que si. */
    val esencial: Boolean = false,
    val colorHex: String? = null,
    val archivada: Boolean = false,
    val orden: Int = 0
)

@Entity(
    tableName = "movimiento",
    indices = [
        Index("fecha"),
        Index("cuentaId"),
        Index("categoriaId"),
        Index("grupoTransferencia"),
        Index("compromisoId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = Cuenta::class,
            parentColumns = ["id"],
            childColumns = ["cuentaId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Movimiento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: LocalDate,
    /** Centavos con signo: negativo sale, positivo entra. */
    @ColumnInfo(name = "importeCentavos") val importeCentavos: Long,
    val cuentaId: Long,
    val categoriaId: Long? = null,
    /** Detalle libre. Complementa a la categoria, no la sustituye: son dos campos distintos. */
    val descripcion: String,
    val medio: Medio,
    val tipo: TipoMovimiento,
    /** Se deriva, no se captura. Ver [com.carlosalbertoxw.ollin.finanzas.domain.usecase.DerivarContraparte]. */
    val contraparte: Contraparte,
    /** Une las dos patas de una transferencia. Ambas comparten el mismo uuid. */
    val grupoTransferencia: String? = null,
    val compromisoId: Long? = null,
    val nota: String? = null,
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "presupuesto",
    indices = [Index(value = ["categoriaId", "anio", "mes"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Presupuesto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoriaId: Long,
    val anio: Int,
    val mes: Int,
    /** Siempre positivo: es un tope de gasto o una meta de ingreso. */
    val montoCentavos: Long
)

@Entity(
    tableName = "compromiso",
    indices = [Index("cuentaId"), Index("categoriaId"), Index("activo")]
)
data class Compromiso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val cuentaId: Long?,
    val categoriaId: Long?,
    /** Importe de cada pago, en centavos y positivo. */
    val montoCentavos: Long,
    val periodicidad: Periodicidad = Periodicidad.MENSUAL,
    /**
     * Ancla del plan: la fecha del pago numero cero. No se mueve nunca, y por
     * eso [proximoPago] siempre se calcula hacia adelante desde aqui.
     */
    val fechaPrimerPago: LocalDate,
    /** null = indefinido (una suscripcion). Un MSI si tiene numero de pagos. */
    val totalPagos: Int? = null,
    val pagosRealizados: Int = 0,
    /**
     * Pagos que se saltaron sin cumplirse: el mes que no te cobraron, el cargo
     * que decidiste no hacer. Recorren el plan igual que un pago cumplido pero
     * no acortan un MSI, y por eso se cuentan aparte de [pagosRealizados].
     */
    val pagosDescartados: Int = 0,
    val activo: Boolean = true,
    val avisarDiasAntes: Int = 3,
    val notas: String? = null
) {
    /**
     * Cuando toca el siguiente pago.
     *
     * Siempre se cuenta sobre [fechaPrimerPago] y nunca sobre el resultado
     * anterior. `plusMonths` recorta el dia al ultimo valido del mes destino y
     * no lo recuerda, asi que encadenar sumas sobre un valor ya recortado
     * arrastra el error: un plan del 31 de enero se volvia del 28 y se quedaba
     * ahi para siempre. Desde el ancla, el 31 se recupera en cada mes que lo
     * tiene. Quien decide si el paso son dias o meses es la periodicidad.
     */
    val proximoPago: LocalDate
        get() = periodicidad.avanza(
            fechaPrimerPago,
            (pagosRealizados + pagosDescartados).toLong()
        )

    /** null = plan indefinido. */
    val pagosRestantes: Int?
        get() = totalPagos?.let { (it - pagosRealizados).coerceAtLeast(0) }
}

/**
 * Traduce una descripcion a su categoria al importar. Es lo que permite que un
 * libro con puras etiquetas planas entre ya clasificado.
 */
@Entity(
    tableName = "mapeo_descripcion",
    indices = [Index(value = ["clave"], unique = true)]
)
data class MapeoDescripcion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Descripcion normalizada (sin acentos, minusculas). */
    val clave: String,
    val categoriaId: Long,
    /** Falso cuando el usuario lo corrigio a mano; asi no lo pisa una resiembra. */
    val generadoPorSistema: Boolean = true
)
