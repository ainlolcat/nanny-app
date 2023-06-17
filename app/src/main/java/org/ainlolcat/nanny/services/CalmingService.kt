package org.ainlolcat.nanny.services

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.util.Supplier
import org.ainlolcat.nanny.settings.NannySettings
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class CalmingService(private val settingsSupplier: Supplier<NannySettings>, private val context: Context) {
    private val TAG = "CalmingService"

    private var calmModeSavedSoundRef: AtomicReference<File> = AtomicReference(null)
    private var calmModePlaying = AtomicBoolean(false)

    fun canStartCalmingSound() : Boolean {
        val settings = settingsSupplier.get()
        return settings.calmModeOn && !calmModePlaying.get()
    }

    fun startCalmingSound() {
        val settings = settingsSupplier.get()
        var currentSoundFile = calmModeSavedSoundRef.get()
        if (currentSoundFile == null) {
            val calmModeSoundData = Base64.decode(settings.calmModeSound, Base64.DEFAULT)
            val tmpCalmModeSavedSound = File.createTempFile("voice", ".ogg")
            tmpCalmModeSavedSound.deleteOnExit()
            val stream = FileOutputStream(tmpCalmModeSavedSound)
            try {
                stream.write(calmModeSoundData)
            } finally {
                stream.close()
            }
            calmModeSavedSoundRef.set(tmpCalmModeSavedSound)
            currentSoundFile = tmpCalmModeSavedSound
        }

        val volume: Float = (settings.calmModeVolume / 100.0).toFloat()
        val mPlayer = MediaPlayer.create(context, Uri.fromFile(currentSoundFile))
        mPlayer.setVolume(volume, volume)
        mPlayer.setOnCompletionListener {
            try {
                mPlayer.release()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot release player due error", e)
            }
            calmModePlaying.set(false)
        }
        mPlayer.setOnErrorListener { mp, what, extra ->
            try {
                mPlayer.release()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot release player due error", e)
            }
            calmModePlaying.set(false)
            true
        }
        mPlayer.start()
        calmModePlaying.set(true)
    }

    fun reinitCalmingSound() {
        calmModeSavedSoundRef.set(null)
    }
}