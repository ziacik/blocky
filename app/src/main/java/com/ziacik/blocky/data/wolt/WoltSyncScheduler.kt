package com.ziacik.blocky.data.wolt

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WoltSyncScheduler {
	private val networkConstraint = Constraints.Builder()
		.setRequiredNetworkType(NetworkType.CONNECTED)
		.build()

	fun disable(context: Context) {
		val workManager = WorkManager.getInstance(context.applicationContext)
		workManager.cancelUniqueWork(PERIODIC_WORK)
		workManager.cancelUniqueWork(IMMEDIATE_WORK)
	}

	fun schedulePeriodic(context: Context) {
		val request = PeriodicWorkRequestBuilder<WoltSyncWorker>(6, TimeUnit.HOURS)
			.setConstraints(networkConstraint)
			.build()
		WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
			PERIODIC_WORK,
			ExistingPeriodicWorkPolicy.KEEP,
			request,
		)
	}

	fun syncNow(context: Context) {
		val request = OneTimeWorkRequestBuilder<WoltSyncWorker>()
			.setConstraints(networkConstraint)
			.build()
		WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
			IMMEDIATE_WORK,
			ExistingWorkPolicy.REPLACE,
			request,
		)
	}

	private const val PERIODIC_WORK = "wolt-periodic-sync"
	private const val IMMEDIATE_WORK = "wolt-immediate-sync"
}
