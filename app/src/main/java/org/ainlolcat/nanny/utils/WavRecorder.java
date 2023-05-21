package org.ainlolcat.nanny.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class WavRecorder {
    private final ByteArrayOutputStream baos;
    private final int bitsPerSample;

    public WavRecorder(int sampleRate, int bitsPerSample, int nChannels) {
        this.bitsPerSample = bitsPerSample;
        this.baos = new ByteArrayOutputStream();
        writeHeader(sampleRate, bitsPerSample, nChannels);
    }

    public void write(byte[] audioData, int offset, int length) {
        if (bitsPerSample != 8) {
            throw new IllegalStateException("AudioRecorder uses byte[] for 8bit PCM. Implement and use short[].");
        }
        baos.write(audioData, offset, length);
    }

    public void writeToFile(File file) throws IOException {
        try (OutputStream os = new FileOutputStream(file)){
            byte[] data = baos.toByteArray();
            baos.reset();
            updateHeader(data);
            os.write(data);
            os.flush();
        }
    }

    public byte[] writeToArray() {
        byte[] data = baos.toByteArray();
        baos.reset();
        updateHeader(data);
        return data;
    }

    private void writeHeader(int sampleRate, int bitsPerSample, int nChannels) {
        writeStr("RIFF");
        writeInt(0); // size of whole file (data + header)
        writeStr("WAVE");

        writeStr("fmt ");
        writeInt(16); // format data length
        writeShort(1); // format - PCM
        writeShort(nChannels);
        writeInt(sampleRate);
        writeInt(nChannels * sampleRate * bitsPerSample);
        writeShort(nChannels * bitsPerSample / 8);
        writeShort(bitsPerSample);

        writeStr("data");
        writeInt(0); // size of data
    }

    private void updateHeader(byte[] data) {
        int fileSize = data.length;
        int dataSize = fileSize - 44;

        data[4] = ((byte) (fileSize & 0xFF)); fileSize >>= 8;
        data[5] = ((byte) (fileSize & 0xFF)); fileSize >>= 8;
        data[6] = ((byte) (fileSize & 0xFF)); fileSize >>= 8;
        data[7] = ((byte) (fileSize & 0xFF));

        data[40] = ((byte) (dataSize & 0xFF)); dataSize >>= 8;
        data[41] = ((byte) (dataSize & 0xFF)); dataSize >>= 8;
        data[42] = ((byte) (dataSize & 0xFF)); dataSize >>= 8;
        data[43] = ((byte) (dataSize & 0xFF));
    }

    private void writeStr(String s) {
        try {
            baos.write(s.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeInt(int i) {
        baos.write((byte) (i & 0xFF));
        i >>= 8;
        baos.write((byte) (i & 0xFF));
        i >>= 8;
        baos.write((byte) (i & 0xFF));
        i >>= 8;
        baos.write((byte) (i & 0xFF));
    }

    private void writeShort(int i) {
        baos.write((byte) (i & 0xFF));
        i >>= 8;
        baos.write((byte) (i & 0xFF));
    }

    private void writeByte(byte b) {
        baos.write(b);
    }
}
