package com.ziacik.blocky.data.wolt

import kotlin.math.min

object WoltRateLimitPolicy {
	private const val BASE_PACING_MILLIS = 1_100L
	private const val PACING_BUMP_MILLIS = 500L
	private const val MAX_PACING_EXTRA_MILLIS = 5_000L
	private const val BASE_RETRY_MILLIS = 2_000L
	private const val MAX_RETRY_MILLIS = 60_000L

	fun pacingDelayMillis(rateLimitCount: Int): Long =
		BASE_PACING_MILLIS +
			min(
				rateLimitCount.coerceAtLeast(0).toLong() * PACING_BUMP_MILLIS,
				MAX_PACING_EXTRA_MILLIS,
			)

	fun retryDelayMillis(attempt: Int, retryAfterMillis: Long?): Long {
		val exponent = (attempt.coerceAtLeast(1) - 1).coerceAtMost(20)
		val exponential = (BASE_RETRY_MILLIS shl exponent)
			.coerceAtMost(MAX_RETRY_MILLIS)
		return maxOf(
			exponential,
			retryAfterMillis ?: 0L,
		).coerceAtMost(MAX_RETRY_MILLIS)
	}
}
