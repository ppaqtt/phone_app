# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable
-dontnote kotlinx.serialization.AnnotationsKt

# ========================================================================
# Compose 核心 — R8 必须保留 @Composable / Saver / mutableStateOf
# ========================================================================
-keep class androidx.compose.runtime.Composable { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}

# P115-FIX: Saver / rememberSaveable / mutableStateOf 不能被 R8 内联或删除,
# 否则 release 包运行时找不到 Saver 实现, 报 "cannot be saved" 闪退.
-keep class androidx.compose.runtime.Saver { *; }
-keep class androidx.compose.runtime.SaverKt { *; }
-keep class androidx.compose.runtime.saveable.Saver { *; }
-keep class androidx.compose.runtime.saveable.SaverKt { *; }
-keep class androidx.compose.runtime.saveable.MapSaverKt { *; }
-keep class androidx.compose.runtime.saveable.ListSaverKt { *; }
-keepclassmembers class * {
    androidx.compose.runtime.Saver *Saver*;
}
-keepclassmembers class * {
    androidx.compose.runtime.saveable.Saver *Saver*;
}

# mutableStateOf / mutableStateListOf / rememberSaveable 不能被混淆
-keep class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.SnapshotStateList { *; }
-keep class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keep class androidx.compose.runtime.saveable.RememberSaveableKt { *; }

# Navigation Compose: 保留所有 Composable Screen 函数
-keepclassmembers class com.example.notes.ui.screens.* {
    @androidx.compose.runtime.Composable <methods>;
}

# ========================================================================
# Compose UI — Color / TextStyle / Modifier 等内部类
# ========================================================================
-keep class androidx.compose.ui.graphics.Color { *; }
-keep class androidx.compose.ui.graphics.ColorKt { *; }
-keep class androidx.compose.ui.text.TextRange { *; }
-keep class androidx.compose.ui.text.input.TextFieldValue { *; }
-keep class androidx.compose.ui.text.input.TextFieldValue$Companion { *; }

# ========================================================================
# Room
# ========================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * {
    <init>(...);
}
-dontwarn androidx.room.paging.**

# Kotlin reflection (used by Room KSP-generated code)
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ========================================================================
# Navigation Compose — 保留路由参数类和 NavType 解析
# ========================================================================
-keep class com.example.notes.ui.navigation.** { *; }
-keepclassmembers class * {
    @androidx.navigation.NavType <fields>;
}

# ========================================================================
# DataStore / Preferences — 序列化器不能被混淆
# ========================================================================
-keep class com.example.notes.data.** { *; }
-keep class com.example.notes.util.ThemePref { *; }
-keep class com.example.notes.util.ThemePref$$serializer { *; }
-keep class com.example.notes.util.FontScale { *; }
-keep class com.example.notes.util.DarkMode { *; }
-keep class com.example.notes.util.ColorTheme { *; }

# ========================================================================
# F1: 备份 DTO 由 JSON (kotlinx.serialization) 反序列化
# ========================================================================
-keep class com.example.notes.util.BackupPayload { *; }
-keep class com.example.notes.util.CategoryBackup { *; }
-keep class com.example.notes.util.NoteBackup { *; }
-keep class com.example.notes.util.ImageBackup { *; }
-keepclassmembers class com.example.notes.util.**Backup$Companion { *; }

# kotlinx.serialization 通用规则
-keepattributes Signature, *Annotation*
-keep,includedescriptorclasses class com.example.notes.util.**$$serializer { *; }
-keepclassmembers class com.example.notes.util.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.notes.util.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========================================================================
# ML Kit 中文识别反射加载内部模块
# ========================================================================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ========================================================================
# Coil / AsyncImage — 保留图片加载相关类
# ========================================================================
-keep class coil.** { *; }
-dontwarn coil.**

# ========================================================================
# WorkManager — 保留 Worker 类
# ========================================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# ========================================================================
# App Widget — 保留 RemoteViews 相关类
# ========================================================================
-keep class com.example.notes.widget.** { *; }

# ========================================================================
# Retrofit / Gson — 保留 API 接口和 DTO
# ========================================================================
-keep class com.example.notes.api.** { *; }
-keep class com.example.notes.network.** { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class com.google.gson.** { *; }

# ========================================================================
# Timber — 保留日志行号信息
# ========================================================================
-keepattributes SourceFile,LineNumberTable
-keep class timber.log.Timber { *; }
