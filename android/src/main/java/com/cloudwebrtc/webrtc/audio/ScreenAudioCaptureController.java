package com.cloudwebrtc.webrtc.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.webrtc.audio.JavaAudioDeviceModule;

import java.nio.ByteBuffer;
import java.lang.reflect.Method;

public class ScreenAudioCaptureController implements JavaAudioDeviceModule.AudioBufferCallback {
    private static final String TAG = "FlutterWebRTCPlugin";
    private static final String SCREEN_AUDIO_DEVICE_ID = "screen_audio";

    private final Object lock = new Object();

    @Nullable
    private JavaAudioDeviceModule audioDeviceModule;
    @Nullable
    private MediaProjection mediaProjection;
    @Nullable
    private AudioRecord audioRecord;

    private boolean active;
    private boolean warnedUnsupported;
    private int currentAudioFormat;
    private int currentChannelCount;
    private int currentSampleRate;
    private int currentBufferSize;

    public void attachAudioDeviceModule(JavaAudioDeviceModule audioDeviceModule) {
        synchronized (lock) {
            this.audioDeviceModule = audioDeviceModule;
        }
    }

    public boolean activate(@Nullable MediaProjection mediaProjection) {
        synchronized (lock) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) {
                warnedUnsupported = false;
                return false;
            }

            this.mediaProjection = mediaProjection;
            this.active = true;
            this.warnedUnsupported = false;
            setUseAudioRecord(false);
            Log.i(TAG, "Playback capture attempted: yes");
            return true;
        }
    }

    public void deactivate() {
        synchronized (lock) {
            active = false;
            mediaProjection = null;
            warnedUnsupported = false;
            releaseAudioRecordLocked();
            setUseAudioRecord(true);
        }
    }

    public boolean isActive() {
        synchronized (lock) {
            return active;
        }
    }

    public static String getDeviceId() {
        return SCREEN_AUDIO_DEVICE_ID;
    }

    @Override
    public long onBuffer(
            ByteBuffer buffer,
            int audioFormat,
            int channelCount,
            int sampleRate,
            int bytesRead,
            long captureTimeNs) {
        AudioRecord record;
        synchronized (lock) {
            if (!active) {
                return captureTimeNs;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) {
                if (!warnedUnsupported) {
                    Log.w(TAG, "Playback capture initialized successfully: no");
                    Log.w(TAG, "Falling back to video-only: Android playback capture requires API 29+");
                    warnedUnsupported = true;
                }
                return captureTimeNs;
            }

            record = ensureAudioRecordLocked(audioFormat, channelCount, sampleRate, bytesRead);
            if (record == null) {
                return captureTimeNs;
            }
        }

        buffer.clear();
        int read = record.read(buffer, bytesRead);
        if (read < 0) {
            Log.w(TAG, "Display audio read failed with code " + read + ", sending silence");
            zeroRemainder(buffer, 0, bytesRead);
            return captureTimeNs;
        }

        if (read < bytesRead) {
            zeroRemainder(buffer, read, bytesRead);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AudioTimestamp audioTimestamp = new AudioTimestamp();
            if (record.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                return audioTimestamp.nanoTime;
            }
        }
        return captureTimeNs;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Nullable
    private AudioRecord ensureAudioRecordLocked(
            int audioFormat, int channelCount, int sampleRate, int bytesRead) {
        if (audioRecord != null
                && audioFormat == currentAudioFormat
                && channelCount == currentChannelCount
                && sampleRate == currentSampleRate
                && bytesRead == currentBufferSize) {
            return audioRecord;
        }

        releaseAudioRecordLocked();

        int channelMask = channelCount > 1 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, audioFormat);
        int bufferSize = Math.max(bytesRead, minBufferSize > 0 ? minBufferSize : bytesRead);

        AudioFormat captureFormat =
                new AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build();

        AudioPlaybackCaptureConfiguration playbackCaptureConfiguration =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

        AudioRecord newRecord =
                new AudioRecord.Builder()
                        .setAudioFormat(captureFormat)
                        .setBufferSizeInBytes(bufferSize)
                        .setAudioPlaybackCaptureConfig(playbackCaptureConfiguration)
                        .build();

        if (newRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "Playback capture initialized successfully: no");
            newRecord.release();
            return null;
        }

        newRecord.startRecording();
        if (newRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            Log.w(TAG, "Playback capture initialized successfully: no");
            newRecord.release();
            return null;
        }

        audioRecord = newRecord;
        currentAudioFormat = audioFormat;
        currentChannelCount = channelCount;
        currentSampleRate = sampleRate;
        currentBufferSize = bytesRead;
        warnedUnsupported = false;
        Log.i(TAG, "Playback capture initialized successfully: yes");
        return audioRecord;
    }

    private void releaseAudioRecordLocked() {
        if (audioRecord == null) {
            return;
        }

        try {
            audioRecord.stop();
        } catch (IllegalStateException e) {
            Log.w(TAG, "Failed to stop playback AudioRecord cleanly", e);
        }
        audioRecord.release();
        audioRecord = null;
        currentAudioFormat = 0;
        currentChannelCount = 0;
        currentSampleRate = 0;
        currentBufferSize = 0;
    }

    private void zeroRemainder(ByteBuffer buffer, int start, int end) {
        for (int i = start; i < end; i++) {
            buffer.put(i, (byte) 0);
        }
    }

    private void setUseAudioRecord(boolean enabled) {
        if (audioDeviceModule == null) {
            return;
        }

        try {
            Object audioInput = audioDeviceModule.audioInput;
            Method method = audioInput.getClass().getDeclaredMethod("setUseAudioRecord", boolean.class);
            method.setAccessible(true);
            method.invoke(audioInput, enabled);
        } catch (Exception e) {
            Log.w(TAG, "Failed to toggle WebRTC audio input mode", e);
        }
    }
}
