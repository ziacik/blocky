package com.ziacik.blocky.data.wolt

object WoltSyncPlanner {
	fun shouldFetchDetail(hasPricedReceipt: Boolean): Boolean = !hasPricedReceipt
}
