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

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.sin

/**
 * 语音输入悬浮面板
 * 
 * @param stage 当前语音输入阶段
 * @param transcription 转写的文字
 * @param volume 当前音量（0f-1f）
 * @param onTextChanged 文字编辑回调
 * @param onConfirm 确认发送回调
 * @param onCancel 取消回调
 */
@Composable
fun VoiceInputPanel(
    stage: VoiceInputStage,
    transcription: String,
    volume: Float,
    onTextChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    // 只在录音中、取消警告、编辑状态下显示面板
    if (stage == VoiceInputStage.IDLE) return

    Dialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 悬浮面板（带小尾巴）
                VoicePanel(
                    stage = stage,
                    transcription = transcription,
                    volume = volume,
                    onTextChanged = onTextChanged
                )

                // 底部提示文字
                if (stage == VoiceInputStage.RECORDING || stage == VoiceInputStage.CANCEL_WARNING) {
                    Text(
                        text = if (stage == VoiceInputStage.RECORDING) {
                            "松开转文字，上滑取消"
                        } else {
                            "松开 取消"
                        },
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                // 编辑状态下的操作按钮
                if (stage == VoiceInputStage.EDITING) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 取消按钮
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "取消",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // 确认按钮（大对勾）
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .clickable(onClick = onConfirm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "确认",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 语音面板主体（带小尾巴的圆角矩形）
 */
@Composable
private fun VoicePanel(
    stage: VoiceInputStage,
    transcription: String,
    volume: Float,
    onTextChanged: (String) -> Unit
) {
    // 面板背景色
    val panelColor = when (stage) {
        VoiceInputStage.RECORDING -> Color(0xFFE3F2FD) // 浅蓝色
        VoiceInputStage.CANCEL_WARNING -> Color(0xFFFF5252) // 红色
        VoiceInputStage.EDITING -> Color(0xFFE3F2FD) // 浅蓝色
        else -> Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 主面板
        Box(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .heightIn(min = 120.dp)
                .background(panelColor, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            when (stage) {
                VoiceInputStage.RECORDING, VoiceInputStage.CANCEL_WARNING -> {
                    // 录音中：显示实时转写文字 + 波形
                    RecordingContent(
                        transcription = transcription,
                        volume = volume,
                        isWarning = stage == VoiceInputStage.CANCEL_WARNING
                    )
                }
                VoiceInputStage.EDITING -> {
                    // 编辑状态：可编辑的文本框
                    EditingContent(
                        text = transcription,
                        onTextChanged = onTextChanged
                    )
                }
                else -> {}
            }
        }

        // 小尾巴（三角形）
        Box(
            modifier = Modifier
                .size(20.dp, 10.dp)
                .background(
                    panelColor,
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                )
        )
    }
}

/**
 * 录音中的内容（转写文字 + 波形动画）
 */
@Composable
private fun RecordingContent(
    transcription: String,
    volume: Float,
    isWarning: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：转写文字或占位符
        Text(
            text = if (transcription.isEmpty()) "..." else transcription,
            fontSize = 16.sp,
            color = if (isWarning) Color.White else Color.Black,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )

        // 右侧：音量波形动画
        WaveformAnimation(
            volume = volume,
            color = if (isWarning) Color.White else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 编辑状态的内容（可编辑文本框）
 */
@Composable
private fun EditingContent(
    text: String,
    onTextChanged: (String) -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }

    // 同步外部文字变化
    LaunchedEffect(text) {
        if (textFieldValue.text != text) {
            textFieldValue = TextFieldValue(text)
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            onTextChanged(it.text)
        },
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = Color.Black
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = "点击编辑...",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * 音量波形动画（多个竖条）
 */
@Composable
private fun WaveformAnimation(
    volume: Float,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    // 创建多个相位不同的动画
    val barCount = 5
    val animations = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(40.dp)
    ) {
        animations.forEachIndexed { index, animation ->
            val phase = (index * 0.2f)
            val height = 8.dp + (20.dp * volume * abs(sin((animation.value + phase) * Math.PI * 2)).toFloat())
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
