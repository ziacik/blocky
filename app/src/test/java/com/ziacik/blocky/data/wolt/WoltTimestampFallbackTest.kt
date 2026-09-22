package com.ziacik.blocky.data.wolt

import com.ziacik.blocky.normalization.ItemNormalizer
import com.ziacik.blocky.normalization.NormalizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WoltTimestampFallbackTest {
	private val parser = WoltOrderParser(
		normalizer = object : ItemNormalizer {
			override fun normalize(rawName: String) = NormalizedItem(rawName, "Test")
		},
	)

	@Test
	fun historyFallsThroughEmptyReceivedAtToPaymentTimestamp() {
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

		val order = parser.latestHistoryOrder(json)

		assertNotNull(order)
		assertEquals(1771142803530L, order!!.issuedAt)
	}

	@Test
	fun historyFallsThroughMalformedReceivedAtToPaymentTimestampSeconds() {
		val json = """
			{
				"orders": [{
					"purchase_id": "p1",
					"received_at": "not-a-date",
					"payment_time_ts": 1700100000
				}]
			}
		""".trimIndent()

		assertEquals(
			1700100000L * 1000L,
			parser.latestHistoryOrder(json)!!.issuedAt,
		)
	}

	@Test
	fun detailFallsThroughEmptyCreationTimeToDeliveryTime() {
		val json = """
			{
				"purchase_id": "p1",
				"creation_time": "",
				"delivery_time": "2026-09-18T17:03:00Z",
				"total_price": 100,
				"items": []
			}
		""".trimIndent()

		assertNotNull(parser.parseOrderDetail(json))
	}
}
