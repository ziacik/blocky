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
	fun openingSummaryOpensSummaryItems() {
		val filter = SummaryFilter.Category("Potraviny")

		assertEquals(
			MainScreen.SummaryItems(filter),
			MainNavigation.reduce(MainScreen.Overview, MainIntent.OpenSummary(filter)),
		)
	}

	@Test
	fun backFromSummaryReturnsOverview() {
		assertEquals(
			MainScreen.Overview,
			MainNavigation.reduce(
				MainScreen.SummaryItems(SummaryFilter.Category("Potraviny")),
				MainIntent.Back,
			),
		)
	}

	@Test
	fun receiptOpenedFromSummaryReturnsToSummary() {
		val summary = MainScreen.SummaryItems(SummaryFilter.Category("Potraviny"))

		assertEquals(
			MainScreen.ReceiptDetail("receipt-1", summary),
			MainNavigation.reduce(summary, MainIntent.OpenReceipt("receipt-1")),
		)
	}

	@Test
	fun openingBreakdownOpensSelectedDimension() {
		assertEquals(
			MainScreen.Breakdown(BreakdownDimension.Subcategory),
			MainNavigation.reduce(
				MainScreen.Overview,
				MainIntent.OpenBreakdown(BreakdownDimension.Subcategory),
			),
		)
	}

	@Test
	fun backFromBreakdownReturnsOverview() {
		assertEquals(
			MainScreen.Overview,
			MainNavigation.reduce(
				MainScreen.Breakdown(BreakdownDimension.Category),
				MainIntent.Back,
			),
		)
	}

	@Test
	fun summaryOpenedFromBreakdownReturnsToBreakdown() {
		val breakdown = MainScreen.Breakdown(BreakdownDimension.Category)
		val filter = SummaryFilter.Category("Potraviny")
		val summary = MainScreen.SummaryItems(filter, breakdown)

		assertEquals(
			summary,
			MainNavigation.reduce(breakdown, MainIntent.OpenSummary(filter)),
		)
		assertEquals(
			breakdown,
			MainNavigation.reduce(summary, MainIntent.Back),
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
