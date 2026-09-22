package com.ziacik.blocky.ui

enum class WoltAction {
	Connect,
	Latest,
	CurrentMonth,
}

object WoltControls {
	fun actions(
		connected: Boolean,
		busy: Boolean,
	): List<WoltAction> {
		if (busy) return emptyList()
		return if (connected) {
			listOf(WoltAction.Latest, WoltAction.CurrentMonth)
		} else {
			listOf(WoltAction.Connect)
		}
	}
}
