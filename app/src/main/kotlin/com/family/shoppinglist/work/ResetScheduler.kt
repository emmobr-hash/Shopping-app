package com.family.shoppinglist.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.family.shoppinglist.ShoppingApp
import com.family.shoppinglist.core.WeeklySchedule
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ResetScheduler {
    /** Sunday, 06:00 local time. */
    val schedule = WeeklySchedule()

    /**
     * Queues the run for the next Sunday morning. The work is named after its target date, so calling this
     * repeatedly (app start, and again when the worker runs) never duplicates or cancels a run in progress.
     * It waits for a network connection because the list lives in the cloud.
     */
    fun scheduleNext(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val next = schedule.nextAfter(now)
        val request = OneTimeWorkRequestBuilder<WeeklyResetWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("weekly-reset-${next.toLocalDate()}", ExistingWorkPolicy.KEEP, request)
    }
}

class WeeklyResetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ResetScheduler.scheduleNext(applicationContext) // line up next week's run first, so a failure can't break the chain
        return try {
            (applicationContext as ShoppingApp).currentRepository()?.runMaintenance()
            Result.success()
        } catch (e: Exception) {
            Log.e("WeeklyResetWorker", "Weekly maintenance failed, will retry", e)
            Result.retry()
        }
    }
}
