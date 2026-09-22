package com.ziacik.blocky.categorization

object ExpenseTaxonomy {
	val categories: Map<String, Set<String>> = linkedMapOf(
		"Potraviny" to setOf(
			"Pečivo",
			"Mäso",
			"Ryby a morské plody",
			"Údeniny a lahôdky",
			"Mliečne výrobky a vajcia",
			"Ovocie",
			"Zelenina",
			"Trvanlivé potraviny",
			"Sladkosti",
			"Slané snacky",
			"Nealkoholické nápoje",
			"Alkohol",
			"Hotové jedlá",
			"Mrazené potraviny",
			"Dochucovadlá a prísady",
			"Iné potraviny",
		),
		"Drogéria" to setOf(
			"Osobná hygiena",
			"Kozmetika",
			"Starostlivosť o vlasy",
			"Ústna hygiena",
			"Čistenie domácnosti",
			"Pranie",
			"Papierový sortiment",
			"Detská starostlivosť",
			"Iná drogéria",
		),
		"Domácnosť" to setOf(
			"Kuchyňa",
			"Vybavenie domácnosti",
			"Údržba a opravy",
			"Dekorácie",
			"Záhrada",
			"Domáce zvieratá",
			"Iné do domácnosti",
		),
		"Oblečenie a obuv" to setOf("Oblečenie", "Obuv", "Doplnky"),
		"Elektronika" to setOf("Zariadenia", "Príslušenstvo", "Spotrebný materiál"),
		"Zdravie" to setOf("Lieky", "Zdravotnícke potreby", "Výživové doplnky"),
		"Deti" to setOf("Škola", "Hračky", "Detské oblečenie", "Voľný čas"),
		"Stravovanie mimo domu" to setOf("Reštaurácie", "Fast food", "Donáška", "Kaviarne a cukrárne"),
		"Zábava a voľný čas" to setOf("Kultúra", "Šport", "Hry a hobby", "Predplatné"),
		"Doprava" to setOf("MHD a verejná doprava", "Taxi", "Cestovanie"),
		"Auto" to setOf("Palivo", "Servis a údržba", "Parkovanie", "Diaľničné poplatky"),
		"Bývanie" to setOf("Energie", "Nájom a poplatky", "Vybavenie bývania"),
		"Služby" to setOf("Telekomunikácie", "Poistenie", "Finančné služby", "Osobné služby", "Iné služby"),
		"Ostatné" to setOf("Ostatné"),
	)

	fun requireValid(category: String, subcategory: String?) {
		val allowed = categories[category]
			?: throw IllegalArgumentException("Unknown category: $category")
		if (subcategory != null && subcategory !in allowed) {
			throw IllegalArgumentException("Unknown subcategory '$subcategory' for '$category'")
		}
	}
}
