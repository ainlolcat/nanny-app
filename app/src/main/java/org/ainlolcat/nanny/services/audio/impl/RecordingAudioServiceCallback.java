package org.ainlolcat.nanny.services.audio.impl;

import android.os.Environment;
import android.util.Log;

import org.ainlolcat.nanny.services.audio.AudioServiceCallback;
import org.ainlolcat.nanny.utils.WavRecorder;

import java.io.File;
import java.io.IOException;

public class RecordingAudioServiceCallback implements AudioServiceCallback {

    private final int sampleRateHz;
    private final int bitsPerSample;
    private final int nChannels;
    private final WavRecorder wavRecorder;
    private final File dir;
    private final long fileRotationPeriod;
    private final String prefix;
    private long lastFileRotation = System.currentTimeMillis();


    public RecordingAudioServiceCallback(int sampleRateHz, int bitsPerSample, int nChannels, long fileRotationPeriod, String prefix) {
        this.sampleRateHz = sampleRateHz;
        this.bitsPerSample = bitsPerSample;
        this.nChannels = nChannels;
        this.fileRotationPeriod = fileRotationPeriod;
        this.prefix = prefix;

        this.wavRecorder = new WavRecorder(sampleRateHz, bitsPerSample, nChannels);
        this.dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
    }

    @Override
    public void onData(byte[] data, int offset, int len) {
        long time = System.currentTimeMillis();
        wavRecorder.write(data, offset, len);
        if (lastFileRotation + fileRotationPeriod < time) {
            File recordFile = new File(dir, prefix + lastFileRotation + ".wav");
            try {
                wavRecorder.writeToFile(recordFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            lastFileRotation = time;
            Log.i("AudioRecordService", "File created " + recordFile);
        }
        lastFileRotation = time;
    }
}
