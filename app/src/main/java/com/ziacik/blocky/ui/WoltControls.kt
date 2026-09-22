package com.ziacik.blocky.ui

enum class WoltAction {
	Connect,
	CurrentMonth,
}

object WoltControls {
	fun actions(
		connected: Boolean,
		busy: Boolean,
	): List<WoltAction> {
		if (busy) return emptyList()
		return if (connected) {
			listOf(WoltAction.CurrentMonth)
		} else {
			listOf(WoltAction.Connect)
		}
	}
}
