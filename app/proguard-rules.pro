# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin reflection (used by Room KSP-generated code)
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# F1: 备份 DTO 由 JSON (kotlinx.serialization) 反序列化, R8 混淆后字段名
# 对不上就抛 MissingFieldException。保留全部字段 (含生成方法)。
-keep class com.example.notes.util.BackupPayload { *; }
-keep class com.example.notes.util.CategoryBackup { *; }
-keep class com.example.notes.util.NoteBackup { *; }
-keep class com.example.notes.util.ImageBackup { *; }
-keepclassmembers class com.example.notes.util.**Backup$Companion { *; }

# kotlinx.serialization 通用规则 (上面 keep 是兜底)
-keepattributes Signature, *Annotation*
-keep,includedescriptorclasses class com.example.notes.util.**$$serializer { *; }
-keepclassmembers class com.example.notes.util.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.notes.util.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit 中文识别反射加载内部模块
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room 生成代码引用 reflection
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Callback
