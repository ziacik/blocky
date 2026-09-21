package com.ziacik.blocky.data.wolt

object WoltCookieJar {
	fun mergeSetCookieHeaders(current: String, setCookieHeaders: List<String>): String {
		val cookies = parseCookieHeader(current)
		setCookieHeaders.forEach { header ->
			putCookiePair(cookies, header.substringBefore(';'))
		}
		return serialize(cookies)
	}

	fun mergeCookieHeaders(headers: List<String>): String {
		val cookies = linkedMapOf<String, String>()
		headers.forEach { header ->
			parseCookieHeader(header).forEach { (name, value) -> cookies[name] = value }
		}
		return serialize(cookies)
	}

	private fun parseCookieHeader(header: String): LinkedHashMap<String, String> {
		val cookies = linkedMapOf<String, String>()
		header.split(';').forEach { part -> putCookiePair(cookies, part) }
		return cookies
	}

	private fun putCookiePair(cookies: LinkedHashMap<String, String>, rawPair: String) {
		val pair = rawPair.trim()
		val separator = pair.indexOf('=')
		if (separator <= 0) return
		val name = pair.substring(0, separator).trim()
		val value = pair.substring(separator + 1).trim()
		if (name.isNotEmpty()) cookies[name] = value
	}

	private fun serialize(cookies: LinkedHashMap<String, String>): String =
		cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}
