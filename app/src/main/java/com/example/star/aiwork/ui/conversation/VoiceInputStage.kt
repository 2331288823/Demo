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

/**
 * 语音输入的各个阶段
 */
enum class VoiceInputStage {
    IDLE,           // 空闲状态（未开始录音）
    RECORDING,      // 录音中（浅蓝色面板）
    CANCEL_WARNING, // 取消警告（红色面板，用户上滑）
    EDITING         // 编辑状态（录音结束，用户可编辑文字）
}
