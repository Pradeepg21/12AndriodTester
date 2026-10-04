package com.satya.a12andriodtester.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satya.a12andriodtester.data.RequestCategory
import com.satya.a12andriodtester.data.RequestStatus

val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF9333EA)
val Pink = Color(0xFFEC4899)
val Teal = Color(0xFF0D9488)
val Amber = Color(0xFFF59E0B)
val Sky = Color(0xFF0EA5E9)
val Emerald = Color(0xFF10B981)
val Rose = Color(0xFFE11D48)
val Slate = Color(0xFF64748B)

val BrandGradient = Brush.linearGradient(listOf(Indigo, Violet, Pink))
val CoolGradient = Brush.linearGradient(listOf(Sky, Indigo))
val SuccessGradient = Brush.linearGradient(listOf(Emerald, Teal))

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = Teal,
    tertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFFF7F7FD),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0FA),
    surfaceContainer = Color(0xFFF1F2FB),
    surfaceContainerLow = Color(0xFFF7F7FD),
    surfaceContainerHigh = Color(0xFFE9EAF6),
    error = Rose,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFD8B4FE),
    onSecondary = Color(0xFF3B0764),
    secondaryContainer = Color(0xFF6B21A8),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = Color(0xFF5EEAD4),
    tertiaryContainer = Color(0xFF115E59),
    background = Color(0xFF0F0E1A),
    surface = Color(0xFF17162A),
    surfaceVariant = Color(0xFF26243F),
    surfaceContainer = Color(0xFF1C1B31),
    surfaceContainerLow = Color(0xFF151427),
    surfaceContainerHigh = Color(0xFF242240),
    error = Color(0xFFFB7185),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TesterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

fun statusColor(status: RequestStatus): Color = when (status) {
    RequestStatus.SUBMITTED, RequestStatus.PAYMENT_PENDING -> Amber
    RequestStatus.PAYMENT_SUBMITTED, RequestStatus.PAYMENT_VERIFIED -> Sky
    RequestStatus.INTERNAL_VERIFICATION, RequestStatus.EMAILS_SHARED, RequestStatus.EMAILS_ADDED -> Indigo
    RequestStatus.TESTING_IN_PROGRESS -> Violet
    RequestStatus.REPORT_READY -> Teal
    RequestStatus.COMPLETED, RequestStatus.REFUNDED -> Emerald
    RequestStatus.NEEDS_INFO, RequestStatus.ON_HOLD, RequestStatus.REFUND_REQUESTED -> Pink
    RequestStatus.CANCELLED -> Slate
    RequestStatus.REJECTED -> Rose
}

fun categoryColor(category: RequestCategory): Color = when (category) {
    RequestCategory.NEW -> Amber
    RequestCategory.IN_PROGRESS -> Violet
    RequestCategory.COMPLETED -> Emerald
    RequestCategory.CLOSED -> Slate
}
