package com.ziacik.blocky.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class EkasaClient {
	fun findReceipt(qrValue: String): String {
		val request = EkasaLookupRequest.fromQr(qrValue)
		val body = when (request) {
			is EkasaLookupRequest.Online -> JSONObject()
				.put("receiptId", request.receiptId)
			is EkasaLookupRequest.Offline -> JSONObject()
				.put("okp", request.okp)
				.put("cashRegisterCode", request.cashRegisterCode)
				.put("issueDateFormatted", request.issueDateFormatted)
				.put("receiptNumber", request.receiptNumber)
				.put("totalAmount", request.totalAmount)
		}.toString()

		val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
			requestMethod = "POST"
			connectTimeout = 15_000
			readTimeout = 15_000
			doOutput = true
			setRequestProperty("Content-Type", "application/json; charset=utf-8")
			setRequestProperty("Accept", "application/json")
			setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Blocky/0.1")
		}

		return try {
			connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
			val code = connection.responseCode
			val stream = if (code in 200..299) connection.inputStream else connection.errorStream
			val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
			if (code !in 200..299) {
				throw IOException("eKasa odpovedala HTTP " + code + response.takeIf { it.isNotBlank() }?.let { ": " + it }.orEmpty())
			}
			response
		} finally {
			connection.disconnect()
		}
	}

	private companion object {
		const val ENDPOINT = "https://ekasa.financnasprava.sk/mdu/api/v1/opd/receipt/find"
	}
}
