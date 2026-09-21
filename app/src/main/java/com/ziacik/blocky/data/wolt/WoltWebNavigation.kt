package com.ziacik.blocky.data.wolt

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object WoltWebNavigation {
	fun shouldKeepInsideWebView(url: String): Boolean =
		url.startsWith("intent://", ignoreCase = true) ||
			url.startsWith("wolt://", ignoreCase = true)

	fun browserFallbackUrl(url: String): String? {
		if (!url.startsWith("intent://", ignoreCase = true)) return null

		val marker = "S.browser_fallback_url="
		val start = url.indexOf(marker)
		if (start < 0) return null

		val encoded = url.substring(start + marker.length)
			.substringBefore(';')
			.takeIf(String::isNotBlank)
			?: return null

		return runCatching {
			URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
		}.getOrNull()
			?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
	}
}
