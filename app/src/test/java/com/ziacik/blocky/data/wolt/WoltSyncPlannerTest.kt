package com.ziacik.blocky.data.wolt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WoltSyncPlannerTest {
	@Test
	fun fetchesDetailWhenReceiptHasNoPricedItems() {
		assertTrue(WoltSyncPlanner.shouldFetchDetail(hasPricedReceipt = false))
	}

	@Test
	fun skipsDetailAlreadyImportedWithPrices() {
		assertFalse(WoltSyncPlanner.shouldFetchDetail(hasPricedReceipt = true))
	}
}
