package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WoltRequestHeadersTest {
	@Test
	fun deviceHeadersMatchTelemetryCookies() {
		val headers = WoltRequestHeaders.fromCookies(
			"foo=bar; telemetryDeviceId=device-123; telemetrySessionId=session-456; __wtoken=token"
		)

		assertEquals("device-123", headers["x-wolt-web-clientid"])
		assertEquals("session-456", headers["w-wolt-session-id"])
		assertEquals("Web", headers["platform"])
		assertEquals("https://wolt.com", headers["Origin"])
		assertEquals("https://wolt.com/", headers["Referer"])
	}

	@Test
	fun doesNotInventDeviceIdsWhenCookiesDoNotContainThem() {
		val headers = WoltRequestHeaders.fromCookies("__wtoken=token")

		assertFalse(headers.containsKey("x-wolt-web-clientid"))
		assertFalse(headers.containsKey("w-wolt-session-id"))
	}
}
