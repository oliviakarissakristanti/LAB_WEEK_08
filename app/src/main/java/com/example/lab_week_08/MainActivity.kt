package com.example.lab_week_08

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.lab_week_08.worker.FirstWorker
import com.example.lab_week_08.worker.SecondWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val workManager by lazy { WorkManager.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Permission POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val id = "001"
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val firstRequest = OneTimeWorkRequest.Builder(FirstWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(FirstWorker.INPUT_DATA_ID, id))
            .build()

        val secondRequest = OneTimeWorkRequest.Builder(SecondWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(SecondWorker.INPUT_DATA_ID, id))
            .build()

        val thirdRequest = OneTimeWorkRequest.Builder(ThirdWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(ThirdWorker.INPUT_DATA_ID, id))
            .build()

        // Urutan proses sesuai assignment:
        // FirstWorker -> SecondWorker -> NotificationService -> ThirdWorker -> SecondNotificationService
        // Kita akan enqueue firstRequest then secondRequest, dan di observer secondRequest kita start NotificationService;
        // setelah NotificationService selesai (trackingCompletion LiveData), kita enqueue thirdRequest, dan setelah thirdRequest selesai kita start SecondNotificationService.

        workManager.beginWith(firstRequest)
            .then(secondRequest)
            .enqueue()

        workManager.getWorkInfoByIdLiveData(firstRequest.id).observe(this) { info ->
            if (info.state.isFinished) {
                showResult("First process is done")
            }
        }

        workManager.getWorkInfoByIdLiveData(secondRequest.id).observe(this) { info ->
            if (info.state.isFinished) {
                showResult("Second process is done")
                launchNotificationService()
            }
        }

        // Observasi completion dari NotificationService (LiveData)
        NotificationService.trackingCompletion.observe(this) { channelId ->
            showResult("Process for Notification Channel ID $channelId is done!")
            // setelah NotificationService selesai, jalankan third worker (enqueue)
            workManager.enqueue(thirdRequest)
        }

        workManager.getWorkInfoByIdLiveData(thirdRequest.id).observe(this) { info ->
            if (info.state.isFinished) {
                showResult("Third process is done")
                // panggil second notification service
                launchSecondNotificationService()
            }
        }
    }

    private fun getIdInputData(idKey: String, idValue: String) = Data.Builder()
        .putString(idKey, idValue)
        .build()

    private fun showResult(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun launchNotificationService() {
        NotificationService.trackingCompletion.observe(this) { Id ->
            // handled above
        }
        val serviceIntent = Intent(this, NotificationService::class.java).apply {
            putExtra(NotificationService.EXTRA_ID, "001")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun launchSecondNotificationService() {
        SecondNotificationService.trackingCompletion.observe(this) { Id ->
            // optional: show toast
            showResult("SecondNotificationService finished for ID $Id")
        }
        val serviceIntent = Intent(this, SecondNotificationService::class.java).apply {
            putExtra(SecondNotificationService.EXTRA_ID, "002")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    companion object {
        const val EXTRA_ID = "Id"
    }

    // helper (simple implementasi)
    private fun enableEdgeToEdge() {
        // kalau punya helper di modul, panggil; jika tidak, bisa kosong
    }
}
