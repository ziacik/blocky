package com.ziacik.blocky.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationTest {
	@Test
	fun clickingReceiptRequestsItsDetail() {
		assertEquals(
			MainScreen.ReceiptDetail("wolt:123"),
			MainNavigation.reduce(MainScreen.Home, MainIntent.OpenReceipt("wolt:123")),
		)
	}

	@Test
	fun clickingAllItemsOpensGlobalItemList() {
		assertEquals(
			MainScreen.AllItems,
			MainNavigation.reduce(MainScreen.Home, MainIntent.OpenAllItems),
		)
	}

	@Test
	fun backReturnsHome() {
		assertEquals(
			MainScreen.Home,
			MainNavigation.reduce(MainScreen.ReceiptDetail("wolt:123"), MainIntent.Back),
		)
	}

	@Test
	fun diagnosticsMenuOpensDiagnosticsOverlay() {
		assertEquals(
			HomeOverlay.Diagnostics,
			HomeOverlayNavigation.reduce(HomeOverlay.None, HomeOverlayIntent.OpenDiagnostics),
		)
	}

	@Test
	fun diagnosticsOverlayCanBeClosed() {
		assertEquals(
			HomeOverlay.None,
			HomeOverlayNavigation.reduce(HomeOverlay.Diagnostics, HomeOverlayIntent.Close),
		)
	}
}
