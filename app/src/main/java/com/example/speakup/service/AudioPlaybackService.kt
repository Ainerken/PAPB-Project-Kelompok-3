package com.example.speakup.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class AudioPlaybackService(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs

    private val _totalDurationMs = MutableStateFlow(0)
    val totalDurationMs: StateFlow<Int> = _totalDurationMs

    fun loadAudio(filePath: String?, defaultDurationSeconds: Int = 22) {
        release()
        _totalDurationMs.value = defaultDurationSeconds * 1000

        if (filePath.isNullOrEmpty() || !File(filePath).exists()) {
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(File(filePath)))
                prepare()
                _totalDurationMs.value = duration
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = duration
                    progressJob?.cancel()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play() {
        val player = mediaPlayer
        if (player != null) {
            try {
                if (_currentPositionMs.value >= _totalDurationMs.value && _totalDurationMs.value > 0) {
                    player.seekTo(0)
                }
                player.start()
                _isPlaying.value = true
                startProgressTracker()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Simulated playback if no physical audio file was recorded
            _isPlaying.value = true
            startSimulatedProgress()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun seekTo(positionMs: Int) {
        _currentPositionMs.value = positionMs
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPositionMs.value = it.currentPosition
                    }
                }
                delay(200)
            }
        }
    }

    private fun startSimulatedProgress() {
        progressJob?.cancel()
        val total = if (_totalDurationMs.value > 0) _totalDurationMs.value else 22000
        progressJob = scope.launch {
            while (_isPlaying.value && _currentPositionMs.value < total) {
                delay(200)
                _currentPositionMs.value = (_currentPositionMs.value + 200).coerceAtMost(total)
            }
            if (_currentPositionMs.value >= total) {
                _isPlaying.value = false
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        _isPlaying.value = false
        _currentPositionMs.value = 0
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
    }
}
