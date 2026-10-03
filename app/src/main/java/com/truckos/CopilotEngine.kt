package com.truckos.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class CopilotEngine(
    private val context: Context,
    private val onStateChange: (Boolean, String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null
    private val prefs: SharedPreferences = context.getSharedPreferences("truckos_brain", Context.MODE_PRIVATE)

    // Put your free Google AI Studio API key here:
    private val apiKey = "YOUR_GEMINI_API_KEY_HERE"

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.95f)
            tts?.setSpeechRate(1.05f)
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speak("Speech recognition is not available on this device.")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStateChange(true, "Listening...")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onStateChange(false, "Thinking...")
                }

                override fun onError(error: Int) {
                    onStateChange(false, "Tap to talk")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull() ?: ""
                    if (spokenText.isNotBlank()) {
                        processDriverQuery(spokenText)
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

    fun getMemoryList(): List<String> {
        return prefs.getStringSet("learned_facts", emptySet())?.toList() ?: emptyList()
    }

    fun storeFact(fact: String) {
        val current = prefs.getStringSet("learned_facts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        current.add(fact)
        prefs.edit().putStringSet("learned_facts", current).apply()
    }

    private fun processDriverQuery(query: String) {
        onStateChange(false, "Thinking...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val knownFacts = getMemoryList().joinToString(separator = "\n- ", prefix = "- ")

                val systemPrompt = """
                    You are TruckOS, an intelligent semi-truck co-driver and assistant.
                    Keep responses very concise, clear, and direct so they sound natural when read over vehicle speakers.
                    Do not use markdown, bullet points, asterisks, or tables.
                    
                    Permanent driver facts and skills you have learned:
                    $knownFacts
                    
                    If the driver asks you to remember something, state clearly that you have memorized it.
                """.trimIndent()

                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey,
                    systemInstruction = content { text(systemPrompt) }
                )

                val response = model.generateContent(query)
                val reply = response.text ?: "I heard you, but I could not compute an answer."

                // If driver says to remember something, commit it to persistent disk
                if (query.contains("remember", ignoreCase = true) || query.contains("my favorite", ignoreCase = true)) {
                    storeFact(query)
                }

                withContext(Dispatchers.Main) {
                    onStateChange(false, reply)
                    speak(reply)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val fallback = "Could not reach co-pilot services. Please check your data connection."
                    onStateChange(false, fallback)
                    speak(fallback)
                }
            }
        }
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
