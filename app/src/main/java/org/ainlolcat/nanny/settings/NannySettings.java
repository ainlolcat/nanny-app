package org.ainlolcat.nanny.settings;

import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class NannySettings {
    public static String  TG_BOT_SECRET_KEY = "TG_BOT_SECRET_KEY";
    public static String  ALLOWED_USERS_KEY = "ALLOWED_USERS_KEY";
    public static String  KNOWN_CHATS_WITH_ALLOWED_USERS_KEY = "KNOWN_CHATS_WITH_ALLOWED_USERS_KEY";
    public static String  SOUND_LEVEL_THRESHOLD_KEY = "SOUND_LEVEL_THRESHOLD_KEY";

    public final String botToken;
    public final Collection<String> allowedUsers;
    public final Map<String, String> knownChats;
    public final int soundLevelThreshold;

    public NannySettings(SharedPreferences preferences) {
        botToken = preferences.getString(TG_BOT_SECRET_KEY, null);
        allowedUsers = preferences.getStringSet(ALLOWED_USERS_KEY, new HashSet<>());
        knownChats = new HashMap<>(preferences.getStringSet(KNOWN_CHATS_WITH_ALLOWED_USERS_KEY, new HashSet<>())
                .stream()
                .map(x -> x.split(":"))
                .collect(Collectors.toMap((x -> x[0]),(x -> x[1])))
        );
        soundLevelThreshold = preferences.getInt(SOUND_LEVEL_THRESHOLD_KEY, 5);
    }

    public NannySettings(String botToken, Collection<String> allowedUsers, Map<String, String> knownChats, int soundLevelThreshold) {
        this.botToken = botToken;
        this.allowedUsers = allowedUsers;
        this.knownChats = knownChats;
        this.soundLevelThreshold = soundLevelThreshold;
    }

    public void storeSettings(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(TG_BOT_SECRET_KEY, botToken);
        editor.putStringSet(ALLOWED_USERS_KEY, new HashSet<>(allowedUsers));
        editor.putStringSet(KNOWN_CHATS_WITH_ALLOWED_USERS_KEY, knownChats.entrySet().stream().map(e -> e.getKey()+":"+e.getValue()).collect(Collectors.toSet()));
        editor.putInt(SOUND_LEVEL_THRESHOLD_KEY, soundLevelThreshold);
        editor.apply();
    }

    @Override
    public String toString() {
        return "NannySettings{" +
                "botToken='" + botToken + '\'' +
                ", allowedUsers=" + allowedUsers +
                ", knownChatsSet=" + knownChats +
                ", soundLevelThreshold=" + soundLevelThreshold +
                '}';
    }
}
