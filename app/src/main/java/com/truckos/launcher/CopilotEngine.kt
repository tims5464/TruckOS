package com.truckos.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class CopilotEngine(
    private val context: Context,
    private val onStateChange: (Boolean, String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null
    private val prefs: SharedPreferences = context.getSharedPreferences("truckos_memories", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // Optional API key for when connected to data
    private val apiKey = "YOUR_API_KEY_HERE"

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.95f)
            tts?.setSpeechRate(1.05f)
        }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speak("Voice input is not ready. Please verify Lineage speech recognizer.")
            return
        }

        stopListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onStateChange(true, "Listening...")
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() = onStateChange(false, "Processing...")
                override fun onError(error: Int) = onStateChange(false, "Tap to talk")
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        processInput(text)
                    } else {
                        onStateChange(false, "Tap to talk")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun storeFact(key: String, fact: String) {
        prefs.edit().putString(key.lowercase(Locale.ROOT), fact).apply()
    }

    fun getAllFacts(): Map<String, *> = prefs.all

    private fun processInput(query: String) {
        onStateChange(false, "Thinking...")

        // 1. Skill: Learning & Remembering Facts
        val lower = query.lowercase(Locale.ROOT)
        if (lower.startsWith("remember that") || lower.startsWith("remember my") || lower.startsWith("save")) {
            val fact = query.replaceFirst(Regex("(?i)^(remember that|remember my|save)\\s*"), "").trim()
            storeFact("memory_${System.currentTimeMillis()}", fact)
            val reply = "Got it. I committed that to memory."
            onStateChange(false, reply)
            speak(reply)
            return
        }

        // 2. Skill: Offline Memory Recall
        if (lower.contains("what is my") || lower.contains("what's my") || lower.contains("do you remember")) {
            val all = getAllFacts().values.map { it.toString() }
            val match = all.firstOrNull { fact ->
                val keywords = lower.split(" ").filter { it.length > 3 }
                keywords.any { fact.lowercase(Locale.ROOT).contains(it) }
            }
            if (match != null) {
                val reply = "You told me: $match"
                onStateChange(false, reply)
                speak(reply)
                return
            }
        }

        // 3. Online Reasoning Agent
        if (isOnline() && apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val memoryPrompt = getAllFacts().values.joinToString(", ")
                    val jsonPayload = JSONObject().apply {
                        val contents = JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", "You are TruckOS co-pilot. Offline driver memory: [$memoryPrompt]. Driver asks: $query. Respond concisely for speech synthesis."))
                                })
                            })
                        }
                        put("contents", contents)
                    }

                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val replyText = JSONObject(body)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .replace("*", "")
                        .trim()

                    withContext(Dispatchers.Main) {
                        onStateChange(false, replyText)
                        speak(replyText)
                    }
                } catch (e: Exception) {
                    fallbackLocal(query)
                }
            }
        } else {
            fallbackLocal(query)
        }
    }

    private fun fallbackLocal(query: String) {
        val reply = when {
            query.contains("speed", true) -> "Monitor the center speedometer dial on your dash."
            query.contains("time", true) -> "Check the status bar clock at the top right."
            query.contains("waze", true) || query.contains("nav", true) -> "Tap the left navigation button to open Waze."
            else -> "Offline mode active. Stored your input or query."
        }
        onStateChange(false, reply)
        speak(reply)
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TRUCKOS_VOICE")
    }

    fun shutdown() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
    }
}
