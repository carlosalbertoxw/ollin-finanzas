package mx.ollin.finanzas.data.db

import androidx.room.TypeConverter
import mx.ollin.finanzas.domain.model.Contraparte
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.Periodicidad
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import java.time.LocalDate

class Convertidores {

    /** Se guarda el dia epoch: entero, ordenable y sin zona horaria de por medio. */
    @TypeConverter fun fechaADia(valor: LocalDate?): Long? = valor?.toEpochDay()
    @TypeConverter fun diaAFecha(valor: Long?): LocalDate? = valor?.let(LocalDate::ofEpochDay)

    @TypeConverter fun tipoMovimientoATexto(v: TipoMovimiento?): String? = v?.name
    @TypeConverter fun textoATipoMovimiento(v: String?): TipoMovimiento? = v?.let(TipoMovimiento::valueOf)

    @TypeConverter fun tipoCuentaATexto(v: TipoCuenta?): String? = v?.name
    @TypeConverter fun textoATipoCuenta(v: String?): TipoCuenta? = v?.let(TipoCuenta::valueOf)

    @TypeConverter fun medioATexto(v: Medio?): String? = v?.name
    @TypeConverter fun textoAMedio(v: String?): Medio? = v?.let(Medio::valueOf)

    @TypeConverter fun contraparteATexto(v: Contraparte?): String? = v?.name
    @TypeConverter fun textoAContraparte(v: String?): Contraparte? = v?.let(Contraparte::valueOf)

    @TypeConverter fun tipoCategoriaATexto(v: TipoCategoria?): String? = v?.name
    @TypeConverter fun textoATipoCategoria(v: String?): TipoCategoria? = v?.let(TipoCategoria::valueOf)


    @TypeConverter fun periodicidadATexto(v: Periodicidad?): String? = v?.name
    @TypeConverter fun textoAPeriodicidad(v: String?): Periodicidad? = v?.let(Periodicidad::valueOf)
}
