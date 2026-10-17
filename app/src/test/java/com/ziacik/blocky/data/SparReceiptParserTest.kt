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
