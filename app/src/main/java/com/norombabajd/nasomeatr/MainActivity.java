package com.norombabajd.nasomeatr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NasomEATR";
    private static final int REQUEST_PERMISSIONS = 200;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private TextView tvStatus;
    private TextView tvSelectedDevice;
    private TextView tvRecordingInfo;
    private Button btnSelectDevice;
    private Button btnStartRecording;
    private Button btnStopRecording;
    private Button btnSplitAudio;
    private Button btnPlayLeft;
    private Button btnPlayRight;

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private File outputFile;
    private File leftChannelFile;
    private File rightChannelFile;
    private AudioDeviceInfo selectedDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configure edge-to-edge layout using ViewCompat
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tv_status), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        requestPermissions();
        setupListeners();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvSelectedDevice = findViewById(R.id.tv_selected_device);
        tvRecordingInfo = findViewById(R.id.tv_recording_info);
        btnSelectDevice = findViewById(R.id.btn_select_device);
        btnStartRecording = findViewById(R.id.btn_start_recording);
        btnStopRecording = findViewById(R.id.btn_stop_recording);
        btnSplitAudio = findViewById(R.id.btn_split_audio);
        btnPlayLeft = findViewById(R.id.btn_play_left);
        btnPlayRight = findViewById(R.id.btn_play_right);
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS
            );
        }
    }

    private void setupListeners() {
        btnSelectDevice.setOnClickListener(v -> showAudioDeviceSelectionDialog());
        btnStartRecording.setOnClickListener(v -> startRecording());
        btnStopRecording.setOnClickListener(v -> stopRecording());
        btnSplitAudio.setOnClickListener(v -> splitStereoAudio());
        btnPlayLeft.setOnClickListener(v -> playAudioFile(leftChannelFile));
        btnPlayRight.setOnClickListener(v -> playAudioFile(rightChannelFile));
    }

    private void showAudioDeviceSelectionDialog() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);

        List<AudioDeviceInfo> usbDevices = new ArrayList<>();
        for (AudioDeviceInfo device : devices) {
            // Filter for USB devices or specifically DJI Mic 2 if possible
            if (device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                usbDevices.add(device);
            }
        }

        if (usbDevices.isEmpty()) {
            Toast.makeText(this, "No USB audio devices found. Please connect your DJI Mic 2.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String[] deviceNames = new String[usbDevices.size()];
        for (int i = 0; i < usbDevices.size(); i++) {
            deviceNames[i] = usbDevices.get(i).getProductName().toString();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Audio Device");
        builder.setItems(deviceNames, (dialog, which) -> {
            selectedDevice = usbDevices.get(which);
            tvSelectedDevice.setText("Selected Device: " + selectedDevice.getProductName());
            btnStartRecording.setEnabled(true);
            Toast.makeText(this, "Selected: " + selectedDevice.getProductName(),
                    Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void startRecording() {
        if (isRecording) {
            return;
        }

        // Create output directory
        File outputDir = new File(getExternalFilesDir(null), "Recordings");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Create output file with timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        outputFile = new File(outputDir, "recording_" + timestamp + ".pcm");

        try {
            // Configure AudioRecord with selected device if available
            if (selectedDevice != null) {
                // For Android versions that support preferred audio devices
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    audioRecord = new AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(CHANNEL_CONFIG)
                                    .setEncoding(AUDIO_FORMAT)
                                    .build())
                            .setBufferSizeInBytes(BUFFER_SIZE)
                            // We cannot specify the exact device on older Android APIs
                            .build();

                    // This is just a note that we selected a specific device, but Android
                    // does not provide direct control over which device to use for input
                    Log.i(TAG, "Selected audio device: " + selectedDevice.getProductName());
                } else {
                    // Fallback for older Android versions
                    audioRecord = new AudioRecord(
                            MediaRecorder.AudioSource.DEFAULT,
                            SAMPLE_RATE,
                            CHANNEL_CONFIG,
                            AUDIO_FORMAT,
                            BUFFER_SIZE);
                }
            } else {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE);
            }

            final DataOutputStream dos = new DataOutputStream(new FileOutputStream(outputFile));

            // Start recording
            audioRecord.startRecording();
            isRecording = true;

            // Update UI
            tvStatus.setText("Status: Recording...");
            btnStartRecording.setEnabled(false);
            btnStopRecording.setEnabled(true);
            btnSelectDevice.setEnabled(false);

            // Start recording thread
            new Thread(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (isRecording) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        try {
                            dos.write(buffer, 0, bytesRead);
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing audio data", e);
                        }
                    }
                }

                // Close file when recording stops
                try {
                    dos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output file", e);
                }
            }).start();

        } catch (IOException e) {
            Log.e(TAG, "Error starting recording", e);
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        // Update UI
        tvStatus.setText("Status: Recording stopped");
        btnStartRecording.setEnabled(true);
        btnStopRecording.setEnabled(false);
        btnSplitAudio.setEnabled(true);
        btnSelectDevice.setEnabled(true);

        String info = "Recording saved to: " + outputFile.getAbsolutePath();
        tvRecordingInfo.setText(info);
        Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
    }

    private void splitStereoAudio() {
        if (outputFile == null || !outputFile.exists()) {
            Toast.makeText(this, "No recording file to split", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("Status: Splitting audio...");
        btnSplitAudio.setEnabled(false);

        new Thread(() -> {
            try {
                // Create output files for left and right channels
                String baseFileName = outputFile.getAbsolutePath().replace(".pcm", "");
                leftChannelFile = new File(baseFileName + "_LEFT.pcm");
                rightChannelFile = new File(baseFileName + "_RIGHT.pcm");

                DataInputStream dis = new DataInputStream(new FileInputStream(outputFile));
                DataOutputStream dosLeft = new DataOutputStream(new FileOutputStream(leftChannelFile));
                DataOutputStream dosRight = new DataOutputStream(new FileOutputStream(rightChannelFile));

                // Each sample in 16-bit PCM is 2 bytes
                // For stereo, we have left channel followed by right channel
                byte[] buffer = new byte[4]; // 2 bytes for left + 2 bytes for right

                while (dis.available() >= 4) {
                    dis.readFully(buffer);

                    // Write left channel (first 2 bytes) to left file
                    dosLeft.write(buffer, 0, 2);

                    // Write right channel (next 2 bytes) to right file
                    dosRight.write(buffer, 2, 2);
                }

                // Close all streams
                dis.close();
                dosLeft.close();
                dosRight.close();

                // Update UI on main thread
                runOnUiThread(() -> {
                    tvStatus.setText("Status: Audio split complete");
                    btnPlayLeft.setEnabled(true);
                    btnPlayRight.setEnabled(true);
                    String info = tvRecordingInfo.getText() + "\n" +
                            "Left channel: " + leftChannelFile.getAbsolutePath() + "\n" +
                            "Right channel: " + rightChannelFile.getAbsolutePath();
                    tvRecordingInfo.setText(info);
                    Toast.makeText(MainActivity.this, "Audio split complete", Toast.LENGTH_SHORT).show();
                    // Call nasalance analysis after UI update
                    analyzeNasalance(leftChannelFile, rightChannelFile);
                });

            } catch (IOException e) {
                Log.e(TAG, "Error splitting audio", e);
                runOnUiThread(() -> {
                    tvStatus.setText("Status: Error splitting audio");
                    btnSplitAudio.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Failed to split audio: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // Analyze nasalance from left and right PCM files
    private void analyzeNasalance(File leftFile, File rightFile) {
        runOnUiThread(() -> tvStatus.setText("Status: Analyzing nasalance..."));

        new Thread(() -> {
            try {
                int totalFrames = 0;
                double sumNasalance = 0;

                FileInputStream leftStream = new FileInputStream(leftFile);
                FileInputStream rightStream = new FileInputStream(rightFile);
                byte[] leftBuffer = new byte[2];
                byte[] rightBuffer = new byte[2];

                while (leftStream.read(leftBuffer) != -1 && rightStream.read(rightBuffer) != -1) {
                    short leftSample = (short)((leftBuffer[1] << 8) | (leftBuffer[0] & 0xFF));
                    short rightSample = (short)((rightBuffer[1] << 8) | (rightBuffer[0] & 0xFF));

                    double nasalEnergy = Math.abs(rightSample);
                    double totalEnergy = Math.abs(leftSample) + nasalEnergy;

                    if (totalEnergy > 0) {
                        double nasalance = (nasalEnergy / totalEnergy) * 100.0;
                        sumNasalance += nasalance;
                        totalFrames++;
                    }
                }

                leftStream.close();
                rightStream.close();

                final double averageNasalance = totalFrames > 0 ? sumNasalance / totalFrames : 0.0;

                // Compute SPL stats for both channels
                double[] leftSplStats = computeSPLStats(leftFile);
                double[] rightSplStats = computeSPLStats(rightFile);

                final double finalNasalance = averageNasalance;
                final String splStats = String.format(Locale.US,
                        "ORAL CHANNEL (LEFT):\n" +
                        "Mean SPL: %.1f dB\nMax SPL: %.1f dB\nMin SPL: %.1f dB\n\n" +
                        "NASAL CHANNEL (RIGHT):\n" +
                        "Mean SPL: %.1f dB\nMax SPL: %.1f dB\nMin SPL: %.1f dB\n\n" +
                        "NASALANCE SCORE: %.1f%%",
                        leftSplStats[0], leftSplStats[1], leftSplStats[2],
                        rightSplStats[0], rightSplStats[1], rightSplStats[2],
                        finalNasalance);

                runOnUiThread(() -> {
                    tvStatus.setText("Status: Analysis complete");
                    tvRecordingInfo.setText(tvRecordingInfo.getText().toString() + "\n\n" + splStats);
                    Toast.makeText(MainActivity.this, "Nasalance: " + String.format(Locale.US, "%.1f%%", finalNasalance), Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                Log.e(TAG, "Error analyzing nasalance", e);
                runOnUiThread(() -> {
                    tvStatus.setText("Status: Error analyzing nasalance");
                    Toast.makeText(MainActivity.this, "Failed to analyze nasalance: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // Helper to compute SPL stats (mean, max, min) for a PCM file
    private double[] computeSPLStats(File pcmFile) throws IOException {
        FileInputStream fis = new FileInputStream(pcmFile);
        List<Double> splValues = new ArrayList<>();
        byte[] buffer = new byte[2];
        double refPressure = 20e-6;

        while (fis.read(buffer) != -1) {
            short sample = (short)((buffer[1] << 8) | (buffer[0] & 0xFF));
            double rms = Math.abs(sample); // For now use peak-like rms
            if (rms > 0) {
                double spl = 20 * Math.log10(rms / refPressure);
                splValues.add(spl);
            }
        }
        fis.close();

        double mean = splValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = splValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = splValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        return new double[]{mean, max, min};
    }

    private void playAudioFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        new Thread(() -> {
            try {
                // Configure audio track for mono playback
                int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelConfig, AUDIO_FORMAT);

                AudioTrack audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(SAMPLE_RATE)
                                .setEncoding(AUDIO_FORMAT)
                                .setChannelMask(channelConfig)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                byte[] buffer = new byte[bufferSize];
                FileInputStream fis = new FileInputStream(file);
                audioTrack.play();

                int bytesRead;
                while ((bytesRead = fis.read(buffer, 0, buffer.length)) != -1) {
                    audioTrack.write(buffer, 0, bytesRead);
                }

                audioTrack.stop();
                audioTrack.release();
                fis.close();

            } catch (IOException e) {
                Log.e(TAG, "Error playing audio", e);
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "Permission denied. The app needs these permissions to work.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}