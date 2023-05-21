package org.ainlolcat.nanny

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.PowerManager.WakeLock
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import org.ainlolcat.nanny.R
import org.ainlolcat.nanny.databinding.ActivityMainBinding
import org.ainlolcat.nanny.services.PermissionHungryService
import org.ainlolcat.nanny.services.audio.AudioDetectionService
import org.ainlolcat.nanny.services.audio.impl.AudioRecordService
import org.ainlolcat.nanny.services.control.TelegramBotCallback
import org.ainlolcat.nanny.services.control.TelegramBotService
import org.ainlolcat.nanny.settings.NannySettings
import org.ainlolcat.nanny.utils.WavRecorder
import com.google.android.material.snackbar.Snackbar
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
        arrayOf("/unsubscribe")
    )
    private val disconnectedUserCommands = arrayOf(arrayOf("/start"))

    var detectionService: AudioDetectionService? = null
    var telegramBotService: TelegramBotService? = null
    var wl: WakeLock? = null
    var setting: NannySettings? = null
    val executor: ExecutorService = Executors.newFixedThreadPool(3)

    @SuppressLint("WakelockTimeout")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("MainActivity", "Starting main activity")
        detectionService = AudioRecordService()
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
        println(detectionService)
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
        val windowData = ByteArray(sampleRateHz * 5)
        val windowOffset = AtomicInteger(0)
        val timer = AtomicLong(System.currentTimeMillis())
        detectionService.addCallback { data, offset, length ->
            val currentTime = System.currentTimeMillis()
            if (windowOffset.get() + length <= windowData.size) {
                // append data to windowData
                System.arraycopy(data, offset, windowData, windowOffset.get(), length)
                windowOffset.addAndGet(length)
            } else {
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

                if (setting != null && avg > setting!!.soundLevelThreshold) {
                    telegramBotService?.sendSoundThresholdAlarm(avg)

                    val wavRecorder = WavRecorder(sampleRateHz, 8, 1)
                    wavRecorder.write(windowData, 0, windowOffset.get())

                    telegramBotService?.sendBroadcastVoice(wavRecorder.writeToArray())
                }

                windowOffset.set(0)
                timer.set(currentTime)
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
        if (useTelegram && setting?.botToken?.isNotBlank() == true) {
            telegramBotService = TelegramBotService(
                setting!!.botToken,
                setting!!.allowedUsers,
                setting!!.knownChats,
                object : TelegramBotCallback {
                    override fun onChatOpen(username: String?, chatId: String?) {
                        // todo settings can be updated from UI thread and from TG thread. Need sync.
                        setting!!.storeSettings(preferences)
                        telegramBotService!!.sendMessageToChat(
                            chatId!!,
                            "Welcome to Nanny.",
                            connectedUserCommands
                        )
                    }

                    override fun onUnsubscribe(username: String?) {
                        // todo settings can be updated from UI thread and from TG thread. Need sync.
                        var chatId = setting!!.knownChats.get(username)
                        setting!!.knownChats.remove(username)
                        setting!!.storeSettings(preferences)
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
                            val newSetting = NannySettings(setting!!.botToken, setting!!.allowedUsers, setting!!.knownChats, setting!!.soundLevelThreshold + 1)
                            setting = newSetting
                            setting!!.storeSettings(preferences)
                            telegramBotService!!.sendMessageToChat(
                                chatId!!,
                                "New sound alarm level is ${setting!!.soundLevelThreshold}",
                                connectedUserCommands
                            )
                        }
                        if (message.equals("/lower")) {
                            // todo settings can be updated from UI thread and from TG thread. Need sync.
                            val newSetting = NannySettings(setting!!.botToken, setting!!.allowedUsers, setting!!.knownChats, setting!!.soundLevelThreshold - 1)
                            setting = newSetting
                            setting!!.storeSettings(preferences)
                            telegramBotService!!.sendMessageToChat(
                                chatId!!,
                                "New sound alarm level is ${setting!!.soundLevelThreshold}",
                                connectedUserCommands
                            )
                        }
                    }
                }
            )
            telegramBotService!!.init()
        }
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