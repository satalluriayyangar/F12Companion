package com.f12companion.reply

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceReplyHandler(private val speechRecognizer: SpeechRecognizer) {

    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null
    var onSpeechPartial: ((String) -> Unit)? = null

    private var isListening = false

    fun startListening(language: String = "en-US") {
        if (isListening) {
            Log.w("VoiceReplyHandler", "Already listening")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.startListening(intent)
        isListening = true
        Log.d("VoiceReplyHandler", "Started listening")
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
            Log.d("VoiceReplyHandler", "Stopped listening")
        }
    }

    fun destroy() {
        stopListening()
        speechRecognizer.destroy()
    }

    fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {
                Log.d("VoiceReplyHandler", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("VoiceReplyHandler", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray) {}

            override fun onEndOfSpeech() {
                Log.d("VoiceReplyHandler", "End of speech")
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error: $error"
                }
                Log.e("VoiceReplyHandler", "Error: $message")
                onSpeechError?.invoke(message)
            }

            override fun onResults(results: Bundle) {
                isListening = false
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.d("VoiceReplyHandler", "Result: $text")
                    onSpeechResult?.invoke(text)
                } else {
                    onSpeechError?.invoke("No speech results")
                }
            }

            override fun onPartialResults(partialResults: Bundle) {
                val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onSpeechPartial?.invoke(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle) {}
        }
    }
}
