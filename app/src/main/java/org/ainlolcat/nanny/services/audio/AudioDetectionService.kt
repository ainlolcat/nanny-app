package org.ainlolcat.nanny.services.audio

interface AudioDetectionService {
    fun isRunning(): Boolean
    fun start()
    fun stop()
    fun addCallback(audioServiceCallback: AudioServiceCallback)
    fun removeCallbacks()
}