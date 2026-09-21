package com.ziacik.blocky.data.wolt

object WoltRequestHeaders {
	fun fromCookies(cookies: String): Map<String, String> = buildMap {
		put("Accept", "application/json, text/plain, */*")
		put("Accept-Language", "sk-SK,sk;q=0.9,en;q=0.8")
		put("app-language", "sk")
		put("app-locale", "sk")
		put("platform", "Web")
		put("client-version", "1.16.99")
		put("clientversionnumber", "1.16.99")
		put("Origin", "https://wolt.com")
		put("Referer", "https://wolt.com/")
		put(
			"User-Agent",
			"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/147.0.0.0 Mobile Safari/537.36",
		)

		cookieValue(cookies, "telemetryDeviceId")?.let {
			put("x-wolt-web-clientid", it)
		}
		cookieValue(cookies, "telemetrySessionId")?.let {
			put("w-wolt-session-id", it)
		}
	}

	private fun cookieValue(cookies: String, name: String): String? =
		cookies
			.split(';')
			.asSequence()
			.map(String::trim)
			.mapNotNull { part ->
				val separator = part.indexOf('=')
				if (separator <= 0) null
				else part.substring(0, separator).trim() to part.substring(separator + 1).trim()
			}
			.firstOrNull { (cookieName, _) -> cookieName == name }
			?.second
			?.takeIf(String::isNotBlank)
}
