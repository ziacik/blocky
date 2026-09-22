package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpCategorizationClientTest {
	@Test
	fun postsOneBatchAndDecodesResponse() {
		val bodies = mutableListOf<String>()
		val transport = object : CategorizationTransport {
			override fun post(body: String): String {
				bodies += body
				return """
				{
					"items": [{
						"index": 0,
						"canonicalName": "Biely rožok",
						"category": "Potraviny",
						"subcategory": "Pečivo",
						"spendingType": "ESSENTIAL",
						"confidence": 0.98
					}]
				}
				""".trimIndent()
			}
		}
		val request = CategorizationRequest(
			merchant = "Lidl",
			items = listOf(CategorizationRequestItem(0, "ROHLIK BIELY 50G", 49, 1.0)),
		)

		val result = HttpCategorizationClient(transport).categorize(request)

		assertEquals(1, bodies.size)
		assertTrue(bodies.single().contains("Lidl"))
		assertTrue(bodies.single().contains("ROHLIK BIELY 50G"))
		assertEquals("Pečivo", result.single().subcategory)
		assertEquals(SpendingType.ESSENTIAL, result.single().spendingType)
	}
}
