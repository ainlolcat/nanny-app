package org.ainlolcat.nanny

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager.WakeLock
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.google.android.material.snackbar.Snackbar
import org.ainlolcat.nanny.databinding.ActivityMainBinding
import org.ainlolcat.nanny.services.AlarmingService
import org.ainlolcat.nanny.services.CalmingService
import org.ainlolcat.nanny.services.CameraService
import org.ainlolcat.nanny.services.PermissionHungryService
import org.ainlolcat.nanny.services.audio.AudioDetectionService
import org.ainlolcat.nanny.services.audio.impl.AudioRecordService
import org.ainlolcat.nanny.services.control.TelegramBotService
import org.ainlolcat.nanny.services.control.TgController
import org.ainlolcat.nanny.settings.NannySettings
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    companion object {
        val INCREASE_SENSITIVITY_COMMAND = "/sensitivity increase"
        val DECREASE_SENSITIVITY_COMMAND = "/sensitivity decrease"

        val BACKGROUND_COLOR_SET_COMMAND = "/background color"
        val BRIGHTNESS_INCREASE_COMMAND = "/brightness increase"
        val BRIGHTNESS_DECREASE_COMMAND = "/brightness decrease"

        val CALM_MODE_ON_COMMAND = "/calm-mode on"
        val CALM_MODE_SET_SOUND_COMMAND = "/calm-mode set sound"
        val CALM_MODE_OFF_COMMAND = "/calm-mode off"
        val CALM_MODE_SENS_INCREASE_COMMAND = "/calm-mode sensitivity increase"
        val CALM_MODE_SENS_DECREASE_COMMAND = "/calm-mode sensitivity decrease"
        val CALM_MODE_VOLUME_INCREASE_COMMAND = "/calm-mode volume increase"
        val CALM_MODE_VOLUME_DECREASE_COMMAND = "/calm-mode volume decrease"

        val TAKE_PHOTO_COMMAND = "/photo"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    var backgroundColorView: View? = null

    // hardcoded settings allows to turn off TG support for dev runs.
    // remaining function will be audio service which will show current noise level
    private val useTelegram = true
    private val sampleRateHz = 44100
    private val windowSizeSec = 5

    private val connectedUserCommands: Array<Array<String>>
        get() {
            val result = ArrayList<Array<String>>()
            result.add(arrayOf(TAKE_PHOTO_COMMAND))
            result.add(
                arrayOf(
                    INCREASE_SENSITIVITY_COMMAND, DECREASE_SENSITIVITY_COMMAND
                )
            )
            result.add(
                arrayOf(
                    BACKGROUND_COLOR_SET_COMMAND,
                    BRIGHTNESS_INCREASE_COMMAND,
                    BRIGHTNESS_DECREASE_COMMAND
                )
            )
            if (setting.calmModeOn) {
                result.add(arrayOf(CALM_MODE_OFF_COMMAND, CALM_MODE_SET_SOUND_COMMAND))
                result.add(
                    arrayOf(
                        CALM_MODE_SENS_INCREASE_COMMAND,
                        CALM_MODE_SENS_DECREASE_COMMAND,
                    )
                )
                result.add(
                    arrayOf(
                        CALM_MODE_VOLUME_INCREASE_COMMAND, CALM_MODE_VOLUME_DECREASE_COMMAND
                    )
                )
            } else if (setting.calmModeSound != null) {
                result.add(arrayOf(CALM_MODE_ON_COMMAND, CALM_MODE_SET_SOUND_COMMAND))
            } else {
                result.add(arrayOf(CALM_MODE_SET_SOUND_COMMAND))
            }
            result.add(arrayOf("/unsubscribe"))
            return result.toTypedArray()
        }
    private val disconnectedUserCommands = arrayOf(arrayOf("/start"))

    private lateinit var detectionService: AudioDetectionService
    private lateinit var cameraService: CameraService
    private lateinit var calmingService: CalmingService
    private lateinit var alarmingService: AlarmingService

    @Volatile
    var telegramBotService: TelegramBotService? = null
    var wl: WakeLock? = null

    @Volatile
    lateinit var setting: NannySettings
    val executor: ExecutorService = Executors.newFixedThreadPool(3)

    @SuppressLint("WakelockTimeout")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Starting main activity")
        detectionService = AudioRecordService()
        cameraService = CameraService(this, this::runOnUiThread, executor, { this.telegramBotService })
        val grants: MutableSet<String> = HashSet()
        grants.addAll((detectionService as PermissionHungryService).requiredPermissions.keys)
        grants.addAll(cameraService.requiredPermissions.keys)
        cameraService.ensurePermission(this, grants, PermissionHungryService.PERMISSION_COUNTER.incrementAndGet())

        initAndApplySettings()

        calmingService = CalmingService({ this.setting }, this, windowSizeSec)
        alarmingService = AlarmingService({ this.setting },
            { this.telegramBotService },
            { this.connectedUserCommands },
            sampleRateHz
        )

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
        view: View, activity: MainActivity, button: Button, avgText: TextView, maxText: TextView
    ) {
        Log.i(TAG, "Start Nanny's switch init")
        if (detectionService is PermissionHungryService) {
            button.isEnabled =
                (detectionService as PermissionHungryService).ensurePermission(activity)
        }
        button.setOnClickListener {
            val detectionService = this.detectionService
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
            telegramBotService?.sendBroadcastMessage(
                "Nanny is starting with sound alarm level ${setting.soundLevelThreshold}",
                connectedUserCommands
            )
        }

        val samplesSize = sampleRateHz * windowSizeSec
        val windowData = ByteArray(samplesSize)
        val windowOffset = AtomicInteger(0)
        val lastCheckTime = AtomicLong(System.currentTimeMillis() + windowSizeSec * 1000)

        detectionService.addCallback { data, offset, length ->
            val currentTime = System.currentTimeMillis()
            // append data to windowData
            if (windowOffset.get() + length <= windowData.size) {
                System.arraycopy(data, offset, windowData, windowOffset.get(), length)
                windowOffset.addAndGet(length)
            } else {
                System.arraycopy(data, offset, windowData, 0, length)
                windowOffset.set(length)
            }
            // such analyse will most likely trigger two messages with different noise level on same sample (t and t + 1 sec)
            // so need to filter signal like |_____/``| and trigger only if wave hits start of the window.
            // there are two mechanics:
            // * alarmStartedAt allows to delay send up to 2 seconds
            // * alarmValue will force send if sound level decreases (but still above threshold)
            if (currentTime > lastCheckTime.get() + 1000L) {
                lastCheckTime.set(currentTime)
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

                if (calmingService.canStartCalmingSound() && avg > setting.calmModeSensitivity) {
                    calmingService.startCalmingSound()
                }

                alarmingService.sendAlarmIfNeeded(avg, windowData, windowOffset)
            }
        }
    }

    private fun stopDetectionService(
        detectionService: AudioDetectionService, button: Button?, view: View?
    ) {
        detectionService.stop()
        detectionService.removeCallbacks()

        if (button != null) button.text = getString(R.string.start)

        if (view != null) Snackbar.make(view, "Stopping audio service", Snackbar.LENGTH_LONG)
            .setAction("Action", null).show()

        executor.execute {
            telegramBotService?.sendBroadcastMessage("Nanny is shutting down")
        }

        if (wl?.isHeld == true) {
            wl!!.release()
        } else if (wl == null) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun stopDetectionServiceIfRunning(button: Button?, view: View?) {
        Log.i(TAG, "Stopping Audio service")
        if (detectionService.isRunning()) {
            stopDetectionService(detectionService, button, view)
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

        setBrightness()

        if (useTelegram && setting.botToken?.isNotBlank() == true) {
            telegramBotService = TelegramBotService(setting.botToken,
                setting.allowedUsers,
                setting.knownChats,
                TgController({ this.setting },
                    { newSetting -> this.setting = newSetting; setting.storeSettings(preferences) },
                    { this.telegramBotService },
                    { this.connectedUserCommands },
                    { this.disconnectedUserCommands },
                    {
                        runOnUiThread {
                            this.backgroundColorView?.setBackgroundColor(Color.parseColor(this.setting.backgroundColor))
                        }
                    },
                    { runOnUiThread { setBrightness() } },
                    { calmingService.reinitCalmingSound() },
                    { chatId ->
                        if (detectionService.isRunning()) {
                            if (chatId != null) {
                                cameraService.takePicture(chatId)
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
                    }))
            telegramBotService!!.init()
        }
    }

    private fun setBrightness() {
        val lp = window.attributes
        lp.screenBrightness = (setting.screenBrightness / 100.0).toFloat()
        window.attributes = lp
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
        stopDetectionServiceIfRunning(findViewById(R.id.button_start_initial), null)
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "onRestart")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
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
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(
            TAG,
            "For code $requestCode result is ${grantResults.contentToString()} for ${permissions.contentToString()}"
        )
        if (detectionService is PermissionHungryService) {
            val button = findViewById<Button>(R.id.button_start_initial) // binding.fab
            if (button != null) {
                button.isEnabled =
                    (detectionService as PermissionHungryService).ensurePermission(this)
            }
        }
    }

}