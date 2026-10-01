package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCategorizationClientTest {
	@Test
	fun sendsApiKeyReceiptContextAndStructuredSchema() {
		var receivedApiKey: String? = null
		var receivedBody: String? = null
		val transport = object : OpenAiTransport {
			override fun post(apiKey: String, body: String): String {
				receivedApiKey = apiKey
				receivedBody = body
				return """
					{
					  "output_text": "{\"items\":[{\"index\":0,\"canonicalName\":\"Biely rožok\",\"category\":\"Potraviny\",\"subcategory\":\"Pečivo\",\"spendingType\":\"ESSENTIAL\",\"confidence\":0.98}]}"
					}
				""".trimIndent()
			}
		}
		val client = OpenAiCategorizationClient(
			apiKey = "test-key",
			transport = transport,
		)

		val result = client.categorize(
			CategorizationRequest(
				merchant = "dm drogerie markt",
				items = listOf(
					CategorizationRequestItem(
						index = 0,
						name = "ROHLÍK BIELY 50G",
						totalCents = 49,
						quantity = 1.0,
					),
				),
			),
		)

		assertEquals("test-key", receivedApiKey)
		val body = requireNotNull(receivedBody)
		assertTrue(body.contains("\"model\":\"gpt-5.6-luna\""))
		assertTrue(body.contains("dm drogerie markt"))
		assertTrue(body.contains("ROHLÍK BIELY 50G"))
		assertTrue(body.contains("\"type\":\"json_schema\""))
		assertTrue(body.contains("Pečivo"))
		assertTrue(body.contains("Káva"))
		assertTrue(body.contains("do not classify them as Nealkoholické nápoje"))
		assertEquals("Biely rožok", result.single().canonicalName)
		assertEquals("Pečivo", result.single().subcategory)
		assertEquals(SpendingType.ESSENTIAL, result.single().spendingType)
	}

	@Test
	fun readsStructuredOutputFromResponseItemsWhenOutputTextShortcutIsMissing() {
		val transport = object : OpenAiTransport {
			override fun post(apiKey: String, body: String): String = """
				{
				  "output": [{
				    "type": "message",
				    "content": [{
				      "type": "output_text",
				      "text": "{\"items\":[{\"index\":0,\"canonicalName\":\"Jar Lemon\",\"category\":\"Drogéria\",\"subcategory\":\"Čistenie domácnosti\",\"spendingType\":\"REGULAR\",\"confidence\":0.95}]}"
				    }]
				  }]
				}
			""".trimIndent()
		}
		val client = OpenAiCategorizationClient("test-key", transport)

		val result = client.categorize(
			CategorizationRequest(
				merchant = "Tesco",
				items = listOf(CategorizationRequestItem(0, "JAR LEMON 900ML", 399, 1.0)),
			),
		)

		assertEquals("Čistenie domácnosti", result.single().subcategory)
	}
}
