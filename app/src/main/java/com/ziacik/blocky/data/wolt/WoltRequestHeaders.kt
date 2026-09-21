package com.ziacik.blocky.data.wolt

object WoltRequestHeaders {
	fun create(
		webClientId: String,
		accessToken: String? = null,
	): Map<String, String> = buildMap {
		put("Accept", "application/json, text/plain, */*")
		put("app-language", "sk")
		put("platform", "Web")
		put("client-version", "1.16.79")
		put("clientversionnumber", "1.16.79")
		put("w-wolt-session-id", "no-analytics-consent")
		put("x-wolt-web-clientid", webClientId)
		put("Origin", "https://wolt.com")
		put("Referer", "https://wolt.com/")
		put(
			"User-Agent",
			"Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/147.0.0.0 Mobile Safari/537.36",
		)
		accessToken?.takeIf(String::isNotBlank)?.let {
			put("Authorization", "Bearer $it")
		}
	}
}
