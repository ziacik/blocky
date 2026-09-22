package com.ziacik.blocky.data.wolt

import com.ziacik.blocky.normalization.ItemNormalizer
import com.ziacik.blocky.normalization.NormalizedItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WoltCurrentMonthTest {
	private val parser = WoltOrderParser(
		normalizer = object : ItemNormalizer {
			override fun normalize(rawName: String) = NormalizedItem(rawName, "Test")
		},
	)

	@Test
	fun historyOrdersSinceKeepsMetadataForCurrentMonth() {
		val json = """
			{
				"orders": [
					{
						"purchase_id": "aug",
						"received_at": "2026-08-31T20:00:00Z",
						"venue_name": "Old"
					},
					{
						"purchase_id": "sep",
						"payment_time_ts": 1789674000000,
						"venue_name": "Wolt Market Petržalka"
					}
				]
			}
		""".trimIndent()
		val since = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli()

		val orders = parser.historyOrdersSince(json, since)

		assertEquals(1, orders.size)
		assertEquals("sep", orders.single().purchaseId)
		assertEquals("Wolt Market Petržalka", orders.single().merchant)
		assertEquals(1789674000000L, orders.single().issuedAt)
	}
}
