package org.ainlolcat.nanny.services.control

import android.graphics.Color
import android.util.Log
import androidx.core.util.Consumer
import androidx.core.util.Supplier
import org.ainlolcat.nanny.MainActivity.Companion.BACKGROUND_COLOR_SET_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.BRIGHTNESS_DECREASE_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.BRIGHTNESS_INCREASE_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.CALM_MODE_OFF_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.CALM_MODE_ON_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.CALM_MODE_SENS_DECREASE_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.CALM_MODE_SENS_INCREASE_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.CALM_MODE_SET_SOUND_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.CALM_MODE_VOLUME_DECREASE_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.CALM_MODE_VOLUME_INCREASE_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.DECREASE_SENSITIVITY_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.INCREASE_SENSITIVITY_COMMAND
import org.ainlolcat.nanny.MainActivity.Companion.TAKE_PHOTO_COMMAND
import org.ainlolcat.nanny.settings.NannySettings
import org.ainlolcat.nanny.settings.NannySettingsBuilder
import java.util.concurrent.atomic.AtomicReference

// todo settings can be updated from UI thread and from TG thread. Need sync
class TgController (
    private val settingSupplier: Supplier<NannySettings>,
    private val settingUpdater: Consumer<NannySettings>,
    private val telegramBotServiceSupplier: Supplier<TelegramBotService>,
    private val connectedUserCommands: Supplier<Array<Array<String>>>,
    private val disconnectedUserCommands: Supplier<Array<Array<String>>>,
    private val updateColor: Runnable,
    private val updateBrightness: Runnable,
    private val updateCalmingSound: Runnable,
    private val photoCallback: Consumer<String>,
    ) : TelegramBotCallback {

    private val TAG = "TgController"

    // this code works in assumption that users do not change these settings in parallel
    var backgroundColorSetExpected = AtomicReference<String>()
    var calmModeSetSoundExpected = AtomicReference<String>()

    override fun onChatOpen(username: String?, chatId: String?) {
        settingUpdater.accept(settingSupplier.get())
        telegramBotServiceSupplier.get().sendMessageToChat(
            chatId!!,
            "Welcome to Nanny.",
            connectedUserCommands.get()
        )
    }

    override fun onUnsubscribe(username: String?) {
        val setting = settingSupplier.get()
        val chatId = setting.knownChats.get(username)
        setting.knownChats.remove(username)
        settingUpdater.accept(setting)
        if (chatId != null) {
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId,
                "You will not receive any message. To resume send /start.",
                disconnectedUserCommands.get()
            )
        }
    }

    override fun onMessage(username: String?, chatId: String?, message: String?) {
        if (message.equals(DECREASE_SENSITIVITY_COMMAND)) {
            val setting = settingSupplier.get()
            val newSetting = NannySettingsBuilder(setting).setSoundLevelThreshold(setting.soundLevelThreshold + 1).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New sound alarm level is ${settingSupplier.get().soundLevelThreshold}",
                connectedUserCommands.get()
            )
        }
        if (message.equals(INCREASE_SENSITIVITY_COMMAND)) {
            val setting = settingSupplier.get()
            val newSetting = NannySettingsBuilder(setting).setSoundLevelThreshold(setting.soundLevelThreshold - 1).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New sound alarm level is ${settingSupplier.get().soundLevelThreshold}",
                connectedUserCommands.get()
            )
        }

        if (message.equals(BACKGROUND_COLOR_SET_COMMAND)) {
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "Please write new color. Allowed: #RRGGBB or red, blue, green, black, white, gray, cyan, magenta, yellow, lightgray, darkgray, grey, lightgrey, darkgrey, aqua, fuchsia, lime, maroon, navy, olive, purple, silver, and teal.",
                connectedUserCommands.get()
            )
            backgroundColorSetExpected.set(chatId)
        } else if (chatId.equals(backgroundColorSetExpected.get())) {
            if (message != null && !message.startsWith("/")) {
                try {
                    val newColor = Color.parseColor(message)
                    Log.i(TAG, "Parsed color $newColor")
                    val setting = settingSupplier.get()
                    val newSetting = NannySettingsBuilder(setting)
                        .setBackgroundColor(message)
                        .build()
                    settingUpdater.accept(newSetting)
                    updateColor.run()
                    telegramBotServiceSupplier.get().sendMessageToChat(
                        chatId!!,
                        "New color is ${settingSupplier.get().backgroundColor}",
                        connectedUserCommands.get()
                    )
                } catch (e: java.lang.Exception) {
                    Log.e(TAG, "Cannot set new color", e)
                    telegramBotServiceSupplier.get().sendMessageToChat(
                        chatId!!,
                        "Cannot parse color ${message}. Allowed: #RRGGBB or red, blue, green, black, white, gray, cyan, magenta, yellow, lightgray, darkgray, grey, lightgrey, darkgrey, aqua, fuchsia, lime, maroon, navy, olive, purple, silver, and teal.",
                        connectedUserCommands.get()
                    )
                }
            }
            backgroundColorSetExpected.set(null)
        }
        if (message.equals(BRIGHTNESS_DECREASE_COMMAND)) {
            val setting = settingSupplier.get()
            val newBrightness = Math.max(0, setting.screenBrightness - 10)
            val newSetting = NannySettingsBuilder(setting).setScreenBrightness(newBrightness).build()
            settingUpdater.accept(newSetting)
            updateBrightness.run()
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New screen brightness is ${settingSupplier.get().screenBrightness}",
                connectedUserCommands.get()
            )
        }
        if (message.equals(BRIGHTNESS_INCREASE_COMMAND)) {
            val setting = settingSupplier.get()
            val newBrightness = Math.min(100, setting.screenBrightness + 10)
            val newSetting = NannySettingsBuilder(setting).setScreenBrightness(newBrightness).build()
            settingUpdater.accept(newSetting)
            updateBrightness.run()
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New screen brightness is ${settingSupplier.get().screenBrightness}",
                connectedUserCommands.get()
            )
        }

        if (message.equals(CALM_MODE_SET_SOUND_COMMAND)) {
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "Please send voice message to bot.",
                connectedUserCommands.get()
            )
            calmModeSetSoundExpected.set(chatId)
        } else if (chatId.equals(calmModeSetSoundExpected.get())) {
            calmModeSetSoundExpected.set(null)
        }

        if (message.equals(CALM_MODE_ON_COMMAND)) {
            val setting = settingSupplier.get()
            val newSetting = NannySettingsBuilder(setting).setCalmModeOn(true).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "Calm-mode was enabled with sensitivity ${setting.calmModeSensitivity} and volume ${setting.calmModeVolume}",
                connectedUserCommands.get()
            )
        }
        if (message.equals(CALM_MODE_SENS_DECREASE_COMMAND)) {
            val setting = settingSupplier.get()
            val newSetting = NannySettingsBuilder(setting).setCalmModeSensitivity(setting.calmModeSensitivity + 1).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New calm-mode sensitivity is ${settingSupplier.get().calmModeSensitivity}",
                connectedUserCommands.get()
            )
        }
        if (message.equals(CALM_MODE_SENS_INCREASE_COMMAND)) {
            val setting = settingSupplier.get()
            val newSetting = NannySettingsBuilder(setting).setCalmModeSensitivity(setting.calmModeSensitivity - 1).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New calm-mode sensitivity is ${settingSupplier.get().calmModeSensitivity}",
                connectedUserCommands.get()
            )
        }
        if (message.equals(CALM_MODE_VOLUME_DECREASE_COMMAND)) {
            val setting = settingSupplier.get()
            val newVolume = Math.max(0, setting.calmModeVolume - 10)
            val newSetting = NannySettingsBuilder(setting).setCalmModeVolume(newVolume).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New calm-mode volume is ${settingSupplier.get().calmModeVolume}",
                connectedUserCommands.get()
            )
        }
        if (message.equals(CALM_MODE_VOLUME_INCREASE_COMMAND)) {
            val setting = settingSupplier.get()
            val newVolume = Math.min(100, setting.calmModeVolume + 10)
            val newSetting = NannySettingsBuilder(setting).setCalmModeVolume(newVolume).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "New calm-mode volume is ${settingSupplier.get().calmModeVolume}",
                connectedUserCommands.get()
            )
        }
        if (message.equals(CALM_MODE_OFF_COMMAND)) {
            val setting = settingSupplier.get()
            val newSetting = NannySettingsBuilder(setting).setCalmModeOn(false).build()
            settingUpdater.accept(newSetting)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "Calm-mode was disabled",
                connectedUserCommands.get()
            )
        }

        if (message.equals(TAKE_PHOTO_COMMAND)) {
            photoCallback.accept(chatId!!)
        }
    }

    override fun onVoiceMessage(username: String?, chatId: String?, data: ByteArray?) {
        if (calmModeSetSoundExpected.get() == chatId) {
            val setting = settingSupplier.get()
            val newSetting = NannySettingsBuilder(setting)
                .setCalmModeSound(android.util.Base64.encodeToString(data, android.util.Base64.DEFAULT))
                .build()
            settingUpdater.accept(newSetting)
            updateCalmingSound.run()
            calmModeSetSoundExpected.set(null)
            telegramBotServiceSupplier.get().sendMessageToChat(
                chatId!!,
                "Sound for calm mode was saved",
                connectedUserCommands.get()
            )
        }
    }
}