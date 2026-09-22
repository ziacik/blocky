package com.ziacik.blocky.data.wolt

import com.ziacik.blocky.normalization.ItemNormalizer
import com.ziacik.blocky.normalization.NormalizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WoltSingleOrderTest {
	private val parser = WoltOrderParser(
		normalizer = object : ItemNormalizer {
			override fun normalize(rawName: String) = NormalizedItem(rawName, "Test")
		},
	)

	@Test
	fun selectsNewestPurchaseFromHistory() {
		val json = """
			{
				"orders": [
					{
						"purchase_id": "older",
						"received_at": "2026-09-18T17:03:00Z"
					},
					{
						"purchase_id": "newest",
						"received_at": "2026-09-21T19:30:00Z"
					}
				]
			}
		""".trimIndent()

		assertEquals("newest", parser.latestPurchaseId(json))
	}

	@Test
	fun fallsBackToFirstPurchaseWhenHistoryHasNoTimestamps() {
		val json = """
			{
				"orders": [
					{"purchase_id": "first"},
					{"purchase_id": "second"}
				]
			}
		""".trimIndent()

		assertEquals("first", parser.latestPurchaseId(json))
	}

	@Test
	fun returnsNullForEmptyHistory() {
		assertNull(parser.latestPurchaseId("""{"orders": []}"""))
	}
}
