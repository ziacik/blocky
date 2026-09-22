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
	primary = Color(0xFF7A233B),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFF6E8EC),
	onPrimaryContainer = Color(0xFF321019),
	background = Color(0xFFF7F7F5),
	onBackground = Color(0xFF151515),
	surface = Color(0xFFFFFFFF),
	onSurface = Color(0xFF151515),
	surfaceVariant = Color(0xFFF0F0ED),
	onSurfaceVariant = Color(0xFF666662),
	outline = Color(0xFFDADAD5),
	outlineVariant = Color(0xFFE8E8E3),
	error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFFF0B6C6),
	onPrimary = Color(0xFF461523),
	primaryContainer = Color(0xFF5F1D30),
	onPrimaryContainer = Color(0xFFFFD9E2),
	background = Color(0xFF111110),
	onBackground = Color(0xFFEAEAE7),
	surface = Color(0xFF191918),
	onSurface = Color(0xFFEAEAE7),
	surfaceVariant = Color(0xFF242422),
	onSurfaceVariant = Color(0xFFBDBDB7),
	outline = Color(0xFF3C3C39),
	outlineVariant = Color(0xFF2E2E2C),
	error = Color(0xFFFFB4AB),
)

private val BlockyTypography = Typography(
	headlineLarge = TextStyle(
		fontSize = 32.sp,
		lineHeight = 38.sp,
		fontWeight = FontWeight.Bold,
	),
	headlineMedium = TextStyle(
		fontSize = 24.sp,
		lineHeight = 30.sp,
		fontWeight = FontWeight.SemiBold,
	),
	titleLarge = TextStyle(
		fontSize = 19.sp,
		lineHeight = 24.sp,
		fontWeight = FontWeight.SemiBold,
	),
	titleMedium = TextStyle(
		fontSize = 16.sp,
		lineHeight = 21.sp,
		fontWeight = FontWeight.SemiBold,
	),
	bodyLarge = TextStyle(
		fontSize = 16.sp,
		lineHeight = 23.sp,
	),
	bodyMedium = TextStyle(
		fontSize = 14.sp,
		lineHeight = 20.sp,
	),
	bodySmall = TextStyle(
		fontSize = 12.sp,
		lineHeight = 17.sp,
	),
	labelLarge = TextStyle(
		fontSize = 14.sp,
		lineHeight = 18.sp,
		fontWeight = FontWeight.SemiBold,
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
