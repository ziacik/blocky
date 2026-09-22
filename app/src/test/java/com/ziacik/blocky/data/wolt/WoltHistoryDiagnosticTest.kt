package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertTrue
import org.junit.Test

class WoltHistoryDiagnosticTest {
	@Test
	fun historySummaryShowsRawTimeFieldsForSelectedPurchase() {
		val json = """
			{
				"orders": [{
					"purchase_id": "p1",
					"received_at": "",
					"payment_time_ts": 1771142803530,
					"venue_name": "Wolt Market Petržalka"
				}]
			}
		""".trimIndent()

		val lines = WoltDiagnosticFormatter.historySummary(json, "p1")

		assertTrue(lines.any { it.contains("received_at=") })
		assertTrue(lines.any { it.contains("payment_time_ts=1771142803530") })
	}
}
