package org.ainlolcat.nanny.settings;

import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class NannySettings {
    public static final String  TG_BOT_SECRET_KEY = "TG_BOT_SECRET_KEY";
    public static final String  ALLOWED_USERS_KEY = "ALLOWED_USERS_KEY";
    public static final String  KNOWN_CHATS_WITH_ALLOWED_USERS_KEY = "KNOWN_CHATS_WITH_ALLOWED_USERS_KEY";

    public static final String  SOUND_LEVEL_THRESHOLD_KEY = "SOUND_LEVEL_THRESHOLD_KEY";
    public static final String ALARM_COOLDOWN_SEC_KEY = "ALARM_COOLDOWN_SEC_KEY";
    public static final String ALARM_COOLDOWN_OVERRIDE_INCREASE_THRESHOLD_KEY = "ALARM_COOLDOWN_OVERRIDE_INCREASE_THRESHOLD_KEY";

    public static final String  BACKGROUND_COLOR_KEY = "BACKGROUND_COLOR_KEY";
    public static final String  SCREEN_BRIGHTNESS_KEY = "SCREEN_BRIGHTNESS_KEY";

    public static final String  CALM_MODE_ENABLED_KEY = "CALM_MODE_ENABLED_KEY";
    public static final String  CALM_MODE_SOUND_KEY = "CALM_MODE_SOUND_KEY";
    public static final String  CALM_MODE_SENSITIVITY_KEY = "CALM_MODE_SENSITIVITY_KEY";
    public static final String  CALM_MODE_VOLUME_KEY = "CALM_MODE_VOLUME_KEY";

    public final String botToken;
    public final Collection<String> allowedUsers;
    public final Map<String, String> knownChats;

    public final int soundLevelThreshold;
    public final int alarmCooldownSec;
    public final int alarmCooldownOverrideIncreaseThreshold;

    public final String backgroundColor;
    public final int screenBrightness;

    public final boolean calmModeOn;
    public final String calmModeSound;
    public final int calmModeSensitivity;
    public final int calmModeVolume;

    public NannySettings(SharedPreferences preferences) {
        botToken = preferences.getString(TG_BOT_SECRET_KEY, null);
        allowedUsers = preferences.getStringSet(ALLOWED_USERS_KEY, new HashSet<>());
        knownChats = new HashMap<>(preferences.getStringSet(KNOWN_CHATS_WITH_ALLOWED_USERS_KEY, new HashSet<>())
                .stream()
                .map(x -> x.split(":"))
                .collect(Collectors.toMap((x -> x[0]),(x -> x[1])))
        );

        soundLevelThreshold = preferences.getInt(SOUND_LEVEL_THRESHOLD_KEY, 5);
        alarmCooldownSec = preferences.getInt(ALARM_COOLDOWN_SEC_KEY, 60);
        alarmCooldownOverrideIncreaseThreshold = preferences.getInt(ALARM_COOLDOWN_OVERRIDE_INCREASE_THRESHOLD_KEY, 1);

        // should not bother new installation but old one can have value with default ""
        backgroundColor = preferences.getString(BACKGROUND_COLOR_KEY, "white").isEmpty() ? "white" :
                preferences.getString(BACKGROUND_COLOR_KEY, "white");
        screenBrightness = preferences.getInt(SCREEN_BRIGHTNESS_KEY, 60);

        calmModeOn = preferences.getBoolean(CALM_MODE_ENABLED_KEY, false);
        calmModeSound = preferences.getString(CALM_MODE_SOUND_KEY, null);
        calmModeSensitivity = preferences.getInt(CALM_MODE_SENSITIVITY_KEY, 1);
        calmModeVolume = preferences.getInt(CALM_MODE_VOLUME_KEY, 10);
    }

    public NannySettings(String botToken, Collection<String> allowedUsers, Map<String, String> knownChats,
                         int soundLevelThreshold, int alarmCooldownSec, int alarmCooldownOverrideIncreaseThreshold,
                         String backgroundColor, int screenBrightness,
                         boolean calmModeOn, String calmModeSound, int calmModeSensitivity, int calmModeVolume) {
        this.botToken = botToken;
        this.allowedUsers = allowedUsers;
        this.knownChats = knownChats;

        this.soundLevelThreshold = soundLevelThreshold;
        this.alarmCooldownSec = alarmCooldownSec;
        this.alarmCooldownOverrideIncreaseThreshold = alarmCooldownOverrideIncreaseThreshold;

        this.backgroundColor = backgroundColor;
        this.screenBrightness = screenBrightness;

        this.calmModeOn = calmModeOn;
        this.calmModeSound = calmModeSound;
        this.calmModeSensitivity = calmModeSensitivity;
        this.calmModeVolume = calmModeVolume;
    }

    public void storeSettings(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(TG_BOT_SECRET_KEY, botToken);
        editor.putStringSet(ALLOWED_USERS_KEY, new HashSet<>(allowedUsers));
        editor.putStringSet(KNOWN_CHATS_WITH_ALLOWED_USERS_KEY, knownChats.entrySet().stream().map(e -> e.getKey()+":"+e.getValue()).collect(Collectors.toSet()));

        editor.putInt(SOUND_LEVEL_THRESHOLD_KEY, soundLevelThreshold);
        editor.putInt(ALARM_COOLDOWN_SEC_KEY, alarmCooldownSec);
        editor.putInt(ALARM_COOLDOWN_OVERRIDE_INCREASE_THRESHOLD_KEY, alarmCooldownOverrideIncreaseThreshold);

        editor.putString(BACKGROUND_COLOR_KEY, backgroundColor);
        editor.putInt(SCREEN_BRIGHTNESS_KEY, screenBrightness);

        editor.putBoolean(CALM_MODE_ENABLED_KEY, calmModeOn);
        editor.putString(CALM_MODE_SOUND_KEY, calmModeSound);
        editor.putInt(CALM_MODE_SENSITIVITY_KEY, calmModeSensitivity);
        editor.putInt(CALM_MODE_VOLUME_KEY, calmModeVolume);

        editor.apply();
    }

    @Override
    public String toString() {
        return "NannySettings{" +
                "botToken='" + botToken + '\'' +
                ", allowedUsers=" + allowedUsers +
                ", knownChats=" + knownChats +
                ", soundLevelThreshold=" + soundLevelThreshold +
                ", alarmCooldownSec=" + alarmCooldownSec +
                ", alarmCooldownOverrideIncreaseThreshold=" + alarmCooldownOverrideIncreaseThreshold +
                ", backgroundColor='" + backgroundColor + '\'' +
                ", screenBrightness=" + screenBrightness +
                ", calmModeOn=" + calmModeOn +
                ", calmModeSound='" + calmModeSound + '\'' +
                ", calmModeSensitivity=" + calmModeSensitivity +
                ", calmModeVolume=" + calmModeVolume +
                '}';
    }
}
