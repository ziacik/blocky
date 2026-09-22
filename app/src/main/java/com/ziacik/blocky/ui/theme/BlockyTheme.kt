package com.ziacik.blocky.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
	primary = Color(0xFFFF5533),
	onPrimary = Color(0xFFFFFFFF),
	background = Color(0xFFF3F0EA),
	onBackground = Color(0xFF171513),
	surface = Color(0xFFF3F0EA),
	onSurface = Color(0xFF171513),
	surfaceVariant = Color(0xFFE6E0D7),
	onSurfaceVariant = Color(0xFF6D675F),
	outline = Color(0xFFC9C1B7),
	outlineVariant = Color(0xFFDDD6CC),
	error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFFFF6B4A),
	onPrimary = Color(0xFF1B100C),
	background = Color(0xFF141311),
	onBackground = Color(0xFFF1EDE7),
	surface = Color(0xFF141311),
	onSurface = Color(0xFFF1EDE7),
	surfaceVariant = Color(0xFF292621),
	onSurfaceVariant = Color(0xFFB8B0A6),
	outline = Color(0xFF4A453F),
	outlineVariant = Color(0xFF36322D),
	error = Color(0xFFFFB4AB),
)

private val BlockyTypography = Typography(
	headlineLarge = TextStyle(
		fontSize = 42.sp,
		lineHeight = 44.sp,
		fontWeight = FontWeight.Black,
		letterSpacing = (-1.4).sp,
	),
	headlineMedium = TextStyle(
		fontSize = 28.sp,
		lineHeight = 31.sp,
		fontWeight = FontWeight.Bold,
		letterSpacing = (-0.7).sp,
	),
	titleLarge = TextStyle(
		fontSize = 21.sp,
		lineHeight = 25.sp,
		fontWeight = FontWeight.Bold,
		letterSpacing = (-0.3).sp,
	),
	titleMedium = TextStyle(
		fontSize = 16.sp,
		lineHeight = 20.sp,
		fontWeight = FontWeight.Bold,
	),
	bodyLarge = TextStyle(
		fontSize = 16.sp,
		lineHeight = 22.sp,
	),
	bodyMedium = TextStyle(
		fontSize = 14.sp,
		lineHeight = 19.sp,
	),
	bodySmall = TextStyle(
		fontSize = 12.sp,
		lineHeight = 16.sp,
	),
	labelLarge = TextStyle(
		fontSize = 13.sp,
		lineHeight = 16.sp,
		fontWeight = FontWeight.Bold,
		letterSpacing = 0.8.sp,
	),
	labelMedium = TextStyle(
		fontSize = 11.sp,
		lineHeight = 14.sp,
		fontWeight = FontWeight.Bold,
		letterSpacing = 1.2.sp,
	),
)

@Composable
fun BlockyTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit,
) {
	MaterialTheme(
		colorScheme = if (darkTheme) DarkColors else LightColors,
		typography = BlockyTypography,
		content = content,
	)
}
