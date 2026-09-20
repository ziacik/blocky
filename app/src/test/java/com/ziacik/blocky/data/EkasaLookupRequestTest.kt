package com.ziacik.blocky.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EkasaLookupRequestTest {
	@Test
	fun parsesOnlineReceiptId() {
		val request = EkasaLookupRequest.fromQr("O-AC6D5656CDC64336AD5656CDC60336E0")
		assertTrue(request is EkasaLookupRequest.Online)
		assertEquals("O-AC6D5656CDC64336AD5656CDC60336E0", (request as EkasaLookupRequest.Online).receiptId)
	}

	@Test
	fun parsesOfflineQrPayload() {
		val request = EkasaLookupRequest.fromQr(
			"2AC245D5-F486472B-77A6008D-D30C3443-96225EE2:88820233545530002:221122084927:696:68.63"
		) as EkasaLookupRequest.Offline

		assertEquals("22.11.2022 08:49:27", request.issueDateFormatted)
		assertEquals(696L, request.receiptNumber)
		assertEquals("68.63", request.totalAmount.toPlainString())
	}
}
