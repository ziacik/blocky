package com.ziacik.blocky.normalization

import java.text.Normalizer
import java.util.Locale

interface ItemNormalizer {
	fun normalize(rawName: String): NormalizedItem
}

data class NormalizedItem(
	val canonicalName: String,
	val category: String,
	val subcategory: String? = null,
)

class HeuristicItemNormalizer : ItemNormalizer {
	override fun normalize(rawName: String): NormalizedItem {
		val key = normalizationKey(rawName)
		return rules.firstOrNull { rule -> rule.tokens.any(key::contains) }
			?.let { NormalizedItem(it.name, it.category, it.subcategory) }
			?: NormalizedItem(cleanDisplayName(rawName), "Nezaradené")
	}

	private fun cleanDisplayName(value: String): String = value
		.trim()
		.lowercase(Locale.forLanguageTag("sk"))
		.split(Regex("\\s+"))
		.joinToString(" ") { token -> token.replaceFirstChar { it.titlecase(Locale.forLanguageTag("sk")) } }

	companion object {
		fun normalizationKey(value: String): String {
			val ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replace(Regex("\\p{M}+"), "")
				.uppercase(Locale.ROOT)
			return ascii
				.replace(Regex("\\b\\d+(?:[.,]\\d+)?\\s*(G|KG|ML|CL|DL|L|KS)\\b"), " ")
				.replace(Regex("[^A-Z0-9]+"), " ")
				.trim()
				.replace(Regex("\\s+"), " ")
		}

		private val rules = listOf(
			Rule(listOf("ROZOK", "CHLIEB", "BAGETA", "KAISER"), "Pečivo", "Potraviny", "Pečivo"),
			Rule(listOf("JOGURT"), "Jogurt", "Potraviny", "Mliečne výrobky"),
			Rule(listOf("EIDAM"), "Eidam", "Potraviny", "Mliečne výrobky"),
			Rule(listOf("PARENICK"), "Parenica", "Potraviny", "Mliečne výrobky"),
			Rule(listOf("MLIEKO"), "Mlieko", "Potraviny", "Mliečne výrobky"),
			Rule(listOf("JABLK"), "Jablká", "Potraviny", "Ovocie a zelenina"),
			Rule(listOf("BANAN"), "Banány", "Potraviny", "Ovocie a zelenina"),
			Rule(listOf("OCOT"), "Ocot", "Potraviny", "Trvanlivé potraviny"),
			Rule(listOf("SUSIEN", "SUS "), "Sušienky", "Potraviny", "Sladkosti"),
			Rule(listOf("CAJ ", "CAJ"), "Čaj", "Potraviny", "Nápoje"),
			Rule(listOf("HUBKA"), "Hubka", "Domácnosť", "Čistenie"),
			Rule(listOf("SAMPON", "MYDLO", "SPRCHOVY"), "Hygiena", "Drogéria", "Osobná hygiena"),
		)
	}

	private data class Rule(
		val tokens: List<String>,
		val name: String,
		val category: String,
		val subcategory: String,
	)
}
