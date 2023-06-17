package org.ainlolcat.nanny.services

import android.util.Log
import androidx.core.util.Supplier
import org.ainlolcat.nanny.services.control.TelegramBotService
import org.ainlolcat.nanny.settings.NannySettings
import org.ainlolcat.nanny.utils.WavRecorder
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AlarmingService(
    private val settingsSupplier: Supplier<NannySettings?>,
    private val botSupplier: Supplier<TelegramBotService?>,
    private val connectedUserCommands: Supplier<Array<Array<String>>>,
    private val sampleRateHz: Int
) {
    private val TAG = "AlarmingService"

    private val alarmValue: AtomicInteger = AtomicInteger(0)
    private val alarmStartedAt: AtomicLong = AtomicLong(0)
    private val lastAlarmValue: AtomicInteger = AtomicInteger(0)
    private val lastAlarmTime: AtomicLong = AtomicLong(0)

    fun sendAlarmIfNeeded(avg: Double, windowData: ByteArray, windowOffset: AtomicInteger) {
        val currentTime = System.currentTimeMillis()
        val setting = settingsSupplier.get()!!
        val bot = botSupplier.get()
        if (avg > setting.soundLevelThreshold) {
            val currentAlarmValue = alarmValue.get() / 100.0
            if (alarmStartedAt.get() + 2000L > currentTime || avg < currentAlarmValue) { // give 2 sec or if sound is lowering
                val lastAvg = lastAlarmValue.get() / 100.0
                Log.i(
                    TAG,
                    "Start processing sound with avg $avg, last time ${Date(lastAlarmTime.get())}, last avg $lastAvg"
                )
                val canThrowEvent =
                    (currentTime - lastAlarmTime.get() > 5000L) && // hard limit on message freq
                            (lastAlarmTime.get() + 1000 * setting.alarmCooldownSec < currentTime || // user defined limit on freq
                                    lastAvg + setting.alarmCooldownOverrideIncreaseThreshold < avg)  // user defined cooldown override on extreame sound increase
                if (canThrowEvent) {
                    lastAlarmTime.set(currentTime)
                    lastAlarmValue.set((100 * avg).toInt())
                    bot?.sendBroadcastMessage(
                        "Alarm: threshold was reached. Current value: $avg",
                        connectedUserCommands.get()
                    )

                    val wavRecorder = WavRecorder(sampleRateHz, 8, 1)
                    val currentOffset = windowOffset.get()
                    Log.i(
                        TAG,
                        "Offset $currentOffset, window data size: ${windowData.size}"
                    )
                    if (currentOffset < windowData.size)
                        wavRecorder.write(
                            windowData,
                            currentOffset,
                            windowData.size - currentOffset
                        )
                    if (currentOffset > 0)
                        wavRecorder.write(windowData, 0, currentOffset)

                    bot?.sendBroadcastVoice(wavRecorder.writeToArray())
                } else {
                    Log.i(
                        TAG,
                        "Skip alarm with avg $avg, last time ${Date(lastAlarmTime.get())}, last avg $lastAvg"
                    )
                }
                alarmStartedAt.set(0)
                alarmValue.set(0)
            } else {
                Log.i(TAG, "Skip alarm with avg $avg as first encounter")
                if (alarmStartedAt.get() == 0L) {
                    alarmStartedAt.set(currentTime)
                }
                alarmValue.set(Math.max(alarmValue.get(), (100 * avg).toInt()))
            }
        } else {
            alarmStartedAt.set(0)
            alarmValue.set(0)
        }
    }
}