-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room genera implementaciones por reflexion en tiempo de compilacion; basta con
# conservar las entidades para que los nombres de columna no se ofusquen.
-keep class com.carlosalbertoxw.ollin.finanzas.data.db.** { *; }
-keep class com.carlosalbertoxw.ollin.finanzas.domain.model.** { *; }

# El lector/escritor XLSX usa XmlPullParser de la plataforma.
-dontwarn org.xmlpull.v1.**

# Estos enums no se guardan como numero sino como su nombre, y se releen con
# valueOf(). Si R8 los renombrara, lo guardado dejaria de reconocerse: la
# eleccion de hojas se perderia, y el candado tendria que deducirse de lo que
# queda (ver AjustesRepositorio.leeModoBloqueo) en vez de leer el que se puso.
-keepclassmembers enum com.carlosalbertoxw.ollin.finanzas.data.excel.EsquemaExportacion { *; }
-keepclassmembers enum com.carlosalbertoxw.ollin.finanzas.data.excel.HojaExportable { *; }
-keepclassmembers enum com.carlosalbertoxw.ollin.finanzas.data.prefs.ModoBloqueo { *; }
