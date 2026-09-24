package com.ziacik.blocky.data.wolt

import com.ziacik.blocky.normalization.ItemNormalizer
import com.ziacik.blocky.normalization.NormalizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class WoltIntegrationTest {
	private val normalizer = object : ItemNormalizer {
		override fun normalize(rawName: String) = NormalizedItem(
			canonicalName = rawName,
			category = "Test",
		)
	}

	@Test
	fun parsesWoltOrderIntoReceipt() {
		val json = """
			{
				"sections": [{
					"items": [{
						"id": "order-1",
						"timestamp": "18/09/2026, 19:03",
						"venue": {"name": "Wolt Market Petržalka"},
						"telemetry": {"end_amount": 2317},
						"items": [
							{"name": "Zemiaky žlté, 1 kg", "count": 1, "price": "€2.49"},
							{"name": "Lay's Maxx", "count": 2, "price": "€2.39"}
						]
					}]
				}]
			}
		""".trimIndent()

		val since = ZonedDateTime.of(
			2026, 9, 1, 0, 0, 0, 0,
			ZoneId.of("Europe/Bratislava"),
		).toInstant().toEpochMilli()

		val receipts = WoltOrderParser(normalizer).parseOrders(json, since)

		assertEquals(1, receipts.size)
		val receipt = receipts.single()
		assertEquals("wolt:order-1", receipt.id)
		assertEquals("Wolt Market Petržalka", receipt.merchant)
		assertEquals(2317L, receipt.totalCents)
		assertEquals(2, receipt.items.size)
		assertEquals(478L, receipt.items[1].totalCents)
	}

	@Test
	fun ignoresOrdersOlderThanRequestedWindow() {
		val json = """
			[
				{
					"id": "august",
					"timestamp": "31/08/2026, 23:59",
					"venue": {"name": "Old"},
					"telemetry": {"end_amount": 100},
					"items": []
				},
				{
					"id": "september",
					"timestamp": "01/09/2026, 00:01",
					"venue": {"name": "New"},
					"telemetry": {"end_amount": 200},
					"items": []
				}
			]
		""".trimIndent()
		val since = ZonedDateTime.of(
			2026, 9, 1, 0, 0, 0, 0,
			ZoneId.of("Europe/Bratislava"),
		).toInstant().toEpochMilli()

		val receipts = WoltOrderParser(normalizer).parseOrders(json, since)

		assertEquals(listOf("wolt:september"), receipts.map { it.id })
	}

	@Test
	fun firstSyncStartsAtBeginningOfCurrentMonth() {
		val now = Instant.parse("2026-09-21T19:15:00Z").toEpochMilli()

		val since = WoltSyncWindow.since(
			lastSuccessfulSyncMillis = null,
			nowMillis = now,
			zoneId = ZoneId.of("Europe/Bratislava"),
		)

		assertEquals(
			ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneId.of("Europe/Bratislava"))
				.toInstant()
				.toEpochMilli(),
			since,
		)
	}

	@Test
	fun laterSyncOverlapsPreviousSyncByOneDay() {
		val lastSync = Instant.parse("2026-09-20T18:00:00Z").toEpochMilli()
		val now = Instant.parse("2026-09-21T19:15:00Z").toEpochMilli()

		val since = WoltSyncWindow.since(
			lastSuccessfulSyncMillis = lastSync,
			nowMillis = now,
			zoneId = ZoneId.of("Europe/Bratislava"),
		)

		assertEquals(lastSync - 24L * 60 * 60 * 1000, since)
	}

	@Test
	fun capsOrderHistoryLimitAtWoltMaximum() {
		assertEquals(100, normalizeWoltOrderHistoryLimit(200))
		assertEquals(100, normalizeWoltOrderHistoryLimit(100))
		assertEquals(10, normalizeWoltOrderHistoryLimit(10))
	}
	@Test
	fun mergesRotatedWoltCookiesWithoutDroppingOthers() {
		val merged = WoltCookieJar.mergeSetCookieHeaders(
			"a=1; __wtoken=old; __wrtoken=refresh; z=9",
			listOf(
				"__wtoken=new; Path=/; Secure; HttpOnly",
				"other=42; Path=/",
			),
		)

		assertTrue(merged.contains("a=1"))
		assertTrue(merged.contains("__wtoken=new"))
		assertTrue(merged.contains("__wrtoken=refresh"))
		assertTrue(merged.contains("other=42"))
		assertTrue(merged.contains("z=9"))
	}
}
