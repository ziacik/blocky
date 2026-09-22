package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertTrue
import org.junit.Test

class WoltDiagnosticFormatterTest {
	@Test
	fun detailSummaryShowsRawPriceFieldsAndZeroCount() {
		val json = """
			{
				"purchase_id": "p1",
				"items": [
					{"name": "A", "count": 1, "price": 249, "end_amount": 249},
					{"name": "B", "count": 2, "price": 100, "end_amount": 0}
				]
			}
		""".trimIndent()

		val lines = WoltDiagnosticFormatter.detailSummary(json)

		assertTrue(lines.any { it.contains("items=2") })
		assertTrue(lines.any { it.contains("price=249") && it.contains("end_amount=249") })
		assertTrue(lines.any { it.contains("price=100") && it.contains("end_amount=0") })
	}
}
