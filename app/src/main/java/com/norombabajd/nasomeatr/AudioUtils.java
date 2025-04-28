package com.norombabajd.nasomeatr;

import android.media.AudioFormat;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class AudioUtils {
    private static final String TAG = "AudioUtils";

    /**
     * Converts a raw PCM file to WAV format
     * @param rawFile The raw PCM file to convert
     * @param sampleRate Sample rate of the audio
     * @param channels Number of channels (1 for mono, 2 for stereo)
     * @return The converted WAV file
     */
    public static File convertPcmToWav(File rawFile, int sampleRate, int channels, int audioFormat) {
        File wavFile = new File(rawFile.getAbsolutePath().replace(".pcm", ".wav"));
        
        try {
            FileInputStream fis = new FileInputStream(rawFile);
            FileOutputStream fos = new FileOutputStream(wavFile);
            
            // WAV header size
            long rawFileSize = rawFile.length();
            int bitsPerSample = (audioFormat == AudioFormat.ENCODING_PCM_16BIT) ? 16 : 8;
            
            // Write WAV header
            writeWavHeader(fos, rawFileSize, sampleRate, channels, bitsPerSample);
            
            // Copy raw PCM data
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            
            fis.close();
            fos.close();
            
            return wavFile;
        } catch (IOException e) {
            Log.e(TAG, "Error converting PCM to WAV", e);
            return null;
        }
    }

    /**
     * Writes a WAV header to the output stream
     */
    private static void writeWavHeader(FileOutputStream fos, long rawFileSize, int sampleRate, 
                                       int channels, int bitsPerSample) throws IOException {
        // RIFF header
        fos.write("RIFF".getBytes()); // ChunkID
        fos.write(intToByteArray((int) (36 + rawFileSize))); // ChunkSize
        fos.write("WAVE".getBytes()); // Format
        
        // fmt subchunk
        fos.write("fmt ".getBytes()); // Subchunk1ID
        fos.write(intToByteArray(16)); // Subchunk1Size (16 for PCM)
        fos.write(shortToByteArray((short) 1)); // AudioFormat (1 for PCM)
        fos.write(shortToByteArray((short) channels)); // NumChannels
        fos.write(intToByteArray(sampleRate)); // SampleRate
        fos.write(intToByteArray(sampleRate * channels * bitsPerSample / 8)); // ByteRate
        fos.write(shortToByteArray((short) (channels * bitsPerSample / 8))); // BlockAlign
        fos.write(shortToByteArray((short) bitsPerSample)); // BitsPerSample
        
        // data subchunk
        fos.write("data".getBytes()); // Subchunk2ID
        fos.write(intToByteArray((int) rawFileSize)); // Subchunk2Size
    }

    /**
     * Splits a stereo PCM file into separate left and right channel files
     */
    public static void splitStereoToMono(File stereoFile, File leftFile, File rightFile) throws IOException {
        DataInputStream dis = new DataInputStream(new FileInputStream(stereoFile));
        DataOutputStream dosLeft = new DataOutputStream(new FileOutputStream(leftFile));
        DataOutputStream dosRight = new DataOutputStream(new FileOutputStream(rightFile));
        
        // For 16-bit PCM stereo: 
        // - Each sample is 2 bytes (16 bits)
        // - Left and right channels alternate
        byte[] buffer = new byte[4]; // 2 bytes for left, 2 bytes for right
        
        while (dis.available() >= 4) {
            // Read 4 bytes (one complete stereo sample)
            dis.readFully(buffer);
            
            // First 2 bytes are the left channel
            dosLeft.write(buffer, 0, 2);
            
            // Last 2 bytes are the right channel
            dosRight.write(buffer, 2, 2);
        }
        
        dis.close();
        dosLeft.close();
        dosRight.close();
    }

    /**
     * Calculates the RMS value of an audio buffer (useful for level metering)
     */
    public static double calculateRms(byte[] audioBuffer, int offset, int length) {
        double sum = 0;
        // Convert bytes to shorts
        ShortBuffer shortBuffer = ByteBuffer.wrap(audioBuffer, offset, length)
                .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        
        for (int i = 0; i < shortBuffer.capacity(); i++) {
            short sample = shortBuffer.get(i);
            sum += sample * sample;
        }
        
        double rms = Math.sqrt(sum / shortBuffer.capacity());
        return rms;
    }

    /**
     * Converts an integer to a byte array in little endian format
     */
    private static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    /**
     * Converts a short to a byte array in little endian format
     */
    private static byte[] shortToByteArray(short value) {
        return new byte[] {
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }
}