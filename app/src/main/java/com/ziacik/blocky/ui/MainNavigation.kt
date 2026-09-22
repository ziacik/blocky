package com.ziacik.blocky.ui

sealed interface MainScreen {
	data object Overview : MainScreen
	data object Receipts : MainScreen
	data object AllItems : MainScreen
	data object Settings : MainScreen
	data class ReceiptDetail(
		val receiptId: String,
		val returnTo: MainScreen,
	) : MainScreen
}

sealed interface MainIntent {
	data class OpenReceipt(val receiptId: String) : MainIntent
	data object OpenOverview : MainIntent
	data object OpenReceipts : MainIntent
	data object OpenAllItems : MainIntent
	data object OpenSettings : MainIntent
	data object Back : MainIntent
}

object MainNavigation {
	fun reduce(current: MainScreen, intent: MainIntent): MainScreen = when (intent) {
		is MainIntent.OpenReceipt -> MainScreen.ReceiptDetail(
			receiptId = intent.receiptId,
			returnTo = when (current) {
				is MainScreen.ReceiptDetail -> current.returnTo
				else -> current
			},
		)

		MainIntent.OpenOverview -> MainScreen.Overview
		MainIntent.OpenReceipts -> MainScreen.Receipts
		MainIntent.OpenAllItems -> MainScreen.AllItems
		MainIntent.OpenSettings -> MainScreen.Settings
		MainIntent.Back -> when (current) {
			is MainScreen.ReceiptDetail -> current.returnTo
			MainScreen.Settings -> MainScreen.Overview
			else -> current
		}
	}
}

enum class HomeOverlay {
	None,
	Diagnostics,
}

sealed interface HomeOverlayIntent {
	data object OpenDiagnostics : HomeOverlayIntent
	data object Close : HomeOverlayIntent
}

object HomeOverlayNavigation {
	fun reduce(current: HomeOverlay, intent: HomeOverlayIntent): HomeOverlay = when (intent) {
		HomeOverlayIntent.OpenDiagnostics -> HomeOverlay.Diagnostics
		HomeOverlayIntent.Close -> HomeOverlay.None
	}
}
