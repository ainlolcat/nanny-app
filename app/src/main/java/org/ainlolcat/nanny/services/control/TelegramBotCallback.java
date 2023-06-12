package org.ainlolcat.nanny.services.control;

public interface TelegramBotCallback {
    void onChatOpen(String username, String chatId);
    void onUnsubscribe(String username);

    void onMessage(String username, String chatId, String message);
    void onVoiceMessage(String username, String chatId, byte[] data);
}
