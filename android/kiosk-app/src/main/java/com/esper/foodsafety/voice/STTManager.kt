package com.esper.foodsafety.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class STTState {
    object Idle : STTState()
    data class Listening(val partial: String = "") : STTState()
    data class Result(val transcript: String) : STTState()
    data class Error(val message: String) : STTState()
}

class STTManager(private val context: Context) {

    private val _state = MutableStateFlow<STTState>(STTState.Idle)
    val state: StateFlow<STTState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun startListening() {
        if (_state.value is STTState.Listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = STTState.Error("Speech recognition not available on this device")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = STTState.Listening()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull() ?: ""
                    _state.value = STTState.Result(transcript)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    _state.value = STTState.Listening(partial)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        else -> "STT error $error"
                    }
                    _state.value = STTState.Error(msg)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            sr.startListening(intent)
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun reset() {
        recognizer?.destroy()
        recognizer = null
        _state.value = STTState.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
