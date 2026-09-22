package com.ziacik.blocky.categorization

import java.net.HttpURLConnection
import java.net.URL

interface CategorizationTransport {
	fun post(body: String): String
}

class HttpCategorizationClient(
	private val transport: CategorizationTransport,
) : CategorizationClient {
	override fun categorize(request: CategorizationRequest): List<CategorizedItem> =
		CategorizationJson.decodeResponse(
			transport.post(CategorizationJson.encodeRequest(request)),
		)
}

class UrlCategorizationTransport(
	private val endpoint: String,
	private val connectTimeoutMillis: Int = 10_000,
	private val readTimeoutMillis: Int = 30_000,
) : CategorizationTransport {
	override fun post(body: String): String {
		val connection = URL(endpoint).openConnection() as HttpURLConnection
		return try {
			connection.requestMethod = "POST"
			connection.connectTimeout = connectTimeoutMillis
			connection.readTimeout = readTimeoutMillis
			connection.doOutput = true
			connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
			connection.outputStream.use { output ->
				output.write(body.toByteArray(Charsets.UTF_8))
			}

			val status = connection.responseCode
			val stream = if (status in 200..299) connection.inputStream else connection.errorStream
			val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
			if (status !in 200..299) {
				error("Categorization backend returned HTTP $status: $response")
			}
			response
		} finally {
			connection.disconnect()
		}
	}
}
