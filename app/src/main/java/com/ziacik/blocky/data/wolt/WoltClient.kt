package com.ziacik.blocky.data.wolt

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class WoltClient(
	private val sessionStore: WoltSessionStore,
) {
	fun fetchOrders(limit: Int = 200): String {
		val cookies = sessionStore.cookies() ?: throw WoltAuthException("Wolt nie je pripojený.")
		val connection = (URL("$ORDERS_ENDPOINT?limit=$limit").openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			connectTimeout = 15_000
			readTimeout = 20_000
			WoltRequestHeaders.fromCookies(cookies).forEach { (name, value) ->
				setRequestProperty(name, value)
			}
			setRequestProperty("Cookie", cookies)
		}

		try {
			val status = connection.responseCode
			val rotatedCookies = connection.headerFields.entries
				.filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
				.flatMap { (_, values) -> values.orEmpty() }
			if (rotatedCookies.isNotEmpty()) {
				sessionStore.saveCookies(WoltCookieJar.mergeSetCookieHeaders(cookies, rotatedCookies))
			}

			val stream = if (status in 200..299) connection.inputStream else connection.errorStream
			val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

			if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
				sessionStore.clearCookies()
				throw WoltAuthException("Wolt prihlásenie vypršalo. Pripoj Wolt znova.")
			}
			if (status == HttpURLConnection.HTTP_FORBIDDEN) {
				throw IOException(
					"Wolt odmietol synchronizáciu (HTTP 403). " +
						"Session existuje, ale request neprešiel autentizáciou.",
				)
			}
			if (status !in 200..299) {
				val suffix = body.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
				throw IOException("Wolt API vrátilo HTTP $status$suffix")
			}
			return body
		} finally {
			connection.disconnect()
		}
	}

	private companion object {
		const val ORDERS_ENDPOINT = "https://consumer-api.wolt.com/order-xp/web/v1/pages/orders"
	}
}

class WoltAuthException(message: String) : IOException(message)
