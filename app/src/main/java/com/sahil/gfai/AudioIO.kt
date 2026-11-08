package com.sahil.gfai

import android.annotation.SuppressLint
import android.media.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/** Mic capture: 16 kHz mono PCM16, 100 ms chunks. */
class Recorder(private val onChunk: (ByteArray) -> Unit) {
    @Volatile private var running = false
    private var rec: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400))
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.enabled = true
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.enabled = true
        rec = r; running = true; r.startRecording()
        thread(name = "mic") {
            val buf = ByteArray(3200)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) onChunk(buf.copyOf(n))
            }
        }
    }
    fun stop() { running = false; try { rec?.stop(); rec?.release() } catch (_: Exception) {}; rec = null }
}

/** Speaker playback: 24 kHz mono PCM16 from Gemini. */
class Player {
    private val q = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = false
    private var track: AudioTrack? = null

    fun start() {
        val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, 48000)).setTransferMode(AudioTrack.MODE_STREAM).build()
        track = t; running = true; t.play()
        thread(name = "speaker") {
            while (running) {
                val b = q.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                t.write(b, 0, b.size)
            }
        }
    }
    fun write(pcm: ByteArray) { q.offer(pcm) }
    fun flush() { q.clear(); track?.let { it.pause(); it.flush(); it.play() } }
    fun stop() { running = false; q.clear(); try { track?.stop(); track?.release() } catch (_: Exception) {}; track = null }
}
