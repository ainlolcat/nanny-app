package org.ainlolcat.nanny.services.control

import android.util.Log
import com.pengrad.telegrambot.Callback
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPhoto
import com.pengrad.telegrambot.request.SendVoice
import com.pengrad.telegrambot.response.BaseResponse
import java.io.IOException

class TelegramBotService(botToken: String,
                         val allowedUsers: Collection<String>,
                         val knownChats: MutableMap<String, String>,
                         val callback: TelegramBotCallback
) {

    private var client = TelegramBot(botToken)

    fun init() {
        client.setUpdatesListener(
            fun(updates: List<Update?>?): Int {
                if (updates != null) {
                    for (update in updates) {
                        val username = update?.message()?.from()?.username()
                        if (username != null && allowedUsers.contains(username)) {
                            val userId = update.message()?.from()?.id().toString()
                            if (!knownChats.containsKey(username)) {
                                knownChats[username] = userId
                                callback.onChatOpen(username, userId)
                            }
                            val message = update.message().text()
                            if (message.equals("/unsubscribe")) {
                                callback.onUnsubscribe(username)
                            } else {
                                callback.onMessage(username, userId, message)
                            }
                            Log.i("TelegramBotService", "Message from approved user: $update")
                        } else {
                            Log.i("TelegramBotService", "Message from unknown user: $update")
                        }
                    }
                }
                return UpdatesListener.CONFIRMED_UPDATES_ALL
            }, fun(e: TelegramException) {
                if (e.response() != null) {
                    Log.e("TelegramBotService", "Error from telegram with response", e)
                } else {
                    Log.e("TelegramBotService", "Error from telegram", e)
                }
            }
        )
    }

    fun shutdown() {
        client.removeGetUpdatesListener()
        client.shutdown()
    }

    fun sendMessageToChat(chatId:String, text: String, replyMarkup: Array<Array<String>>?) {
        val message = SendMessage(chatId.toLong(), text)
        if (replyMarkup != null) {
            if (replyMarkup.isNotEmpty()) {
                val keyboard = ReplyKeyboardMarkup(*replyMarkup[0])
                for (i in 1 until replyMarkup.size) {
                    keyboard.addRow(*replyMarkup[i])
                }
                message.replyMarkup(keyboard)
            } else {
                message.replyMarkup(ReplyKeyboardRemove())
            }
        }
        execute(message)
    }
    fun sendBroadcastMessage(text: String, replyMarkup: Array<Array<String>>?) {
        for (chatId in knownChats.values) {
            sendMessageToChat(chatId, text, replyMarkup)
        }
    }

    fun sendBroadcastMessage(message: String) {
        for (chatId in knownChats.values) {
            execute(SendMessage(chatId.toLong(), message))
        }
    }

    fun sendBroadcastVoice(windowData: ByteArray) {
        for (chatId in knownChats.values) {
            val audioMessage = SendVoice(chatId.toLong(), windowData)
            execute(audioMessage)
        }
    }

    fun sendImageToChat(chatId:String, imageData: ByteArray) {
        val photoMessage = SendPhoto(chatId.toLong(), imageData)
        execute(photoMessage)
    }


    private fun <T : BaseRequest<T, R>?, R : BaseResponse?> execute(request: T) {
        Log.i("TelegramBotService", "Sending message")
        client.execute(request, object : Callback<T, R> {
            override fun onResponse(request: T, response: R) {
                Log.i("TelegramBotService", "Response received $response")
            }

            override fun onFailure(request: T, e: IOException?) {
                Log.i("TelegramBotService", "Error received: $e")
            }

        })
    }
}