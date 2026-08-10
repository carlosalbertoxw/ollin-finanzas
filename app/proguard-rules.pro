-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room genera implementaciones por reflexion en tiempo de compilacion; basta con
# conservar las entidades para que los nombres de columna no se ofusquen.
-keep class mx.ollin.finanzas.data.db.** { *; }
-keep class mx.ollin.finanzas.domain.model.** { *; }

# El lector/escritor XLSX usa XmlPullParser de la plataforma.
-dontwarn org.xmlpull.v1.**
