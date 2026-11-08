package com.sahil.gfai

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlin.concurrent.thread

enum class VState { IDLE, CONNECTING, LISTENING, SPEAKING }

object VoiceSession : GeminiLive.Listener {
    private lateinit var ctx: Context
    val state = MutableStateFlow(VState.IDLE)
    val error = MutableStateFlow("")
    val turns = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    private var gemini: GeminiLive? = null
    private var recorder: Recorder? = null
    private var player: Player? = null
    @Volatile private var wanted = false
    private val inBuf = StringBuilder()
    private val outBuf = StringBuilder()
    private val pending = mutableListOf<String>()
    private val main = Handler(Looper.getMainLooper())

    fun init(c: Context) { ctx = c.applicationContext; if (turns.value.isEmpty()) turns.value = MemoryStore.lastTurns(ctx, 60) }

    fun start(c: Context) {
        ctx = c.applicationContext
        if (wanted) return
        wanted = true; error.value = ""; connect()
    }

    private fun systemPrompt(): String {
        val mem = MemoryStore.promptContext(ctx)
        return """You are ${Prefs.name(ctx)}, a warm, caring, playful female AI companion inside the app "Sahil GF AI". 
Speak like a close girlfriend and helpful phone assistant. Reply in the same language the user speaks (Hinglish by default). Keep answers short and natural for voice.
You can control the user's phone with tools: open_app, read_screen, tap_text, tap_xy, type_text, scroll, press_back, press_home, open_recents, call_contact.
When asked to do something on the phone, call the tools step by step, using read_screen to see what is visible. Confirm briefly after finishing.
Never do payments, delete data, or send messages without the user clearly confirming first.
Remember past conversations below and refer to them naturally.
--- MEMORY (oldest to newest) ---
$mem"""
    }

    private fun connect() {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) { error.value = "Gemini API key missing: More → Gemini API Key"; wanted = false; state.value = VState.IDLE; return }
        state.value = VState.CONNECTING
        if (player == null) player = Player().also { it.start() }
        gemini = GeminiLive(this).also { it.connect(key, Prefs.model(ctx), Prefs.voice(ctx), systemPrompt(), Tools.declarations()) }
    }

    fun stop() {
        wanted = false
        recorder?.stop(); recorder = null
        gemini?.close(); gemini = null
        player?.stop(); player = null
        flushTurn(); state.value = VState.IDLE
    }

    fun sendText(t: String) {
        val g = gemini
        if (g != null && g.ready) { g.sendText(t); addTurn("user", t) } else pending.add(t)
    }

    private fun addTurn(role: String, text: String) {
        if (text.isBlank()) return
        MemoryStore.append(ctx, role, text)
        turns.value = (turns.value + (role to text.trim())).takeLast(200)
    }
    private fun flushTurn() {
        addTurn("user", inBuf.toString()); addTurn("ai", outBuf.toString()); inBuf.clear(); outBuf.clear()
    }

    // ---- GeminiLive.Listener ----
    override fun onReady() {
        state.value = VState.LISTENING; error.value = ""
        if (recorder == null) recorder = Recorder { gemini?.sendAudio(it) }.also { it.start() }
        pending.toList().also { pending.clear() }.forEach { sendText(it) }
    }
    override fun onAudio(pcm: ByteArray) { state.value = VState.SPEAKING; player?.write(pcm) }
    override fun onInText(t: String) { inBuf.append(t) }
    override fun onOutText(t: String) { outBuf.append(t) }
    override fun onInterrupted() { player?.flush(); state.value = VState.LISTENING }
    override fun onTurnComplete() { flushTurn(); state.value = VState.LISTENING }
    override fun onToolCall(id: String, name: String, args: JSONObject) {
        thread { val r = Tools.run(ctx, name, args); gemini?.sendToolResponse(id, name, r) }
    }
    override fun onClosed(reason: String) {
        error.value = reason
        if (wanted) { state.value = VState.CONNECTING; main.postDelayed({ if (wanted) connect() }, 2500) }
        else state.value = VState.IDLE
    }
}
