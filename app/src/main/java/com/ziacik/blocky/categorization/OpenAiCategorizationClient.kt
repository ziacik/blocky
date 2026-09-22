package com.ziacik.blocky.categorization

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface OpenAiTransport {
	fun post(apiKey: String, body: String): String
}

class OpenAiResponsesTransport(
	private val endpoint: String = "https://api.openai.com/v1/responses",
	private val connectTimeoutMillis: Int = 10_000,
	private val readTimeoutMillis: Int = 30_000,
) : OpenAiTransport {
	override fun post(apiKey: String, body: String): String {
		val connection = URL(endpoint).openConnection() as HttpURLConnection
		return try {
			connection.requestMethod = "POST"
			connection.connectTimeout = connectTimeoutMillis
			connection.readTimeout = readTimeoutMillis
			connection.doOutput = true
			connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
			connection.setRequestProperty("Authorization", "Bearer $apiKey")
			connection.outputStream.use { output ->
				output.write(body.toByteArray(Charsets.UTF_8))
			}

			val status = connection.responseCode
			val stream = if (status in 200..299) connection.inputStream else connection.errorStream
			val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
			if (status !in 200..299) {
				error("OpenAI returned HTTP $status: $response")
			}
			response
		} finally {
			connection.disconnect()
		}
	}
}

class OpenAiCategorizationClient(
	private val apiKey: String,
	private val transport: OpenAiTransport = OpenAiResponsesTransport(),
) : CategorizationClient {
	init {
		require(apiKey.isNotBlank()) { "OpenAI API key must not be blank" }
	}

	override fun categorize(request: CategorizationRequest): List<CategorizedItem> {
		val response = transport.post(
			apiKey = apiKey,
			body = encodeRequest(request),
		)
		return CategorizationJson.decodeResponse(extractOutputText(response))
	}

	private fun encodeRequest(request: CategorizationRequest): String = JSONObject()
		.put("model", MODEL)
		.put("store", false)
		.put(
			"reasoning",
			JSONObject().put("effort", "none"),
		)
		.put("instructions", instructions())
		.put("input", CategorizationJson.encodeRequest(request))
		.put(
			"text",
			JSONObject().put(
				"format",
				JSONObject()
					.put("type", "json_schema")
					.put("name", "receipt_categorization")
					.put("strict", true)
					.put("schema", outputSchema()),
			),
		)
		.toString()

	private fun instructions(): String = """
		Classify every receipt item. Use the merchant name and the other items on the same receipt as context.
		Return exactly one result for every input index and preserve each index.
		canonicalName must be a concise, human-readable Slovak product name.
		ESSENTIAL means a basic necessary purchase, REGULAR means an ordinary recurring purchase,
		and DISCRETIONARY means a purchase that could reasonably be omitted.
		Use only categories and subcategories permitted by the supplied output schema.
	""".trimIndent()

	private fun outputSchema(): JSONObject {
		val categories = ExpenseTaxonomy.categories.keys
		val subcategories = ExpenseTaxonomy.categories.values.flatten().distinct()

		return JSONObject()
			.put("type", "object")
			.put(
				"properties",
				JSONObject().put(
					"items",
					JSONObject()
						.put("type", "array")
						.put(
							"items",
							JSONObject()
								.put("type", "object")
								.put(
									"properties",
									JSONObject()
										.put("index", JSONObject().put("type", "integer"))
										.put("canonicalName", JSONObject().put("type", "string").put("minLength", 1))
										.put(
											"category",
											JSONObject()
												.put("type", "string")
												.put("enum", JSONArray(categories)),
										)
										.put(
											"subcategory",
											JSONObject()
												.put("type", "string")
												.put("enum", JSONArray(subcategories)),
										)
										.put(
											"spendingType",
											JSONObject()
												.put("type", "string")
												.put("enum", JSONArray(listOf("ESSENTIAL", "REGULAR", "DISCRETIONARY"))),
										)
										.put(
											"confidence",
											JSONObject()
												.put("type", "number")
												.put("minimum", 0)
												.put("maximum", 1),
										),
								)
								.put(
									"required",
									JSONArray(
										listOf(
											"index",
											"canonicalName",
											"category",
											"subcategory",
											"spendingType",
											"confidence",
										),
									),
								)
								.put("additionalProperties", false),
						),
				),
			)
			.put("required", JSONArray(listOf("items")))
			.put("additionalProperties", false)
	}

	private fun extractOutputText(response: String): String {
		val root = JSONObject(response)
		root.optString("output_text")
			.takeIf(String::isNotBlank)
			?.let { return it }

		val output = root.optJSONArray("output")
			?: error("OpenAI response does not contain output")
		for (outputIndex in 0 until output.length()) {
			val item = output.optJSONObject(outputIndex) ?: continue
			val content = item.optJSONArray("content") ?: continue
			for (contentIndex in 0 until content.length()) {
				val part = content.optJSONObject(contentIndex) ?: continue
				if (part.optString("type") == "output_text") {
					part.optString("text")
						.takeIf(String::isNotBlank)
						?.let { return it }
				}
			}
		}
		error("OpenAI response does not contain output text")
	}

	private companion object {
		const val MODEL = "gpt-5.6-luna"
	}
}
