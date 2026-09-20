package com.ziacik.blocky.data

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed interface EkasaLookupRequest {
	data class Online(val receiptId: String) : EkasaLookupRequest
	data class Offline(
		val okp: String,
		val cashRegisterCode: String,
		val issueDateFormatted: String,
		val receiptNumber: Long,
		val totalAmount: BigDecimal,
	) : EkasaLookupRequest

	companion object {
		private val onlinePattern = Regex("^[A-Z]-[0-9A-F]{32}$", RegexOption.IGNORE_CASE)
		private val offlineDate = DateTimeFormatter.ofPattern("yyMMddHHmmss")
		private val apiDate = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

		fun fromQr(raw: String): EkasaLookupRequest {
			val value = raw.trim()
			if (onlinePattern.matches(value)) {
				return Online(value.uppercase())
			}

			val parts = value.split(':')
			require(parts.size == 5) { "QR kód nemá podporovaný formát eKasa." }
			val date = LocalDateTime.parse(parts[2], offlineDate)
			return Offline(
				okp = parts[0],
				cashRegisterCode = parts[1],
				issueDateFormatted = date.format(apiDate),
				receiptNumber = parts[3].toLong(),
				totalAmount = parts[4].toBigDecimal(),
			)
		}
	}
}
