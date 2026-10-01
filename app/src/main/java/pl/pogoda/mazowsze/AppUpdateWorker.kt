package pl.pogoda.mazowsze

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import pl.pogoda.mazowsze.data.AppUpdates
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {
    private const val WORK_NAME = "cirral_release_check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}

class AppUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val updates = AppUpdates(applicationContext)
        return try {
            val snapshot = updates.check()
            val version = snapshot.latestVersion
            if (snapshot.isAvailable && version != null && snapshot.apkUrl != null) {
                updates.download(snapshot)
                if (updates.notificationsEnabled() && !updates.wasNotified(version) && showNotification(version))
                    updates.markNotified(version)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next periodic run will retry; the last successful result stays visible in settings.
            Result.success()
        }
    }

    private fun showNotification(version: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channelId = "cirral_updates"
        manager.createNotificationChannel(NotificationChannel(
            channelId, "Aktualizacje Cirral", NotificationManager.IMPORTANCE_DEFAULT
        ))
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra("open_updates", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Dostępna aktualizacja Cirral")
            .setContentText("Wersja $version jest pobrana. Otwórz ustawienia, aby ją zainstalować")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
        return true
    }
}
