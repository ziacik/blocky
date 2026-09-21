package com.ziacik.blocky.data.wolt

import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object WoltCredentialParser {
	private val jwt = Regex("""[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

	fun accessToken(cookies: String): String? =
		extractCookie(cookies, "__wtoken")
			?.let(::decode)
			?.let(::findAccessToken)

	fun refreshToken(cookies: String): String? =
		extractCookie(cookies, "__wrtoken")
			?.let(::decode)
			?.let(::findRefreshToken)

	private fun extractCookie(cookies: String, name: String): String? =
		cookies.split(';')
			.asSequence()
			.map(String::trim)
			.mapNotNull { part ->
				val separator = part.indexOf('=')
				if (separator <= 0) null
				else part.substring(0, separator).trim() to part.substring(separator + 1).trim()
			}
			.firstOrNull { (cookieName, _) -> cookieName == name }
			?.second

	private fun decode(value: String): String =
		runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
			.getOrDefault(value)

	private fun findAccessToken(value: String): String? {
		jwt.find(value)?.value?.let { return it }
		val json = runCatching { JSONObject(value) }.getOrNull() ?: return null
		for (key in listOf("accessToken", "access_token", "__wtoken", "token")) {
			val candidate = json.optString(key).takeIf(String::isNotBlank) ?: continue
			jwt.find(candidate)?.value?.let { return it }
		}
		return null
	}

	private fun findRefreshToken(value: String): String? {
		val json = runCatching { JSONObject(value) }.getOrNull()
		if (json != null) {
			for (key in listOf("refreshToken", "refresh_token", "__wrtoken", "token")) {
				json.optString(key).takeIf(String::isNotBlank)?.let { return it }
			}
		}
		return value.takeIf(String::isNotBlank)
	}
}
