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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private val bufferSize = AudioRecord.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    /**
     * 开始录音。
     *
     * @param onAudioData 音频数据回调，当有新的音频数据块可用时触发。
     * @param onError 错误回调，当发生异常时触发。
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        onAudioData: (ByteArray, Int) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (recordingJob != null) return

        try {
            // 初始化 AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000, // 采样率 16kHz
                AudioFormat.CHANNEL_IN_MONO, // 单声道
                AudioFormat.ENCODING_PCM_16BIT, // 16位 PCM
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError(Exception("AudioRecord init failed"))
                return
            }

            audioRecord?.startRecording()

            // 在 IO 线程中循环读取音频数据
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        onAudioData(buffer, read)
                    } else if (read < 0) {
                         // read < 0 表示错误代码
                         onError(Exception("AudioRecord read error: $read"))
                         break
                    }
                }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * 停止录音。
     * 释放 AudioRecord 资源并取消协程。
     */
    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
