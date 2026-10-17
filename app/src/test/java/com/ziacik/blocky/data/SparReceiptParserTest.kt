package com.ziacik.blocky.data

import com.ziacik.blocky.normalization.HeuristicItemNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SparReceiptParserTest {
	private val parser = SparReceiptParser(HeuristicItemNormalizer())

	@Test
	fun importsItemsAndDiscountsFromSparDigitalReceipt() {
		val text = """
			SPAR
			Der österreichische Supermarkt
			in Ihrer Nähe
			------------------------
			EUR
			SPAR NATUR PUR BIO EIER 3,59
			App-Joker 25% -0,90
			TAFELSCHOKOLADE 1,19
			App-Gutschein -0,60
			APFELSAFT 1,39
			GEMÜSEMIX DOSE 0,79
			TOASTSCHINKEN 1,19
			SUMME EUR 6,65
			Gegeben BAR 50,00
			Rückgeld 43,35
			20.09.2026 13:41:05
			USt% Netto Steuer Brutto
		""".trimIndent()

		val receipt = parser.parse(text)

		assertEquals("SPAR", receipt.merchant)
		assertEquals(665L, receipt.totalCents)
		assertEquals(7, receipt.items.size)
		assertEquals(-90L, receipt.items[1].totalCents)
		assertEquals("App-Joker 25%", receipt.items[1].originalName)
		assertEquals(-60L, receipt.items[3].totalCents)
		assertEquals(665L, receipt.items.sumOf { it.totalCents })
		assertEquals("Nezaradené", receipt.items.first().category)
		assertEquals(
			LocalDateTime.of(2026, 9, 20, 13, 41, 5)
				.atZone(ZoneId.of("Europe/Vienna")).toInstant().toEpochMilli(),
			receipt.issuedAt,
		)
		assertTrue(receipt.id.startsWith("spar:"))
		assertTrue(receipt.rawJson.contains("\"source\":\"spar\""))
		assertEquals(receipt.id, parser.parse(text).id)
	}

	@Test
	fun acceptsEuroSparAndPriceWithVatSuffix() {
		val receipt = parser.parse("""
			EUROSPAR
			EUR
			SEMMEL 0,99 A
			MILCH 1,29 B
			SUMME 2,28
			19.10.2026 17:20
		""".trimIndent())
		assertEquals("EUROSPAR", receipt.merchant)
		assertEquals(228L, receipt.totalCents)
		assertEquals(2, receipt.items.size)
	}

	@Test
	fun parsesActualSparPdfLayoutWithFooterBrandAndMultipleQuantities() {
		// Sanitized example preserving the structure of a real SPAR PDF export:
		// logo is graphical (not extractable), cashier time differs from the purchase time.
		val text = """
			Eisenstädter Straße 29
			2421 Kittsee
			Ihr Einkauf am 17.10.2026 um 15:27 Uhr
			--------------------------------------------
			                                      EUR
			ARTIKEL B                           9,99 B
			GENUSS CRACKER
			  2    x   0,99                      1,98 A
			TRINKWASSER 0,75L                    1,39 B
			  PFAND EINWEG    0,25               0,25 E
			MILCH 1L                             1,89 K
			  PFANDFLASCHE                       0,21 K
			SEMMEL 60G
			  4    x   0,23                      0,92 K
			TEE                                  1,59 K
			--------------------------------------------
			SUMME:                             18,22
			ZAHLUNG VISA                       18,22
			17.10.2026                        15:29:14
			XXXX XXXX XXXX 0000
			123456789012345678901234567890
			Registrierkassensicherheitsverordnung
			https://rksv.spar.at/example
			Vielen Dank für Ihren Einkauf bei
			SPAR
		""".trimIndent()

		val receipt = parser.parse(text)

		assertEquals("SPAR", receipt.merchant)
		assertEquals(1822L, receipt.totalCents)
		assertEquals(8, receipt.items.size)
		assertEquals("GENUSS CRACKER", receipt.items[1].originalName)
		assertEquals(2.0, receipt.items[1].quantity, 0.001)
		assertEquals(198L, receipt.items[1].totalCents)
		assertEquals("PFAND EINWEG", receipt.items[3].originalName)
		assertEquals(25L, receipt.items[3].totalCents)
		assertEquals("SEMMEL 60G", receipt.items[6].originalName)
		assertEquals(4.0, receipt.items[6].quantity, 0.001)
		assertEquals("spar:123456789012345678901234567890", receipt.id)
		assertEquals(
			LocalDateTime.of(2026, 10, 17, 15, 27)
				.atZone(ZoneId.of("Europe/Vienna")).toInstant().toEpochMilli(),
			receipt.issuedAt,
		)
		assertTrue(!receipt.rawJson.contains("XXXX XXXX"))
		assertEquals(1822L, receipt.items.sumOf { it.totalCents })
	}

	@Test
	fun rejectsSumMismatchWithRealSparFooter() {
		assertInvalid("""
			Ihr Einkauf am 17.10.2026 um 15:27 Uhr
			EUR
			BROT 1,29 A
			SUMME: 1,39
			Vielen Dank für Ihren Einkauf bei
			SPAR
		""")
	}

	@Test
	fun rejectsNonSparReceipt() {
		assertInvalid("""
			OTHER MARKET
			BROT 1,29
			SUMME 1,29
			19.10.2026 17:20
		""")
	}

	@Test
	fun rejectsWrongSumRatherThanSilentlyDroppingItems() {
		assertInvalid("""
			SPAR
			EUR
			BROT 1,29
			SUMME EUR 2,29
			19.10.2026 17:20
		""")
	}

	@Test
	fun rejectsReceiptWithoutDateOrItems() {
		assertInvalid("SPAR\nEIER 1,99\nSUMME 1,99")
		assertInvalid("SPAR\nSUMME 3,40\n19.10.2026 17:20")
	}

	private fun assertInvalid(text: String) {
		try {
			parser.parse(text.trimIndent())
			fail("Expected invalid SPAR receipt to fail")
		} catch (_: IllegalArgumentException) {
			// Expected. Never persist a partial receipt.
		}
	}
}
