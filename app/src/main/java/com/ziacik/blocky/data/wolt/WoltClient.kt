package com.ziacik.blocky.data.wolt

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class WoltClient(
	private val sessionStore: WoltSessionStore,
) {
	private val webClientId = UUID.randomUUID().toString()

	fun fetchOrders(limit: Int = 200): String {
		var cookies = sessionStore.cookies() ?: throw WoltAuthException("Wolt nie je pripojený.")
		var accessToken = WoltCredentialParser.accessToken(cookies)

		if (accessToken == null) {
			cookies = refreshSession(cookies)
			accessToken = WoltCredentialParser.accessToken(cookies)
				?: throw WoltAuthException("Wolt access token sa nepodarilo načítať.")
		}

		var response = requestOrders(cookies, accessToken, limit)
		if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
			cookies = refreshSession(cookies)
			accessToken = WoltCredentialParser.accessToken(cookies)
				?: throw WoltAuthException("Wolt access token sa nepodarilo obnoviť.")
			response = requestOrders(cookies, accessToken, limit)
		}

		persistRotatedCookies(cookies, response.setCookies)

		if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
			sessionStore.clearCookies()
			throw WoltAuthException("Wolt prihlásenie už nie je platné. Pripoj Wolt znova.")
		}
		if (response.status == HttpURLConnection.HTTP_FORBIDDEN) {
			throw IOException("Wolt odmietol synchronizáciu (HTTP 403).")
		}
		if (response.status !in 200..299) {
			val suffix = response.body.takeIf(String::isNotBlank)?.let { ": " + it }.orEmpty()
			throw IOException("Wolt API vrátilo HTTP " + response.status + suffix)
		}
		return response.body
	}

	private fun requestOrders(
		cookies: String,
		accessToken: String,
		limit: Int,
	): WoltHttpResponse = request(
		url = ORDERS_ENDPOINT + "?limit=" + limit,
		method = "GET",
		cookies = cookies,
		accessToken = accessToken,
	)

	private fun refreshSession(cookies: String): String {
		val refreshToken = WoltCredentialParser.refreshToken(cookies)
			?: run {
				sessionStore.clearCookies()
				throw WoltAuthException("Wolt refresh token chýba. Pripoj Wolt znova.")
			}

		val body = "grant_type=refresh_token&refresh_token=" +
			URLEncoder.encode(refreshToken, StandardCharsets.UTF_8.name())
		val response = request(
			url = ACCESS_TOKEN_ENDPOINT,
			method = "POST",
			body = body,
			extraHeaders = mapOf(
				"Content-Type" to "application/x-www-form-urlencoded",
				"Accept" to "application/json",
			),
		)

		if (response.status !in 200..299) {
			sessionStore.clearCookies()
			throw WoltAuthException(
				"Wolt session sa nepodarilo obnoviť (HTTP " + response.status + ").",
			)
		}

		val payload = runCatching { JSONObject(response.body) }.getOrNull()
			?: throw WoltAuthException("Wolt vrátil neplatnú odpoveď pri obnove session.")
		val data = payload.optJSONObject("data")
		val newAccess = firstNonBlank(
			payload.optString("access_token"),
			payload.optString("accessToken"),
			payload.optString("__wtoken"),
			data?.optString("access_token"),
			data?.optString("accessToken"),
			data?.optString("__wtoken"),
		) ?: throw WoltAuthException("Wolt nevrátil nový access token.")
		val newRefresh = firstNonBlank(
			payload.optString("refresh_token"),
			payload.optString("refreshToken"),
			payload.optString("__wrtoken"),
			data?.optString("refresh_token"),
			data?.optString("refreshToken"),
			data?.optString("__wrtoken"),
		) ?: refreshToken

		val updated = WoltCookieJar.mergeSetCookieHeaders(
			cookies,
			listOf(
				"__wtoken=" + newAccess,
				"__wrtoken=" + newRefresh,
			),
		)
		sessionStore.saveCookies(updated)
		return updated
	}

	private fun request(
		url: String,
		method: String,
		cookies: String? = null,
		accessToken: String? = null,
		body: String? = null,
		extraHeaders: Map<String, String> = emptyMap(),
	): WoltHttpResponse {
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			requestMethod = method
			connectTimeout = 15_000
			readTimeout = 20_000
			WoltRequestHeaders.create(
				webClientId = webClientId,
				accessToken = accessToken,
			).forEach { (name, value) ->
				setRequestProperty(name, value)
			}
			extraHeaders.forEach { (name, value) ->
				setRequestProperty(name, value)
			}
			cookies?.let { setRequestProperty("Cookie", it) }
			if (body != null) {
				doOutput = true
			}
		}

		try {
			if (body != null) {
				connection.outputStream.use {
					it.write(body.toByteArray(StandardCharsets.UTF_8))
				}
			}
			val status = connection.responseCode
			val responseBody = (
				if (status in 200..299) connection.inputStream else connection.errorStream
			)?.bufferedReader()?.use { it.readText() }.orEmpty()
			val setCookies = connection.headerFields.entries
				.filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
				.flatMap { (_, values) -> values.orEmpty() }

			return WoltHttpResponse(
				status = status,
				body = responseBody,
				setCookies = setCookies,
			)
		} finally {
			connection.disconnect()
		}
	}

	private fun persistRotatedCookies(cookies: String, setCookies: List<String>) {
		if (setCookies.isEmpty()) return
		sessionStore.saveCookies(WoltCookieJar.mergeSetCookieHeaders(cookies, setCookies))
	}

	private fun firstNonBlank(vararg values: String?): String? =
		values.firstOrNull { !it.isNullOrBlank() }

	private data class WoltHttpResponse(
		val status: Int,
		val body: String,
		val setCookies: List<String>,
	)

	private companion object {
		const val ORDERS_ENDPOINT = "https://consumer-api.wolt.com/order-xp/web/v1/pages/orders"
		const val ACCESS_TOKEN_ENDPOINT = "https://authentication.wolt.com/v1/wauth2/access_token"
	}
}

class WoltAuthException(message: String) : IOException(message)
