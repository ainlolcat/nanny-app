package org.ainlolcat.nanny.services.audio.impl;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;

import org.ainlolcat.nanny.services.PermissionHungryService;
import org.ainlolcat.nanny.services.audio.AudioDetectionService;
import org.ainlolcat.nanny.services.audio.AudioServiceCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class AudioRecordService implements AudioDetectionService, PermissionHungryService {

    private final int sampleRateHz = 44100;
    private final int windowSize = 2048;
    private volatile boolean processingIsRunning = false;

    private final List<AudioServiceCallback> callbackList = new CopyOnWriteArrayList<>();

    @Override
    public Map<String, Integer> getRequiredPermissions() {
        Map<String, Integer> permissions = new HashMap<>();
        permissions.put(Manifest.permission.RECORD_AUDIO, PERMISSION_COUNTER.getAndIncrement());
//        permissions.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_COUNTER.getAndIncrement());
        return permissions;
    }

    @Override
    public boolean isRunning() {
        return processingIsRunning;
    }

    @Override
    public void start() {
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            runAnalyze();
        }).start();
    }

    private void runAnalyze() {
        AudioRecord record = null;
        try {
            record = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRateHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_8BIT,
                    AudioRecord.getMinBufferSize(
                            sampleRateHz,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_8BIT
                    )
            );
            record.startRecording();
            processingIsRunning = true;
            byte[] buffer = new byte[windowSize];

            while (processingIsRunning && !Thread.interrupted()) {
                int numSamples = record.read(buffer, 0, buffer.length);
                if (numSamples == -1) {
                    Log.i("AudioRecordService", "No more data.");
                    break;
                }
                callbackList.forEach(c -> c.onData(buffer, 0, numSamples));
            }
        } catch (SecurityException e) {
            Log.e("AudioRecordService", "Failed to perform analyze", e);
        } catch (Exception e) {
            Log.e("AudioRecordService", "Failed to perform analyze", e);
        } finally {
            processingIsRunning = false;
            if (record != null) {
                record.stop();
                record.release();
            }
        }
    }

    @Override
    public void stop() {
        processingIsRunning = false;
    }

    @Override
    public void addCallback(@NonNull AudioServiceCallback audioServiceCallback) {
        callbackList.add(audioServiceCallback);
    }

    @Override
    public void removeCallbacks() {
        callbackList.clear();
    }
}
