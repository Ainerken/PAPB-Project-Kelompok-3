package com.example.speakup.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

class AudioRecordingService(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMillis: Long = 0L
    private var isRecording = false

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds

    private val _amplitudeFlow = MutableStateFlow(0)
    val amplitudeFlow: StateFlow<Int> = _amplitudeFlow

    private var durationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun startRecording(): Boolean {
        try {
            val audioDir = File(context.filesDir, "recordings").apply { if (!exists()) mkdirs() }
            outputFile = File(audioDir, "speakup_session_${System.currentTimeMillis()}.m4a")

            val targetFile = outputFile!!

            fun createAndStartRecorder(source: Int): MediaRecorder {
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                return try {
                    recorder.apply {
                        setAudioSource(source)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(128000)
                        setAudioSamplingRate(44100)
                        setOutputFile(targetFile.absolutePath)
                        prepare()
                        start()
                    }
                } catch (e: Exception) {
                    try {
                        recorder.reset()
                        recorder.release()
                    } catch (_: Exception) {}
                    throw e
                }
            }

            mediaRecorder = try {
                createAndStartRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                targetFile.delete()
                createAndStartRecorder(MediaRecorder.AudioSource.MIC)
            }

            startTimeMillis = SystemClock.elapsedRealtime()
            isRecording = true
            _recordingDurationSeconds.value = 0

            durationJob?.cancel()
            durationJob = scope.launch {
                while (isRecording) {
                    val elapsed = (SystemClock.elapsedRealtime() - startTimeMillis) / 1000
                    _recordingDurationSeconds.value = elapsed.toInt()

                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        _amplitudeFlow.value = maxAmp
                    } catch (e: Exception) {
                        _amplitudeFlow.value = 0
                    }
                    delay(100)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            cancelRecording()
            return false
        }
    }

    fun stopRecording(): RecordingResult {
        isRecording = false
        durationJob?.cancel()
        val duration = _recordingDurationSeconds.value

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Ignore stop error if record was too brief
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        val file = outputFile
        return RecordingResult(
            filePath = file?.absolutePath,
            durationSeconds = duration,
            isValid = duration >= 5
        )
    }

    fun cancelRecording() {
        isRecording = false
        durationJob?.cancel()
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {}
                reset()
                release()
            }
        } catch (e: Exception) {}
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }

    fun releaseScope() {
        isRecording = false
        durationJob?.cancel()
        scope.cancel()
    }

    data class RecordingResult(
        val filePath: String?,
        val durationSeconds: Int,
        val isValid: Boolean
    )
}
