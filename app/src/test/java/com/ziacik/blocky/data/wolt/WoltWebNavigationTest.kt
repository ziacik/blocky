package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WoltWebNavigationTest {
	@Test
	fun extractsBrowserFallbackFromIntentUrl() {
		val url = "intent://open#Intent;scheme=wolt;package=com.wolt.android;S.browser_fallback_url=https%3A%2F%2Fwolt.com%2Fen%2Fme;end"

		val fallback = WoltWebNavigation.browserFallbackUrl(url)

		assertEquals("https://wolt.com/en/me", fallback)
	}

	@Test
	fun blocksIntentWithoutBrowserFallback() {
		val url = "intent://open#Intent;scheme=wolt;package=com.wolt.android;end"

		assertTrue(WoltWebNavigation.shouldKeepInsideWebView(url))
		assertNull(WoltWebNavigation.browserFallbackUrl(url))
	}

	@Test
	fun blocksWoltAppSchemeFromLeavingLoginFlow() {
		assertTrue(WoltWebNavigation.shouldKeepInsideWebView("wolt://front-page"))
	}

	@Test
	fun leavesNormalHttpsNavigationToWebView() {
		assertEquals(false, WoltWebNavigation.shouldKeepInsideWebView("https://wolt.com/en"))
	}
}
