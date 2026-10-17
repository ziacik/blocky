package com.ziacik.blocky.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptQrSelectorTest {
	@Test
	fun recognizesOnlineEkasaAmongOtherQrCodes() {
		assertEquals(
			"O-AC6D5656CDC64336AD5656CDC60336E0",
			ReceiptQrSelector.select(
				listOf("https://example.com/loyalty", "  o-ac6d5656cdc64336ad5656cdc60336e0  "),
			)?.uppercase(),
		)
	}

	@Test
	fun recognizesOfflineEkasa() {
		val qr = "2AC245D5-F486472B-77A6008D-D30C3443-96225EE2:88820233545530002:221122084927:696:68.63"
		assertEquals(qr, ReceiptQrSelector.select(listOf(null, qr)))
	}

	@Test
	fun ignoresUnsupportedQrCodes() {
		assertNull(ReceiptQrSelector.select(listOf(null, "", "https://example.com", "NOT A RECEIPT")))
	}

	@Test
	fun doesNotAcceptMalformedOfflineQr() {
		assertNull(ReceiptQrSelector.select(listOf("okp:register:not-a-date:3:4.50")))
	}
}
