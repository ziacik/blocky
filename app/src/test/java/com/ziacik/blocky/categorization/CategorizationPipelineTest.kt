package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CategorizationPipelineTest {
	@Test
	fun blankEndpointLeavesReceiptUntouched() {
		val receipt = receipt()

		val categorized = CategorizationPipeline.create(
			endpoint = "",
			transportFactory = { error("Transport must not be created") },
		).categorize(receipt)

		assertSame(receipt, categorized)
	}

	@Test
	fun configuredEndpointUsesReceiptContext() {
		var requestedEndpoint: String? = null
		var requestBody: String? = null
		val categorizer = CategorizationPipeline.create(
			endpoint = "https://example.test/categorize",
			transportFactory = { endpoint ->
				requestedEndpoint = endpoint
				object : CategorizationTransport {
					override fun post(body: String): String {
						requestBody = body
						return """
							{
							  "items": [
							    {
							      "index": 0,
							      "canonicalName": "Biely rožok",
							      "category": "Potraviny",
							      "subcategory": "Pečivo",
							      "spendingType": "ESSENTIAL",
							      "confidence": 0.98
							    }
							  ]
							}
						""".trimIndent()
					}
				}
			},
		)

		val result = categorizer.categorize(receipt())

		assertEquals("https://example.test/categorize", requestedEndpoint)
		assert(requestBody!!.contains("\"merchant\":\"dm drogerie markt\""))
		assert(requestBody!!.contains("\"name\":\"ROHLÍK BIELY 50G\""))
		assertEquals("Pečivo", result.items.single().subcategory)
		assertEquals(SpendingType.ESSENTIAL, result.items.single().spendingType)
	}

	@Test
	fun apiKeyTakesPrecedenceOverBackendEndpoint() {
		var receivedApiKey: String? = null
		var backendTransportCreated = false
		val client = object : CategorizationClient {
			override fun categorize(request: CategorizationRequest): List<CategorizedItem> = listOf(
				CategorizedItem(
					index = 0,
					canonicalName = "Biely rožok",
					category = "Potraviny",
					subcategory = "Pečivo",
					spendingType = SpendingType.ESSENTIAL,
					confidence = 0.99,
				),
			)
		}

		val result = CategorizationPipeline.create(
			apiKey = "test-key",
			endpoint = "https://example.test/categorize",
			openAiClientFactory = { key ->
				receivedApiKey = key
				client
			},
			transportFactory = {
				backendTransportCreated = true
				error("Backend transport must not be created when OPENAI_API_KEY is set")
			},
		).categorize(receipt())

		assertEquals("test-key", receivedApiKey)
		assertEquals(false, backendTransportCreated)
		assertEquals("Pečivo", result.items.single().subcategory)
	}

	private fun receipt() = Receipt(
		id = "receipt-1",
		merchant = "dm drogerie markt",
		issuedAt = 1L,
		totalCents = 49L,
		items = listOf(
			ReceiptItem(
				originalName = "ROHLÍK BIELY 50G",
				canonicalName = "ROHLÍK BIELY 50G",
				category = "Nezaradené",
				subcategory = null,
				quantity = 1.0,
				totalCents = 49L,
				vatRate = null,
			),
		),
		rawJson = "{}",
	)
}
