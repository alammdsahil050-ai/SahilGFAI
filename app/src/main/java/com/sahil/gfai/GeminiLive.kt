package com.sahil.gfai

import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal Gemini Live API (BidiGenerateContent) WebSocket client. */
class GeminiLive(private val l: Listener) {
    interface Listener {
        fun onReady()
        fun onAudio(pcm: ByteArray)
        fun onInText(t: String)
        fun onOutText(t: String)
        fun onInterrupted()
        fun onTurnComplete()
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onClosed(reason: String)
    }

    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var ws: WebSocket? = null
    @Volatile var ready = false
    @Volatile private var closedByUs = false

    fun connect(key: String, model: String, voice: String, system: String, tools: JSONArray) {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setup = JSONObject().put("setup", JSONObject()
                    .put("model", "models/$model")
                    .put("generationConfig", JSONObject()
                        .put("responseModalities", JSONArray().put("AUDIO"))
                        .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                            .put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("tools", tools)
                    .put("inputAudioTranscription", JSONObject())
                    .put("outputAudioTranscription", JSONObject())
                    .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject())))
                webSocket.send(setup.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false; if (!closedByUs) l.onClosed(t.message ?: "connection failed")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false; if (!closedByUs) l.onClosed("closed $code $reason")
            }
        })
    }

    private fun handle(s: String) {
        try {
            val j = JSONObject(s)
            if (j.has("setupComplete")) { ready = true; l.onReady() }
            j.optJSONObject("serverContent")?.let { sc ->
                sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                    for (i in 0 until parts.length()) {
                        parts.getJSONObject(i).optJSONObject("inlineData")?.let {
                            l.onAudio(Base64.decode(it.getString("data"), Base64.DEFAULT))
                        }
                    }
                }
                sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let(l::onInText)
                sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let(l::onOutText)
                if (sc.optBoolean("interrupted")) l.onInterrupted()
                if (sc.optBoolean("turnComplete")) l.onTurnComplete()
            }
            j.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { fc ->
                for (i in 0 until fc.length()) {
                    val c = fc.getJSONObject(i)
                    l.onToolCall(c.optString("id"), c.getString("name"), c.optJSONObject("args") ?: JSONObject())
                }
            }
        } catch (_: Exception) {}
    }

    fun sendAudio(pcm: ByteArray) {
        if (!ready) return
        ws?.send(JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
            .put("mimeType", "audio/pcm;rate=16000"))).toString())
    }
    fun sendText(t: String) {
        if (!ready) return
        ws?.send(JSONObject().put("realtimeInput", JSONObject().put("text", t)).toString())
    }
    fun sendToolResponse(id: String, name: String, result: String) {
        ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(
            JSONObject().put("id", id).put("name", name).put("response", JSONObject().put("result", result))))).toString())
    }
    fun close() { closedByUs = true; ready = false; try { ws?.close(1000, "bye") } catch (_: Exception) {} }
}
