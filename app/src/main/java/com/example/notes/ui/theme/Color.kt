package com.example.notes.ui.theme

import androidx.compose.ui.graphics.Color

// --- Brand: 极简现代, 以淡紫色为主, 中性灰白辅 ---
val Primary = Color(0xFF6750A4)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFEADDFF)
val OnPrimaryContainer = Color(0xFF21005D)

val Secondary = Color(0xFF625B71)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFE8DEF8)
val OnSecondaryContainer = Color(0xFF1D192B)

val Tertiary = Color(0xFF7D5260)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFD8E4)
val OnTertiaryContainer = Color(0xFF31111D)

val Background = Color(0xFFFFFBFE)
val OnBackground = Color(0xFF1C1B1F)
val Surface = Color(0xFFFFFBFE)
val OnSurface = Color(0xFF1C1B1F)
val SurfaceVariant = Color(0xFFE7E0EC)
val OnSurfaceVariant = Color(0xFF49454F)
val Outline = Color(0xFF79747E)

// Dark palette
val PrimaryDark = Color(0xFFD0BCFF)
val OnPrimaryDark = Color(0xFF381E72)
val PrimaryContainerDark = Color(0xFF4F378B)
val OnPrimaryContainerDark = Color(0xFFEADDFF)

val SecondaryDark = Color(0xFFCCC2DC)
val OnSecondaryDark = Color(0xFF332D41)
val SecondaryContainerDark = Color(0xFF4A4458)
val OnSecondaryContainerDark = Color(0xFFE8DEF8)

val BackgroundDark = Color(0xFF1C1B1F)
val OnBackgroundDark = Color(0xFFE6E1E5)
val SurfaceDark = Color(0xFF1C1B1F)
val OnSurfaceDark = Color(0xFFE6E1E5)
val SurfaceVariantDark = Color(0xFF49454F)
val OnSurfaceVariantDark = Color(0xFFCAC4D0)
val OutlineDark = Color(0xFF938F99)

// Predefined note card swatches
// P112-FIX: 显式 `listOf<Color>`, 让 Kotlin 1.8.22 不会推断成 List<Any>。
// 否则 .first() 返回 Any?, 赋值给 NoteSnapshot.color: Color 字段时
// 会报 "Type mismatch: inferred type is Any but Color was expected"。
val NoteSwatches: List<Color> = listOf(
    Color(0xFFFFFFFF), // White
    Color(0xFFFFE7A0), // Sun
    Color(0xFFFFD1DC), // Pink
    Color(0xFFCFE8FF), // Sky
    Color(0xFFD7F5D4), // Mint
    Color(0xFFEEE0FF)  // Lavender
)
