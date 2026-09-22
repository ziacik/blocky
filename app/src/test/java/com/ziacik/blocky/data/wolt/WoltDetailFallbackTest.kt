package com.ziacik.blocky.data.wolt

import com.ziacik.blocky.normalization.ItemNormalizer
import com.ziacik.blocky.normalization.NormalizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class WoltDetailFallbackTest {
	private val parser = WoltOrderParser(
		normalizer = object : ItemNormalizer {
			override fun normalize(rawName: String) = NormalizedItem(rawName, "Test")
		},
	)

	@Test
	fun parsesDetailWithoutIdOrCreationTimeUsingHistoryFallback() {
		val historyJson = """
			{
				"orders": [{
					"purchase_id": "6aad660ae81012dc076e5a43",
					"received_at": "2026-09-18T17:03:00Z",
					"venue_name": "Wolt Market Petržalka"
				}]
			}
		""".trimIndent()
		val detailJson = """
			{
				"venue_name": "Wolt Market Petržalka",
				"total_price": 2530,
				"items": [{
					"name": "Chlieb a láska Vianočka pletená, 500 g",
					"count": 1,
					"price": 480,
					"end_amount": 480
				}]
			}
		""".trimIndent()

		val history = parser.latestHistoryOrder(historyJson)
		assertNotNull(history)

		val receipt = parser.parseOrderDetail(
			json = detailJson,
			fallbackPurchaseId = history!!.purchaseId,
			fallbackIssuedAt = history.issuedAt,
			fallbackMerchant = history.merchant,
		)

		assertNotNull(receipt)
		assertEquals("wolt:6aad660ae81012dc076e5a43", receipt!!.id)
		assertEquals(Instant.parse("2026-09-18T17:03:00Z").toEpochMilli(), receipt.issuedAt)
		assertEquals("Wolt Market Petržalka", receipt.merchant)
		assertEquals(480L, receipt.items.single().totalCents)
	}

	@Test
	fun latestHistoryOrderCarriesFallbackMetadata() {
		val json = """
			{
				"orders": [
					{
						"purchase_id": "old",
						"received_at": "2026-09-17T17:03:00Z",
						"venue_name": "Old"
					},
					{
						"purchase_id": "new",
						"received_at": "2026-09-18T17:03:00Z",
						"venue_name": "New"
					}
				]
			}
		""".trimIndent()

		val order = parser.latestHistoryOrder(json)!!

		assertEquals("new", order.purchaseId)
		assertEquals("New", order.merchant)
		assertEquals(Instant.parse("2026-09-18T17:03:00Z").toEpochMilli(), order.issuedAt)
	}
}
