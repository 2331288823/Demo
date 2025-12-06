/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork.ui.conversation

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 音频录制器。
 *
 * 负责从麦克风捕获原始音频数据 (PCM 16bit)。
 * 主要用于语音识别功能的音频输入。
 *
 * @param context Android 上下文。
 */
class AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // 创建一个持久的作用域
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    ).coerceAtLeast(4096) // 确保缓冲区至少为 4KB

    /**
     * 开始录音。
     *
     * @param onAudioData 音频数据回调,当有新的音频数据块可用时触发。
     * @param onError 错误回调,当发生异常时触发。
     * @param onVolumeChanged 音量变化回调（0f-1f）。
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        onAudioData: (ByteArray, Int) -> Unit,
        onError: (Exception) -> Unit,
        onVolumeChanged: ((Float) -> Unit)? = null
    ) {
        // 防止重复启动
        if (recordingJob?.isActive == true) {
            Log.w("AudioRecorder", "Recording already in progress")
            return
        }

        try {
            Log.d("AudioRecorder", "Initializing AudioRecord with buffer size: $bufferSize")

            // 初始化 AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            // 检查初始化状态
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val error = Exception("AudioRecord initialization failed. State: ${audioRecord?.state}")
                Log.e("AudioRecorder", error.message ?: "Unknown error")
                onError(error)
                return
            }

            // 开始录音
            audioRecord?.startRecording()
            Log.d("AudioRecorder", "AudioRecord started recording")

            // 在持久作用域中启动录音循环
            recordingJob = recordingScope.launch {
                val buffer = ByteArray(bufferSize)
                var totalBytesRead = 0

                Log.d("AudioRecorder", "Recording loop started")

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0

                    when {
                        bytesRead > 0 -> {
                            totalBytesRead += bytesRead
                            onAudioData(buffer.copyOf(bytesRead), bytesRead)

                            // 计算音量（振幅）
                            onVolumeChanged?.let {
                                val volume = calculateVolume(buffer, bytesRead)
                                it(volume)
                            }

                            // 每秒记录一次日志 (16000 采样率 * 2 字节 = 32000 字节/秒)
                            if (totalBytesRead % 32000 < bufferSize) {
                                Log.d("AudioRecorder", "Read $bytesRead bytes (total: $totalBytesRead)")
                            }
                        }
                        bytesRead == 0 -> {
                            Log.w("AudioRecorder", "AudioRecord read 0 bytes")
                        }
                        else -> {
                            // bytesRead < 0 表示错误
                            val errorMsg = when (bytesRead) {
                                AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                                AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                                AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                                AudioRecord.ERROR -> "ERROR"
                                else -> "Unknown error code: $bytesRead"
                            }
                            Log.e("AudioRecorder", "AudioRecord read error: $errorMsg")
                            onError(Exception("AudioRecord read error: $errorMsg"))
                            break
                        }
                    }
                }

                Log.d("AudioRecorder", "Recording loop ended. Total bytes: $totalBytesRead")
            }

        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            onError(e)
        }
    }

    /**
     * 计算音频振幅（音量）
     * @return 0f - 1f 之间的值
     */
    private fun calculateVolume(buffer: ByteArray, size: Int): Float {
        var sum = 0L
        for (i in 0 until size step 2) {
            // PCM 16bit 是小端序
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += abs(sample.toShort().toLong())
        }
        val average = sum / (size / 2)
        // 归一化到 0-1 范围（Short.MAX_VALUE = 32767）
        return (average.toFloat() / 32767f).coerceIn(0f, 1f)
    }

    /**
     * 停止录音。
     * 释放 AudioRecord 资源并取消协程。
     */
    fun stopRecording() {
        Log.d("AudioRecorder", "Stopping recording...")

        // 取消录音任务
        recordingJob?.cancel()
        recordingJob = null

        // 停止并释放 AudioRecord
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                    Log.d("AudioRecorder", "AudioRecord stopped")
                }
                release()
                Log.d("AudioRecorder", "AudioRecord released")
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping AudioRecord", e)
        }

        audioRecord = null
    }

    /**
     * 清理资源
     * 在不再需要 AudioRecorder 时调用
     */
    fun cleanup() {
        stopRecording()
        recordingScope.cancel()
        Log.d("AudioRecorder", "AudioRecorder cleanup completed")
    }
}