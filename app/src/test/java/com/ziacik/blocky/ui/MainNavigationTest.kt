package com.ziacik.blocky.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationTest {
	@Test
	fun openingReceiptRemembersCurrentPrimaryScreen() {
		assertEquals(
			MainScreen.ReceiptDetail("wolt:123", MainScreen.Receipts),
			MainNavigation.reduce(MainScreen.Receipts, MainIntent.OpenReceipt("wolt:123")),
		)
	}

	@Test
	fun backFromReceiptReturnsToPreviousPrimaryScreen() {
		assertEquals(
			MainScreen.Receipts,
			MainNavigation.reduce(
				MainScreen.ReceiptDetail("wolt:123", MainScreen.Receipts),
				MainIntent.Back,
			),
		)
	}

	@Test
	fun selectingOverviewOpensOverview() {
		assertEquals(
			MainScreen.Overview,
			MainNavigation.reduce(MainScreen.Settings, MainIntent.OpenOverview),
		)
	}

	@Test
	fun selectingReceiptsOpensReceipts() {
		assertEquals(
			MainScreen.Receipts,
			MainNavigation.reduce(MainScreen.Overview, MainIntent.OpenReceipts),
		)
	}

	@Test
	fun selectingItemsOpensItems() {
		assertEquals(
			MainScreen.AllItems,
			MainNavigation.reduce(MainScreen.Overview, MainIntent.OpenAllItems),
		)
	}

	@Test
	fun selectingSettingsOpensSettings() {
		assertEquals(
			MainScreen.Settings,
			MainNavigation.reduce(MainScreen.Overview, MainIntent.OpenSettings),
		)
	}

	@Test
	fun backFromSettingsReturnsOverview() {
		assertEquals(
			MainScreen.Overview,
			MainNavigation.reduce(MainScreen.Settings, MainIntent.Back),
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
