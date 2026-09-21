package com.ziacik.blocky.ui

sealed interface MainScreen {
	data object Home : MainScreen
	data object AllItems : MainScreen
	data class ReceiptDetail(val receiptId: String) : MainScreen
}

sealed interface MainIntent {
	data class OpenReceipt(val receiptId: String) : MainIntent
	data object OpenAllItems : MainIntent
	data object Back : MainIntent
}

object MainNavigation {
	fun reduce(current: MainScreen, intent: MainIntent): MainScreen = when (intent) {
		is MainIntent.OpenReceipt -> MainScreen.ReceiptDetail(intent.receiptId)
		MainIntent.OpenAllItems -> MainScreen.AllItems
		MainIntent.Back -> MainScreen.Home
	}
}
