package com.sahil.gfai

import android.content.Context

object Prefs {
    const val DEFAULT_MODEL = "gemini-3.1-flash-live-preview"
    val FEMALE_VOICES = listOf("Aoede", "Kore", "Leda", "Zephyr", "Autonoe")

    private fun sp(c: Context) = c.getSharedPreferences("sahil_gf", Context.MODE_PRIVATE)
    fun apiKey(c: Context) = sp(c).getString("api", "") ?: ""
    fun setApiKey(c: Context, v: String) = sp(c).edit().putString("api", v.trim()).apply()
    fun model(c: Context) = sp(c).getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun setModel(c: Context, v: String) = sp(c).edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply()
    fun voice(c: Context) = sp(c).getString("voice", "Aoede") ?: "Aoede"
    fun setVoice(c: Context, v: String) = sp(c).edit().putString("voice", v).apply()
    fun name(c: Context) = sp(c).getString("name", "Priya") ?: "Priya"
    fun setName(c: Context, v: String) = sp(c).edit().putString("name", v.trim().ifBlank { "Priya" }).apply()
    fun design(c: Context) = sp(c).getInt("design", 1)
    fun setDesign(c: Context, v: Int) = sp(c).edit().putInt("design", v).apply()
    fun always(c: Context) = sp(c).getBoolean("always", true)
    fun setAlways(c: Context, v: Boolean) = sp(c).edit().putBoolean("always", v).apply()
}
