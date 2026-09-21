package com.ziacik.blocky.data.wolt

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class WoltSyncWorker(
	appContext: Context,
	params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
	override suspend fun doWork() = withContext(Dispatchers.IO) {
		val sessionStore = WoltSessionStore(applicationContext)
		if (!sessionStore.isConnected()) {
			return@withContext Result.success()
		}

		try {
			val imported = WoltSyncService(applicationContext).sync()
			Result.success(workDataOf("imported" to imported))
		} catch (_: WoltAuthException) {
			Result.failure()
		} catch (_: IOException) {
			Result.retry()
		} catch (_: Exception) {
			Result.failure()
		}
	}
}
