package org.ainlolcat.nanny

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.PowerManager.WakeLock
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import org.ainlolcat.nanny.databinding.ActivityMainBinding
import org.ainlolcat.nanny.services.PermissionHungryService
import org.ainlolcat.nanny.services.audio.AudioDetectionService
import org.ainlolcat.nanny.services.audio.impl.AudioRecordService
import org.ainlolcat.nanny.services.control.TelegramBotCallback
import org.ainlolcat.nanny.services.control.TelegramBotService
import org.ainlolcat.nanny.settings.NannySettings
import org.ainlolcat.nanny.utils.WavRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs


class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    // hardcoded settings allows to turn off TG support for dev runs.
    // remaining function will be audio service which will show current noise level
    val useTelegram = true

    private val connectedUserCommands = arrayOf(
        arrayOf("/louder", "/lower"),
        arrayOf("/photo"),
        arrayOf("/unsubscribe")
    )
    private val disconnectedUserCommands = arrayOf(arrayOf("/start"))

    var detectionService: AudioDetectionService? = null
    var telegramBotService: TelegramBotService? = null
    var wl: WakeLock? = null
    lateinit var setting: NannySettings
    val executor: ExecutorService = Executors.newFixedThreadPool(3)

    @SuppressLint("WakelockTimeout")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("MainActivity", "Starting main activity")
        detectionService = AudioRecordService()
        (detectionService as PermissionHungryService).ensurePermission(this, "android.permission.CAMERA", PermissionHungryService.PERMISSION_COUNTER.incrementAndGet())

        initAndApplySettings()

        // wake lock prevents phone from sleep but it starts sending garbage instead of audio
//        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
//        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nanny:service-lock")


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        setSupportActionBar(binding.toolbar)
//        val navController = findNavController(R.id.nav_host_fragment_content_main)
//        appBarConfiguration = AppBarConfiguration(navController.graph)
//        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    fun initNannySwitch(
        view: View,
        activity: MainActivity,
        button: Button,
        avgText: TextView,
        maxText: TextView
    ) {
        Log.i("MainActivity", "Start Nanny's switch init")
        if (detectionService is PermissionHungryService) {
            button.isEnabled = (detectionService as PermissionHungryService).ensurePermission(activity)
        }
        button.setOnClickListener {
            val detectionService = this.detectionService!!
            if (detectionService.isRunning()) {
                stopDetectionService(detectionService, button, view)
            } else {
                startDetectionService(detectionService, button, view, activity, avgText, maxText)
            }
        }
    }

    private fun startDetectionService(
        detectionService: AudioDetectionService,
        button: Button,
        view: View,
        activity: MainActivity,
        avgText: TextView,
        maxText: TextView
    ) {
        if (wl != null) {
            wl!!.acquire()
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        detectionService.start()
        button.text = getString(R.string.stop)
        Snackbar.make(view, "Starting audio service", Snackbar.LENGTH_LONG)
            .setAction("Action", null).show()
        executor.execute {
            telegramBotService?.sendBroadcastMessage("Nanny is starting", connectedUserCommands)
        }

        val sampleRateHz = 44100
        val windowSizeSec = 5
        val samplesSize = sampleRateHz * windowSizeSec
        val windowData = ByteArray(samplesSize)
        val windowOffset = AtomicInteger(0)
//        val lastCheckTime = AtomicLong(System.currentTimeMillis() + windowSizeSec * 1000)
        val lastAlarmTime = AtomicLong(0)
        val lastAlarmValue = AtomicInteger(0)
        detectionService.addCallback { data, offset, length ->
            val currentTime = System.currentTimeMillis()
            // append data to windowData
            if (windowOffset.get() + length <= windowData.size) {
                System.arraycopy(data, offset, windowData, windowOffset.get(), length)
                windowOffset.addAndGet(length)
            } else {
//                System.arraycopy(data, offset, windowData, 0, length)
//                windowOffset.set(length)
//            }
//            // such analyse will most likely trigger two messages with different noise level on same sample (t and t + 1 sec)
//            // so need to filter signal like |_____/``| and trigger only if wave hits start of the window.
//            // todo dont forget to remove windowOffset.set(0)
//            if (currentTime > lastCheckTime.get() + 1000L) {
//                lastCheckTime.set(currentTime)
                // process data
                var commonMagnitude = 0
                var max = 0
                for (i in 0 until windowOffset.get()) {
                    val amp: Int = abs((windowData[i].toInt() and 0xff) - 127)
                    commonMagnitude += amp
                    if (amp > max) {
                        max = amp
                    }
                }
                val avg = Math.round(100.0 * commonMagnitude / windowOffset.get()) / 100.0

                activity.runOnUiThread {
                    avgText.text = "$avg"
                    maxText.text = "$max"
                }

                if (avg > setting.soundLevelThreshold) {
                    val lastAvg = lastAlarmValue.get() / 100.0
                    Log.i("MainActivity", "Start processing sound with avg $avg, last time ${Date(lastAlarmTime.get())}, last avg $lastAvg")
                    if (lastAlarmTime.get() + 1000 * setting.alarmCooldownSec < currentTime ||
                        lastAvg + setting.alarmCooldownOverrideIncreaseThreshold < avg) {
                        lastAlarmTime.set(currentTime)
                        lastAlarmValue.set((100 * avg).toInt())
                        telegramBotService?.sendBroadcastMessage(
                            "Alarm: threshold was reached. Current value: $avg",
                            connectedUserCommands
                        )

                        val wavRecorder = WavRecorder(sampleRateHz, 8, 1)
                        val currentOffset = windowOffset.get()
                        Log.i("MainActivity", "Offset $currentOffset, window data size: ${windowData.size}")
                        if (currentOffset < windowData.size)
                            wavRecorder.write(windowData, currentOffset, windowData.size - currentOffset)
                        if (currentOffset > 0)
                            wavRecorder.write(windowData, 0, currentOffset)

                        telegramBotService?.sendBroadcastVoice(wavRecorder.writeToArray())
                    } else {
                        Log.i("MainActivity", "Skip alarm with avg $avg, last time ${Date(lastAlarmTime.get())}, last avg $lastAvg")
                    }
                }

                windowOffset.set(0)
            }
        }
    }

    private fun stopDetectionService(
        detectionService: AudioDetectionService,
        button: Button?,
        view: View?
    ) {
        detectionService.stop()
        detectionService.removeCallbacks()

        if (button!=null)
            button.text = getString(R.string.start)

        if (view!=null)
            Snackbar.make(view, "Stopping audio service", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

        executor.execute {
            telegramBotService?.sendBroadcastMessage("Nanny is shutting down")
        }

        if (wl?.isHeld == true) {
            wl!!.release()
        } else if (wl == null) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    fun stopDetectionServiceIfRunning(button: Button?, view: View?) {
        Log.i("MainActivity", "Stopping Audio service")
        if (detectionService?.isRunning() == true) {
            stopDetectionService(detectionService!!, button, view)
        }
    }

    fun initAndApplySettings() {
        // todo skip reinit if token/chats are the same
        val existingBot = telegramBotService
        if (existingBot != null) {
            executor.execute {
                existingBot.shutdown()
            }
        }
        val preferences = getSharedPreferences("org.ainlolcat.nanny.app", MODE_PRIVATE)
        setting = NannySettings(preferences)

        val lp = window.attributes
        lp.screenBrightness = (setting.screenBrightness / 100.0).toFloat()
        window.attributes = lp

        if (useTelegram && setting.botToken?.isNotBlank() == true) {
            telegramBotService = TelegramBotService(
                setting.botToken,
                setting.allowedUsers,
                setting.knownChats,
                object : TelegramBotCallback {
                    override fun onChatOpen(username: String?, chatId: String?) {
                        // todo settings can be updated from UI thread and from TG thread. Need sync.
                        setting.storeSettings(preferences)
                        telegramBotService!!.sendMessageToChat(
                            chatId!!,
                            "Welcome to Nanny.",
                            connectedUserCommands
                        )
                    }

                    override fun onUnsubscribe(username: String?) {
                        // todo settings can be updated from UI thread and from TG thread. Need sync.
                        var chatId = setting.knownChats.get(username)
                        setting.knownChats.remove(username)
                        setting.storeSettings(preferences)
                        if (chatId != null) {
                            telegramBotService!!.sendMessageToChat(
                                chatId,
                                "You will not receive any message. To resume send /start.",
                                disconnectedUserCommands
                            )
                        }
                    }

                    override fun onMessage(username: String?, chatId: String?, message: String?) {
                        if (message.equals("/louder")) {
                            // todo settings can be updated from UI thread and from TG thread. Need sync.
                            val newSetting = NannySettings(setting.botToken, setting.allowedUsers, setting.knownChats,
                                setting.soundLevelThreshold + 1, setting.alarmCooldownSec, setting.alarmCooldownOverrideIncreaseThreshold,
                                setting.backgroundColor, setting.screenBrightness
                            )
                            setting = newSetting
                            setting.storeSettings(preferences)
                            telegramBotService!!.sendMessageToChat(
                                chatId!!,
                                "New sound alarm level is ${setting.soundLevelThreshold}",
                                connectedUserCommands
                            )
                        }
                        if (message.equals("/lower")) {
                            // todo settings can be updated from UI thread and from TG thread. Need sync.
                            val newSetting = NannySettings(setting.botToken, setting.allowedUsers, setting.knownChats,
                                setting.soundLevelThreshold - 1, setting.alarmCooldownSec, setting.alarmCooldownOverrideIncreaseThreshold,
                                setting.backgroundColor, setting.screenBrightness)
                            setting = newSetting
                            setting.storeSettings(preferences)
                            telegramBotService!!.sendMessageToChat(
                                chatId!!,
                                "New sound alarm level is ${setting.soundLevelThreshold}",
                                connectedUserCommands
                            )
                        }
                        if (message.equals("/photo")) {
                            if (detectionService?.isRunning() == true) {
                                if (chatId != null) {
                                    takePictureX(chatId)
                                }
                            } else {
                                if (chatId != null) {
                                    telegramBotService!!.sendMessageToChat(
                                        chatId,
                                        "You need to start nanny to take photos.",
                                        connectedUserCommands
                                    )
                                }
                            }
                        }
                    }
                }
            )
            telegramBotService!!.init()
        }
    }

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraInitializationFailed = false;
    private fun takePictureX(chatId: String) {
        runOnUiThread {
            takePictureXUiRunnable(chatId)
        }
    }

    private fun takePictureXUiRunnable(chatId: String) {
        // this init will work because only one thread access cameraProviderFuture/imageCapture
        // pay attention to any changes here
        if (cameraProviderFuture == null && !cameraInitializationFailed) {
            try {
                cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Could not init ProcessCameraProvider", e)
                cameraInitializationFailed = true
                telegramBotService?.sendBroadcastMessage("Could not init camera")
            }
        }
        cameraProviderFuture!!.addListener({
            runOnUiThread {
                val provider = try {
                    cameraProviderFuture!!.get()

                } catch (e: java.lang.Exception) {
                    Log.e("MainActivity", "Could not get ProcessCameraProvider from future", e)
                    cameraInitializationFailed = true
                    telegramBotService?.sendBroadcastMessage("Could not init camera")
                    null
                }
                if (provider != null) {
                    takePictureXCameraProviderListenerUiRunnable(provider, chatId)
                }
            }
        }, executor)
    }

    private fun takePictureXCameraProviderListenerUiRunnable(
        cameraProvider: ProcessCameraProvider,
        chatId: String
    ) {
        val hasCamera = try {
            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        } catch (e: CameraInfoUnavailableException) {
            Log.i("MainActivity", "Cannot check camera availability", e)
            false
        }
        if (!hasCamera) {
            cameraInitializationFailed = true
            executor.execute {
                telegramBotService?.sendBroadcastMessage("Sorry, this phone does not have front camera")
            }
            return
        }
        if (imageCapture == null) {
            imageCapture = ImageCapture
                .Builder()
                .setTargetResolution(Size(1920, 1080))
                .build()
        }
        if (camera == null) {
            camera = cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageCapture
            )
        }
        imageCapture!!.takePicture(executor,
            object : ImageCapture.OnImageCapturedCallback() {

                override fun onError(error: ImageCaptureException) {
                    Log.i("MainActivity", "Got error", error)
                }

                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    Log.i(
                        "MainActivity",
                        "Got image $imageProxy of size ${imageProxy.height}x${imageProxy.width}"
                    )
                    val planeProxy = imageProxy.planes[0]
                    val buffer: ByteBuffer = planeProxy.buffer
                    val bytes = ByteArray(buffer.remaining())
                    val stream = ByteArrayOutputStream()
                    buffer.get(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        .compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    val data = stream.toByteArray()
                    Log.i("MainActivity", "Schedule sending image of size ${data.size}")
                    executor.execute {
                        Log.i("MainActivity", "Start sending image of size ${data.size}")
                        telegramBotService?.sendImageToChat(chatId, data)
                    }
                    imageProxy.close()
                }
            })
    }

    override fun onStop() {
        super.onStop()
        Log.i("MainActivity", "onStop")
        stopDetectionServiceIfRunning(findViewById(R.id.button_start_initial), null)
    }

    override fun onRestart() {
        super.onRestart()
        Log.i("MainActivity", "onRestart")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "onDestroy")
        executor.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i("MainActivity", "For code $requestCode result is ${grantResults.contentToString()} for ${permissions.contentToString()}")
        if (detectionService is PermissionHungryService) {
            val button = findViewById<Button>(R.id.button_start_initial) // binding.fab
            if (button != null) {
                button.isEnabled = (detectionService as PermissionHungryService).ensurePermission(this)
            }
        }
    }

}