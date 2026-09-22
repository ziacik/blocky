package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizationJsonTest {
	@Test
	fun requestContainsMerchantAndEveryReceiptItem() {
		val request = CategorizationRequest(
			merchant = "Lidl Slovenská republika",
			items = listOf(
				CategorizationRequestItem(0, "ROHLIK BIELY 50G", 49, 1.0),
				CategorizationRequestItem(1, "MILKA OREO", 199, 1.0),
			),
		)

		val json = CategorizationJson.encodeRequest(request)

		assertTrue(json.contains("Lidl Slovenská republika"))
		assertTrue(json.contains("ROHLIK BIELY 50G"))
		assertTrue(json.contains("MILKA OREO"))
	}

	@Test
	fun responseDecodesSubcategoryAndSpendingType() {
		val result = CategorizationJson.decodeResponse(
			"""
			{
				"items": [{
					"index": 0,
					"canonicalName": "Biely rožok",
					"category": "Potraviny",
					"subcategory": "Pečivo",
					"spendingType": "ESSENTIAL",
					"confidence": 0.96
				}]
			}
			""".trimIndent(),
		)

		assertEquals("Pečivo", result.single().subcategory)
		assertEquals(SpendingType.ESSENTIAL, result.single().spendingType)
	}

	@Test
	fun responseRejectsCategoryOutsideTaxonomy() {
		assertThrows(IllegalArgumentException::class.java) {
			CategorizationJson.decodeResponse(
				"""
				{
					"items": [{
						"index": 0,
						"canonicalName": "X",
						"category": "Vymyslená",
						"subcategory": "Čokoľvek",
						"spendingType": "REGULAR",
						"confidence": 0.5
					}]
				}
				""".trimIndent(),
			)
		}
	}
}
