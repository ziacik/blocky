package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertEquals
import org.junit.Test

class WoltRequestHeadersTest {
	@Test
	fun buildsCurrentWoltWebIdentityHeaders() {
		val headers = WoltRequestHeaders.create(
			webClientId = "client-123",
			accessToken = "abc.def.ghi",
		)

		assertEquals("client-123", headers["x-wolt-web-clientid"])
		assertEquals("no-analytics-consent", headers["w-wolt-session-id"])
		assertEquals("Bearer abc.def.ghi", headers["Authorization"])
		assertEquals("Web", headers["platform"])
		assertEquals("1.16.79", headers["client-version"])
		assertEquals("1.16.79", headers["clientversionnumber"])
	}
}
