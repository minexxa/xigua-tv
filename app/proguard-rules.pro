# ===== 崩溃堆栈可读 =====
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# ===== Kotlinx Serialization（Feed JSON 动态解析）=====
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留 @Serializable 数据类的 Companion（本项目主要用 JsonElement 动态解析，此项兜底）
-if class * {
    @kotlinx.serialization.Serializable *;
}
-keep class <1> {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== Room 生成的数据库实现 =====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# ===== Media3 / Compose / OkHttp 由库自带的 consumer-rules 处理 =====
-dontwarn androidx.media3.**
-dontwarn okhttp3.internal.**
