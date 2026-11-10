# kotlinx.serialization: conservar serializadores generados.
-keepclassmembers class **$$serializer { *** INSTANCE; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
# BouncyCastle API ligera: sin reflexión, no necesita reglas adicionales.
