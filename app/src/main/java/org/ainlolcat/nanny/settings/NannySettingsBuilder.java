package org.ainlolcat.nanny.settings;

import android.content.SharedPreferences;

import java.util.Collection;
import java.util.Map;

public class NannySettingsBuilder {
    private String botToken;
    private Collection<String> allowedUsers;
    private Map<String, String> knownChats;
    private int soundLevelThreshold;
    private int alarmCooldownSec;
    private int alarmCooldownOverrideIncreaseThreshold;
    private String backgroundColor;
    private int screenBrightness;
    private boolean calmModeOn = false;
    private String calmModeSound = null;
    private int calmModeSensitivity = 1;
    private int calmModeVolume = 10;

    public NannySettingsBuilder() {
    }

    public NannySettingsBuilder(NannySettings nannySettings) {
        this.botToken = nannySettings.botToken;
        this.allowedUsers = nannySettings.allowedUsers;
        this.knownChats = nannySettings.knownChats;

        this.soundLevelThreshold = nannySettings.soundLevelThreshold;
        this.alarmCooldownSec = nannySettings.alarmCooldownSec;
        this.alarmCooldownOverrideIncreaseThreshold = nannySettings.alarmCooldownOverrideIncreaseThreshold;

        this.backgroundColor = nannySettings.backgroundColor;
        this.screenBrightness = nannySettings.screenBrightness;

        this.calmModeOn = nannySettings.calmModeOn;
        this.calmModeSound = nannySettings.calmModeSound;
        this.calmModeSensitivity = nannySettings.calmModeSensitivity;
        this.calmModeVolume = nannySettings.calmModeVolume;
    }

    public NannySettingsBuilder setBotToken(String botToken) {
        this.botToken = botToken;
        return this;
    }

    public NannySettingsBuilder setAllowedUsers(Collection<String> allowedUsers) {
        this.allowedUsers = allowedUsers;
        return this;
    }

    public NannySettingsBuilder setKnownChats(Map<String, String> knownChats) {
        this.knownChats = knownChats;
        return this;
    }

    public NannySettingsBuilder setSoundLevelThreshold(int soundLevelThreshold) {
        this.soundLevelThreshold = soundLevelThreshold;
        return this;
    }

    public NannySettingsBuilder setAlarmCooldownSec(int alarmCooldownSec) {
        this.alarmCooldownSec = alarmCooldownSec;
        return this;
    }

    public NannySettingsBuilder setAlarmCooldownOverrideIncreaseThreshold(int alarmCooldownOverrideIncreaseThreshold) {
        this.alarmCooldownOverrideIncreaseThreshold = alarmCooldownOverrideIncreaseThreshold;
        return this;
    }

    public NannySettingsBuilder setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public NannySettingsBuilder setScreenBrightness(int screenBrightness) {
        this.screenBrightness = screenBrightness;
        return this;
    }

    public NannySettingsBuilder setCalmModeOn(boolean calmModeOn) {
        this.calmModeOn = calmModeOn;
        return this;
    }

    public NannySettingsBuilder setCalmModeSound(String calmModeSound) {
        this.calmModeSound = calmModeSound;
        return this;
    }

    public NannySettingsBuilder setCalmModeSensitivity(int calmModeSensitivity) {
        this.calmModeSensitivity = calmModeSensitivity;
        return this;
    }

    public NannySettingsBuilder setCalmModeVolume(int calmModeVolume) {
        this.calmModeVolume = calmModeVolume;
        return this;
    }

    public NannySettings build() {
        return new NannySettings(
                botToken, allowedUsers, knownChats,
                soundLevelThreshold, alarmCooldownSec, alarmCooldownOverrideIncreaseThreshold,
                backgroundColor, screenBrightness,
                calmModeOn, calmModeSound, calmModeSensitivity, calmModeVolume);
    }
}