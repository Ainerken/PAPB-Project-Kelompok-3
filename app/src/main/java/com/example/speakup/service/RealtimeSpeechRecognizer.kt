package com.example.speakup.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RealtimeSpeechRecognizer(private val context: Context) {

    private val tag = "SpeakUpRecognizer"
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isRecording = false
    private var language = "English"

    private val accumulatedTextBuilder = StringBuilder()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing

    private val _recognizerError = MutableStateFlow<String?>(null)
    val recognizerError: StateFlow<String?> = _recognizerError

    fun startListening(selectedLanguage: String) {
        language = selectedLanguage
        isRecording = true
        accumulatedTextBuilder.clear()
        _liveTranscript.value = ""
        _recognizerError.value = null

        mainHandler.post {
            initRecognizerAndStart()
        }
    }

    private fun initRecognizerAndStart() {
        if (!isRecording) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "Speech recognition is NOT available on this device/emulator")
            _isRecognizing.value = false
            _recognizerError.value = "Layanan SpeechRecognizer belum terpasang di perangkat"
            return
        }

        try {
            cleanupRecognizer()

            // Prefer standard SpeechRecognizer for broad compatibility, or on-device if available
            speechRecognizer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(tag, "onReadyForSpeech")
                    _isRecognizing.value = true
                    _recognizerError.value = null
                }

                override fun onBeginningOfSpeech() {
                    Log.d(tag, "onBeginningOfSpeech")
                    _isRecognizing.value = true
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(tag, "onEndOfSpeech")
                    _isRecognizing.value = false
                    if (isRecording) {
                        mainHandler.postDelayed({
                            initRecognizerAndStart()
                        }, 250)
                    }
                }

                override fun onError(error: Int) {
                    val errorMsg = getErrorMessage(error)
                    Log.w(tag, "Speech recognition error: $error ($errorMsg)")
                    _isRecognizing.value = false

                    if (!isRecording) return

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // User paused speaking, continue listening seamlessly
                            mainHandler.postDelayed({
                                initRecognizerAndStart()
                            }, 300)
                        }
                        SpeechRecognizer.ERROR_AUDIO,
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // Recover from audio device contention
                            mainHandler.postDelayed({
                                cleanupRecognizer()
                                initRecognizerAndStart()
                            }, 700)
                        }
                        else -> {
                            _recognizerError.value = errorMsg
                            mainHandler.postDelayed({
                                initRecognizerAndStart()
                            }, 1000)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognized = matches[0].trim()
                        if (recognized.isNotBlank()) {
                            if (accumulatedTextBuilder.isNotEmpty()) {
                                accumulatedTextBuilder.append(" ")
                            }
                            accumulatedTextBuilder.append(recognized)
                            _liveTranscript.value = accumulatedTextBuilder.toString()
                            Log.d(tag, "Accumulated text: ${_liveTranscript.value}")
                        }
                    }

                    if (isRecording) {
                        mainHandler.postDelayed({
                            initRecognizerAndStart()
                        }, 200)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partial = matches[0].trim()
                        val current = if (accumulatedTextBuilder.isEmpty()) {
                            partial
                        } else {
                            "${accumulatedTextBuilder.toString()} $partial"
                        }
                        _liveTranscript.value = current
                        Log.d(tag, "Partial text: $current")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val isIndo = language.startsWith("Indo", ignoreCase = true)
            val langTag = if (isIndo) "id-ID" else "en-US"

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start speech recognition", e)
            _isRecognizing.value = false
            _recognizerError.value = e.localizedMessage
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
        }
    }

    fun stopListening(): String {
        isRecording = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            cleanupRecognizer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isRecognizing.value = false

        val result = _liveTranscript.value.trim()
        Log.d(tag, "Final recognized transcript: '$result'")
        return result
    }

    fun cancelListening() {
        isRecording = false
        mainHandler.removeCallbacksAndMessages(null)
        cleanupRecognizer()
        _isRecognizing.value = false
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
            else -> "Speech recognition error ($errorCode)"
        }
    }
}
