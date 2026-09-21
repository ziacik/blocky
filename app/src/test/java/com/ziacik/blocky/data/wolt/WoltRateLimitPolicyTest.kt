package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertEquals
import org.junit.Test

class WoltRateLimitPolicyTest {
	@Test
	fun usesExponentialBackoffFor429() {
		assertEquals(2_000L, WoltRateLimitPolicy.retryDelayMillis(attempt = 1, retryAfterMillis = null))
		assertEquals(4_000L, WoltRateLimitPolicy.retryDelayMillis(attempt = 2, retryAfterMillis = null))
		assertEquals(8_000L, WoltRateLimitPolicy.retryDelayMillis(attempt = 3, retryAfterMillis = null))
	}

	@Test
	fun honorsLongerRetryAfterHeader() {
		assertEquals(
			7_000L,
			WoltRateLimitPolicy.retryDelayMillis(attempt = 1, retryAfterMillis = 7_000L),
		)
	}

	@Test
	fun capsRetryDelayAtOneMinute() {
		assertEquals(
			60_000L,
			WoltRateLimitPolicy.retryDelayMillis(attempt = 8, retryAfterMillis = null),
		)
	}

	@Test
	fun pacingGrowsByHalfSecondAfterEach429() {
		assertEquals(1_100L, WoltRateLimitPolicy.pacingDelayMillis(rateLimitCount = 0))
		assertEquals(1_600L, WoltRateLimitPolicy.pacingDelayMillis(rateLimitCount = 1))
		assertEquals(6_100L, WoltRateLimitPolicy.pacingDelayMillis(rateLimitCount = 20))
	}
}
