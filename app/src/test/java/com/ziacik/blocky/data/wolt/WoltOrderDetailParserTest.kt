package com.ziacik.blocky.data.wolt

import com.ziacik.blocky.normalization.ItemNormalizer
import com.ziacik.blocky.normalization.NormalizedItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WoltOrderDetailParserTest {
	private val parser = WoltOrderParser(
		normalizer = object : ItemNormalizer {
			override fun normalize(rawName: String) = NormalizedItem(rawName, "Test")
		},
	)

	@Test
	fun historyReturnsOnlyPurchasesInsideSyncWindow() {
		val json = """
			{
				"orders": [
					{
						"purchase_id": "old",
						"received_at": "2026-08-31T20:00:00Z",
						"venue_name": "Old"
					},
					{
						"purchase_id": "current",
						"received_at": "2026-09-18T17:03:00Z",
						"venue_name": "Wolt Market Petržalka"
					}
				]
			}
		""".trimIndent()
		val since = ZonedDateTime.of(
			2026, 9, 1, 0, 0, 0, 0,
			ZoneId.of("Europe/Bratislava"),
		).toInstant().toEpochMilli()

		assertEquals(
			listOf("current"),
			parser.parseHistoryPurchaseIds(json, since),
		)
	}

	@Test
	fun detailUsesEndAmountAsLineTotalInMinorUnits() {
		val json = """
			{
				"purchase_id": "purchase-1",
				"creation_time": "2026-09-18T17:03:00Z",
				"venue_name": "Wolt Market Petržalka",
				"currency": "EUR",
				"total_price": 2530,
				"items": [
					{
						"id": "potatoes",
						"name": "Zemiaky žlté, 1 kg",
						"count": 1,
						"price": 249,
						"end_amount": 249
					},
					{
						"id": "chips",
						"name": "Lay's Maxx",
						"count": 2,
						"price": 239,
						"end_amount": 478
					}
				]
			}
		""".trimIndent()

		val receipt = parser.parseOrderDetail(json)!!

		assertEquals("wolt:purchase-1", receipt.id)
		assertEquals(2530L, receipt.totalCents)
		assertEquals(249L, receipt.items[0].totalCents)
		assertEquals(478L, receipt.items[1].totalCents)
	}

	@Test
	fun detailFallsBackToUnitPriceTimesQuantity() {
		val json = """
			{
				"purchase_id": "purchase-2",
				"creation_time": "2026-09-18T17:03:00Z",
				"venue_name": "KFC",
				"total_price": 1200,
				"items": [
					{"name": "Burger", "count": 2, "price": 600}
				]
			}
		""".trimIndent()

		assertEquals(1200L, parser.parseOrderDetail(json)!!.items.single().totalCents)
	}
}
