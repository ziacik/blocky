package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertEquals
import org.junit.Test

class WoltCredentialParserTest {
	@Test
	fun extractsRawJwtFromWtokenCookie() {
		val cookies = "__wtoken=abc.def.ghi; foo=bar"

		assertEquals("abc.def.ghi", WoltCredentialParser.accessToken(cookies))
	}

	@Test
	fun extractsJwtFromUrlEncodedJsonWtokenCookie() {
		val cookies = "__wtoken=%7B%22accessToken%22%3A%22abc.def.ghi%22%2C%22expirationTime%22%3A1771540095000%7D"

		assertEquals("abc.def.ghi", WoltCredentialParser.accessToken(cookies))
	}

	@Test
	fun extractsRefreshTokenFromUrlEncodedJsonCookie() {
		val cookies = "__wrtoken=%7B%22refreshToken%22%3A%22refresh-123%22%7D"

		assertEquals("refresh-123", WoltCredentialParser.refreshToken(cookies))
	}
}
