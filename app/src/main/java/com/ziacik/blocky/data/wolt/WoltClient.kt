package com.ziacik.blocky.data.wolt

internal const val WOLT_ORDER_HISTORY_MAX_LIMIT = 100

internal fun normalizeWoltOrderHistoryLimit(limit: Int): Int =
	limit.coerceIn(1, WOLT_ORDER_HISTORY_MAX_LIMIT)

import android.os.SystemClock
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class WoltClient(
	private val sessionStore: WoltSessionStore,
) {
	private val webClientId = UUID.randomUUID().toString()
	private var lastRequestAtMillis = 0L
	private var rateLimitCount = 0

	fun fetchOrderHistory(
		limit: Int = WOLT_ORDER_HISTORY_MAX_LIMIT,
		diagnostic: (String) -> Unit = {},
	): String = authenticatedGet(
		ORDER_HISTORY_ENDPOINT + "?limit=" + normalizeWoltOrderHistoryLimit(limit),
		diagnostic,
	)

	fun fetchOrderDetail(
		purchaseId: String,
		diagnostic: (String) -> Unit = {},
	): String {
		val encodedId = URLEncoder.encode(purchaseId, StandardCharsets.UTF_8.name())
		return authenticatedGet(
			ORDER_HISTORY_ENDPOINT + "purchase/" + encodedId + "?tips_use_percentage=true",
			diagnostic,
		)
	}

	private fun authenticatedGet(
		url: String,
		diagnostic: (String) -> Unit,
	): String {
		var cookies = sessionStore.cookies() ?: throw WoltAuthException("Wolt nie je pripojený.")
		var accessToken = WoltCredentialParser.accessToken(cookies)

		if (accessToken == null) {
			diagnostic("auth: access token chýba, obnovujem session")
			cookies = refreshSession(cookies, diagnostic)
			accessToken = WoltCredentialParser.accessToken(cookies)
				?: throw WoltAuthException("Wolt access token sa nepodarilo načítať.")
		}

		var response = requestWithBackoff(
			url = url,
			cookies = cookies,
			accessToken = accessToken,
			diagnostic = diagnostic,
		)
		if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
			diagnostic("auth: HTTP 401, obnovujem access token")
			cookies = refreshSession(cookies, diagnostic)
			accessToken = WoltCredentialParser.accessToken(cookies)
				?: throw WoltAuthException("Wolt access token sa nepodarilo obnoviť.")
			response = requestWithBackoff(
				url = url,
				cookies = cookies,
				accessToken = accessToken,
				diagnostic = diagnostic,
			)
		}

		persistRotatedCookies(cookies, response.setCookies)

		if (response.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
			sessionStore.clearCookies()
			diagnostic("error: HTTP 401 aj po obnove session")
			throw WoltAuthException("Wolt prihlásenie už nie je platné. Pripoj Wolt znova.")
		}
		if (response.status == HttpURLConnection.HTTP_FORBIDDEN) {
			diagnostic("error: HTTP 403")
			logBodyExcerpt(response.body, diagnostic)
			throw IOException("Wolt odmietol synchronizáciu (HTTP 403).")
		}
		if (response.status == HTTP_TOO_MANY_REQUESTS) {
			diagnostic("error: HTTP 429 aj po retry")
			logBodyExcerpt(response.body, diagnostic)
			throw IOException(
				"Wolt stále obmedzuje počet požiadaviek (HTTP 429).",
			)
		}
		if (response.status !in 200..299) {
			diagnostic("error: HTTP " + response.status)
			logBodyExcerpt(response.body, diagnostic)
			throw IOException("Wolt API vrátilo HTTP " + response.status)
		}
		return response.body
	}

	private fun requestWithBackoff(
		url: String,
		cookies: String,
		accessToken: String,
		diagnostic: (String) -> Unit,
	): WoltHttpResponse {
		var lastResponse: WoltHttpResponse? = null

		for (attempt in 1..MAX_ATTEMPTS) {
			waitForPacing(diagnostic)
			diagnostic(
				"request: GET " + safeEndpoint(url) +
					" (attempt " + attempt + "/" + MAX_ATTEMPTS + ")",
			)
			val response = request(
				url = url,
				method = "GET",
				cookies = cookies,
				accessToken = accessToken,
			)
			lastResponse = response
			diagnostic(
				"response: HTTP " + response.status +
					(response.retryAfterMillis?.let { ", Retry-After=" + it + " ms" } ?: ""),
			)

			if (response.status != HTTP_TOO_MANY_REQUESTS &&
				response.status != HTTP_SERVICE_UNAVAILABLE
			) {
				return response
			}

			if (response.status == HTTP_TOO_MANY_REQUESTS) {
				rateLimitCount++
			}

			if (attempt < MAX_ATTEMPTS) {
				val waitMillis = WoltRateLimitPolicy.retryDelayMillis(
					attempt = attempt,
					retryAfterMillis = response.retryAfterMillis,
				)
				diagnostic("retry: čakám " + waitMillis + " ms")
				Thread.sleep(waitMillis)
			}
		}

		return requireNotNull(lastResponse)
	}

	private fun waitForPacing(diagnostic: (String) -> Unit) {
		val delay = WoltRateLimitPolicy.pacingDelayMillis(rateLimitCount)
		val now = SystemClock.elapsedRealtime()
		if (lastRequestAtMillis > 0L) {
			val elapsed = now - lastRequestAtMillis
			val wait = delay - elapsed
			if (wait > 0L) {
				diagnostic("pacing: čakám " + wait + " ms")
				Thread.sleep(wait)
			}
		}
		lastRequestAtMillis = SystemClock.elapsedRealtime()
	}

	private fun refreshSession(
		cookies: String,
		diagnostic: (String) -> Unit,
	): String {
		val refreshToken = WoltCredentialParser.refreshToken(cookies)
			?: run {
				sessionStore.clearCookies()
				throw WoltAuthException("Wolt refresh token chýba. Pripoj Wolt znova.")
			}

		diagnostic("auth: POST /v1/wauth2/access_token")
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
		diagnostic("auth: HTTP " + response.status)

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
		diagnostic("auth: token obnovený")
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
			val retryAfterMillis = parseRetryAfterMillis(
				connection.getHeaderField("Retry-After"),
			)

			return WoltHttpResponse(
				status = status,
				body = responseBody,
				setCookies = setCookies,
				retryAfterMillis = retryAfterMillis,
			)
		} finally {
			connection.disconnect()
		}
	}

	private fun parseRetryAfterMillis(value: String?): Long? {
		val header = value?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
		header.toLongOrNull()?.let { seconds ->
			return (seconds * 1_000L).coerceAtLeast(0L)
		}

		return runCatching {
			val retryAt = ZonedDateTime.parse(
				header,
				DateTimeFormatter.RFC_1123_DATE_TIME,
			).toInstant().toEpochMilli()
			(retryAt - System.currentTimeMillis()).coerceAtLeast(0L)
		}.getOrNull()
	}

	private fun safeEndpoint(url: String): String = runCatching {
		val parsed = URL(url)
		parsed.path + (parsed.query?.let { "?" + it } ?: "")
	}.getOrDefault("<invalid-url>")

	private fun logBodyExcerpt(
		body: String,
		diagnostic: (String) -> Unit,
	) {
		val excerpt = body
			.replace("\n", " ")
			.replace("\r", " ")
			.trim()
			.take(300)
		if (excerpt.isNotEmpty()) {
			diagnostic("body: " + excerpt)
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
		val retryAfterMillis: Long?,
	)

	private companion object {
		const val ORDER_HISTORY_ENDPOINT =
			"https://consumer-api.wolt.com/order-tracking-api/v1/order_history/"
		const val ACCESS_TOKEN_ENDPOINT =
			"https://authentication.wolt.com/v1/wauth2/access_token"
		const val HTTP_TOO_MANY_REQUESTS = 429
		const val HTTP_SERVICE_UNAVAILABLE = 503
		const val MAX_ATTEMPTS = 6
	}
}

class WoltAuthException(message: String) : IOException(message)
