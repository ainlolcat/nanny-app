package org.ainlolcat.nanny.services.audio;

public interface AudioServiceCallback {
    void onData(byte[] data, int offset, int len);
}
