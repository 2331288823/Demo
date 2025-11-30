/*
 * Copyright 2020 The Android Open Source Project
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.star.aiwork.ui.conversation

import android.Manifest
import android.content.ClipDescription
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.star.aiwork.R
import com.example.star.aiwork.data.exampleUiState
import com.example.star.aiwork.data.provider.OpenAIProvider
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.infra.util.AIRequestInterceptor
import com.example.star.aiwork.infra.util.toBase64
import com.example.star.aiwork.ui.FunctionalityNotAvailablePopup
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.ui.components.JetchatAppBar
import com.example.star.aiwork.ui.theme.JetchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.math.roundToInt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentSize



/**
 * 对话屏幕的入口点。
 *
 * 这个可组合函数协调主要的对话 UI，包括：
 * - 显示消息历史记录。
 * - 处理用户输入（文本和语音）。
 * - 管理 AI 模型交互（文本生成）。
 * - 处理设置对话框和导航。
 *
 * @param uiState [ConversationUiState] 包含要显示的消息和 UI 状态。
 * @param navigateToProfile 请求导航到用户个人资料时的回调。
 * @param modifier 应用于此布局节点的 [Modifier]。
 * @param onNavIconPressed 当按下导航图标（汉堡菜单）时的回调。
 * @param providerSettings 可用的 AI 提供商设置列表。
 * @param activeProviderId 当前选中的提供商 ID。
 * @param activeModelId 当前选中的模型 ID。
 * @param temperature 当前的 AI 文本生成温度设置 (0.0 - 2.0)。
 * @param maxTokens 生成的最大 Token 数。
 * @param streamResponse 是否流式传输 AI 响应或等待完整响应。
 * @param onUpdateSettings 更新模型设置（温度、最大 Token 数、流式响应）的回调。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationContent(
    uiState: ConversationUiState,
    navigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    providerSettings: List<ProviderSetting> = emptyList(),
    activeProviderId: String? = null,
    activeModelId: String? = null,
    temperature: Float = 0.7f,
    maxTokens: Int = 2000,
    streamResponse: Boolean = true,
    onUpdateSettings: (Float, Int, Boolean) -> Unit = { _, _, _ -> }
) {
    val authorMe = stringResource(R.string.author_me)
    val timeNow = stringResource(id = R.string.now)
    val context = LocalContext.current

    // 列表滚动和顶部应用栏行为的状态
    val scrollState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    val scope = rememberCoroutineScope()

    // 显示模型设置对话框的状态
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 将从 ViewModel 传递的参数与 UiState 同步
    // 这确保了 UI 反映持久化的设置
    LaunchedEffect(temperature, maxTokens, streamResponse) {
        uiState.temperature = temperature
        uiState.maxTokens = maxTokens
        uiState.streamResponse = streamResponse
    }

    // 拖放视觉状态
    var background by remember {
        mutableStateOf(Color.Transparent)
    }

    var borderStroke by remember {
        mutableStateOf(Color.Transparent)
    }

    // 如果请求，显示模型设置对话框
    if (showSettingsDialog) {
        ModelSettingsDialog(
            uiState = uiState,
            onDismissRequest = {
                // 当对话框关闭时保存设置
                onUpdateSettings(uiState.temperature, uiState.maxTokens, uiState.streamResponse)
                showSettingsDialog = false
            }
        )
    }

    // 拖放回调处理
    val dragAndDropCallback = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipData = event.toAndroidDragEvent().clipData

                if (clipData.itemCount < 1) {
                    return false
                }

                // 将拖放的文本添加为新消息
                uiState.addMessage(
                    Message(authorMe, clipData.getItemAt(0).text.toString(), timeNow),
                )

                return true
            }

            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)
                borderStroke = Color.Red
            }

            override fun onEntered(event: DragAndDropEvent) {
                super.onEntered(event)
                background = Color.Red.copy(alpha = .3f)
            }

            override fun onExited(event: DragAndDropEvent) {
                super.onExited(event)
                background = Color.Transparent
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)
                background = Color.Transparent
                borderStroke = Color.Transparent
            }
        }
    }

    // 初始化带有 OkHttp 客户端的 OpenAI 提供商
    val client = remember {
        OkHttpClient.Builder()
            .addInterceptor(AIRequestInterceptor())
            .build()
    }
    val provider = remember { OpenAIProvider(client) }

    // 根据 ID 选择当前的 Provider 和 Model
    val providerSetting = remember(providerSettings, activeProviderId) {
        providerSettings.find { it.id == activeProviderId } ?: providerSettings.firstOrNull()
    }
    val model = remember(providerSetting, activeModelId) {
        providerSetting?.models?.find { it.modelId == activeModelId } ?: providerSetting?.models?.firstOrNull()
    }

    // 初始化用于语音转文本的音频录制器和 WebSocket
    val audioRecorder = remember { AudioRecorder(context) }

    // 跟踪挂起的部分文本长度，以便在实时转录期间正确替换它
    var lastPartialLength by remember { mutableIntStateOf(0) }

    // 处理 ASR 结果的转录监听器
    val transcriptionListener = remember(scope, uiState) {
        object : YoudaoWebSocket.TranscriptionListener {
            override fun onTranscriptionReceived(text: String) {  // ✅ 修正方法名
                scope.launch(Dispatchers.Main) {
                    uiState.textFieldValue = uiState.textFieldValue.copy(
                        text = text,
                        selection = TextRange(text.length)
                    )
                }
            }

            override fun onError(error: String) {  // ✅ 修正参数类型
                Log.e("VoiceInput", "ASR Error: $error")
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "识别错误: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val youdaoWebSocket = remember {
        YoudaoWebSocket().apply {
            listener = transcriptionListener
        }
    }

    // 音频录制的权限启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, trying to start recording again...
            // Note: Ideally we should not auto-start, but for user convenience here we might want to signal UI
            // However, the original logic required user to press again.
            Toast.makeText(context, "Permission granted, press record again", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 在 dispose 时清理资源
    DisposableEffect(Unit) {
        onDispose {
            youdaoWebSocket.close()
            audioRecorder.stopRecording()
            audioRecorder.cleanup()
        }
    }

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = uiState.channelName,
                channelMembers = uiState.channelMembers,
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                onSettingsClicked = { showSettingsDialog = true }
            )
        },
        // 排除 ime 和导航栏内边距，以便由 UserInput composable 添加
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.navigationBars)
            .exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues)
                .background(color = background)
                .border(width = 2.dp, color = borderStroke)
                .dragAndDropTarget(shouldStartDragAndDrop = { event ->
                    event
                        .mimeTypes()
                        .contains(
                            ClipDescription.MIMETYPE_TEXT_PLAIN,
                        )
                }, target = dragAndDropCallback),
        ) {
            // 消息列表
            Messages(
                messages = uiState.messages,
                navigateToProfile = navigateToProfile,
                modifier = Modifier.weight(1f),
                scrollState = scrollState,
            )

            // 用户输入区域//f2
            UserInput(
                selectedImageUri = uiState.selectedImageUri,
                onImageSelected = { uri -> uiState.selectedImageUri = uri },
                onMessageSent = { content ->
                    // 将发送逻辑封装为挂起函数，支持递归调用
                    suspend fun processMessage(
                        inputContent: String,
                        isAutoTriggered: Boolean = false,
                        loopCount: Int = 0
                    ) {
                        // 1. 如果是用户手动发送，立即显示消息；自动追问也显示在 UI 上
                        if (!isAutoTriggered) {
                            val currentImageUri = uiState.selectedImageUri
                            uiState.addMessage(
                                Message(
                                    author = authorMe,
                                    content = inputContent,
                                    timestamp = timeNow,
                                    imageUrl = currentImageUri?.toString()
                                )
                            )
                            // 清空已选择的图片
                            uiState.selectedImageUri = null
                        } else {
                            // 自动追问消息，可以显示不同的样式或前缀，这里简单处理
                            uiState.addMessage(Message(authorMe, "[Auto-Loop ${loopCount}] $inputContent", timeNow))
                        }

                        // 2. 调用 LLM 获取响应
                        if (providerSetting != null && model != null) {
                            // 检查提供商是否兼容
                            if (providerSetting !is ProviderSetting.OpenAI) {
                                uiState.addMessage(
                                    Message("System", "Currently only OpenAI compatible providers are supported.", timeNow)
                                )
                                return
                            }

                            try {
                                val activeAgent = uiState.activeAgent

                                // 构造实际要发送的用户消息（考虑模板）
                                // 仅对第一条用户原始输入应用模板，自动循环的消息通常是系统生成的指令，不应用模板
                                val finalUserContent = if (activeAgent != null && !isAutoTriggered) {
                                    activeAgent.messageTemplate.replace("{{ message }}", inputContent)
                                } else {
                                    inputContent
                                }

                                // 收集上下文消息：最近的聊天历史
                                val contextMessages = uiState.messages.asReversed().map { msg ->
                                    val role = if (msg.author == authorMe) MessageRole.USER else MessageRole.ASSISTANT
                                    val parts = mutableListOf<UIMessagePart>()

                                    // 文本部分
                                    if (msg.content.isNotEmpty()) {
                                        parts.add(UIMessagePart.Text(msg.content))
                                    }

                                    // 图片部分（如果有）
                                    // 注意：历史消息中的图片可能需要从 URI 读取并转换为 Base64，或者如果是网络图片直接使用 URL
                                    // 这里简化处理，仅当有 imageUrl 且是 content 协议（本地图片）时尝试读取
                                    // 对于上下文中的历史图片，如果太大可能需要压缩或忽略，视 API 限制而定
                                    // 简单起见，这里假设只发送当前消息的图片，历史消息的图片暂不回传给 API（或者你可以实现回传逻辑）
                                    // 如果要支持多轮对话带图，需要在这里处理

                                    UIMessage(role = role, parts = parts)
                                }.takeLast(10).toMutableList()

                                // **组装完整的消息列表 (Prompt Construction)**
                                val messagesToSend = mutableListOf<UIMessage>()

                                // 1. 系统提示词 (System Prompt)
                                if (activeAgent != null && activeAgent.systemPrompt.isNotEmpty()) {
                                    messagesToSend.add(UIMessage(
                                        role = MessageRole.SYSTEM,
                                        parts = listOf(UIMessagePart.Text(activeAgent.systemPrompt))
                                    ))
                                }

                                // 2. 少样本示例 (Few-shot Examples)
                                if (activeAgent != null) {
                                    activeAgent.presetMessages.forEach { preset ->
                                        messagesToSend.add(UIMessage(
                                            role = preset.role,
                                            parts = listOf(UIMessagePart.Text(preset.content))
                                        ))
                                    }
                                }

                                // 4. 历史对话 (Conversation History)
                                messagesToSend.addAll(contextMessages)

                                // 5. 当前用户输入 (Current Input)
                                // 同样的逻辑：如果是新的一轮对话（非从历史中取出），我们需要确保它在列表中
                                // 如果从历史中取出的最后一条和当前输入重复（或 UI 已经添加了），需要小心处理
                                // 这里简化处理：直接追加最后一条，因为 contextMessages 是从 uiState.messages 构建的，而 uiState 已经在上面 addMessage 了
                                // 所以 contextMessages 理论上已经包含了最新一条。
                                // 但是，对于应用模板的情况，我们需要替换最后一条的内容。
                                if (messagesToSend.isNotEmpty() && messagesToSend.last().role == MessageRole.USER) {
                                    messagesToSend.removeAt(messagesToSend.lastIndex)
                                }

                                // 构建当前消息 parts
                                val currentParts = mutableListOf<UIMessagePart>()
                                if (finalUserContent.isNotEmpty()) {
                                    currentParts.add(UIMessagePart.Text(finalUserContent))
                                }

                                // 如果有图片（且不是自动循环），读取并转换为 Base64 添加到 parts
                                if (!isAutoTriggered) {
                                    // 查找最新一条用户消息（刚刚添加的）
                                    val lastUserMsg = uiState.messages.firstOrNull { it.author == authorMe }
                                    if (lastUserMsg?.imageUrl != null) {
                                        try {
                                            val imageUri = android.net.Uri.parse(lastUserMsg.imageUrl)
                                            // 读取图片并转 Base64
                                            val base64Image = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                                                inputStream.readBytes().toBase64()
                                            }
                                            if (base64Image != null) {
                                                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                                                currentParts.add(UIMessagePart.Image(url = "data:$mimeType;base64,$base64Image"))
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }

                                messagesToSend.add(UIMessage(
                                    role = MessageRole.USER,
                                    parts = currentParts
                                ))

                                // 添加初始空 AI 消息占位符
                                uiState.addMessage(
                                    Message("AI", "", timeNow, isLoading = true) // ✅ 标记为加载中
                                )

                                var fullResponse = ""

                                if (uiState.streamResponse) {
                                    // 调用 streamText 进行流式响应
                                    provider.streamText(
                                        providerSetting = providerSetting,
                                        messages = messagesToSend,
                                        params = TextGenerationParams(
                                            model = model,
                                            temperature = uiState.temperature,
                                            maxTokens = uiState.maxTokens
                                        )
                                    ).collect { chunk ->
                                        withContext(Dispatchers.Main) {
                                            val deltaContent = chunk.choices.firstOrNull()?.delta?.toText() ?: ""
                                            if (deltaContent.isNotEmpty()) {
                                                uiState.appendToLastMessage(deltaContent)
                                                fullResponse += deltaContent
                                            }
                                        }
                                    }
                                } else {
                                    // 调用 generateText 进行非流式响应
                                    val response = provider.generateText(
                                        providerSetting = providerSetting,
                                        messages = messagesToSend,
                                        params = TextGenerationParams(
                                            model = model,
                                            temperature = uiState.temperature,
                                            maxTokens = uiState.maxTokens
                                        )
                                    )
                                    val content = response.choices.firstOrNull()?.message?.toText() ?: ""
                                    fullResponse = content
                                    withContext(Dispatchers.Main) {
                                        if (content.isNotEmpty()) {
                                            uiState.appendToLastMessage(content)
                                        }
                                    }
                                }

                                // --- Auto-Loop Logic with Planner ---
                                if (uiState.isAutoLoopEnabled && loopCount < uiState.maxLoopCount && fullResponse.isNotBlank()) {

                                    // Step 2: 调用 Planner 模型生成下一步追问
                                    // 这里我们使用单独的非流式请求，不更新 UI，只为获取指令

                                    val plannerSystemPrompt = """
                                        You are a task planner agent.
                                        Analyze the previous AI response and generate a short, specific instruction for the next step to deepen the task or solve remaining issues.
                                        If the task appears complete or no further meaningful steps are needed, reply with exactly "STOP".
                                        Output ONLY the instruction or "STOP".
                                    """.trimIndent()

                                    val plannerMessages = listOf(
                                        UIMessage(role = MessageRole.SYSTEM, parts = listOf(UIMessagePart.Text(plannerSystemPrompt))),
                                        UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Previous Response:\n$fullResponse")))
                                    )

                                    // 使用相同的 provider/model 进行规划（也可以换一个更快的）
                                    val plannerResponse = provider.generateText(
                                        providerSetting = providerSetting,
                                        messages = plannerMessages,
                                        params = TextGenerationParams(
                                            model = model,
                                            temperature = 0.3f, // 降低温度以获得更确定的指令
                                            maxTokens = 100
                                        )
                                    )

                                    val nextInstruction = plannerResponse.choices.firstOrNull()?.message?.toText()?.trim() ?: "STOP"

                                    if (nextInstruction != "STOP" && nextInstruction.isNotEmpty()) {
                                        // 递归调用，使用 Planner 生成的指令
                                        processMessage(nextInstruction, isAutoTriggered = true, loopCount = loopCount + 1)
                                    }
                                }

                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    uiState.addMessage(
                                        Message("System", "Error: ${e.message}", timeNow)
                                    )
                                }
                                e.printStackTrace()
                            }
                        } else {
                            uiState.addMessage(
                                Message("System", "No AI Provider configured.", timeNow)
                            )
                        }
                    }

                    scope.launch {
                        processMessage(content)
                    }
                },
                resetScroll = {
                    scope.launch {
                        scrollState.scrollToItem(0)
                    }
                },
                // 让此元素处理填充，以便将 elevation 显示在导航栏后面
                modifier = Modifier.navigationBarsPadding().imePadding(),
                onStartRecording = {
                    // 检查权限并开始录音
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        uiState.isRecording = true
                        lastPartialLength = 0 // 重置部分长度跟踪器
                        scope.launch(Dispatchers.IO) {
                            youdaoWebSocket.connect()
                            audioRecorder.startRecording(
                                onAudioData = { data, size ->
                                    // ✅ 添加这一行日志
                                    Log.d("VoiceInput", "📤 Sending $size bytes to Youdao WebSocket")

                                    youdaoWebSocket.sendAudio(data, size)
                                },
                                onError = { error ->
                                    Log.e("VoiceInput", "❌ Recording error: ${error.message}")
                                    scope.launch {
                                        Toast.makeText(context, "录音失败: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    uiState.isRecording = false  // ✅ 修正
                                }
                            )
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = {
                    // 停止录音并关闭 socket
                    if (uiState.isRecording) {
                        uiState.isRecording = false
                        audioRecorder.stopRecording()
                        youdaoWebSocket.close()
                    }
                },
                isRecording = uiState.isRecording,
                textFieldValue = uiState.textFieldValue,
                onTextChanged = { uiState.textFieldValue = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelNameBar(
    channelName: String,
    channelMembers: Int,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavIconPressed: () -> Unit = { },
    onSettingsClicked: () -> Unit = { }
) {
    var functionalityNotAvailablePopupShown by remember { mutableStateOf(false) }
    if (functionalityNotAvailablePopupShown) {
        FunctionalityNotAvailablePopup { functionalityNotAvailablePopupShown = false }
    }
    JetchatAppBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        onNavIconPressed = onNavIconPressed,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 频道名称
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium,
                )
                // 成员数量
                Text(
                    text = stringResource(R.string.members, channelMembers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            // 设置图标
            IconButton(onClick = onSettingsClicked) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = "Settings"
                )
            }
            // 搜索图标
            Icon(
                painterResource(id = R.drawable.ic_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = { functionalityNotAvailablePopupShown = true })
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .height(24.dp),
                contentDescription = stringResource(id = R.string.search),
            )
            // 信息图标
            Icon(
                painterResource(id = R.drawable.ic_info),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = { functionalityNotAvailablePopupShown = true })
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .height(24.dp),
                contentDescription = stringResource(id = R.string.info),
            )
        },
    )
}

/**
 * 配置 AI 模型设置的对话框。
 *
 * 允许用户调整：
 * - Temperature (温度，创造性 vs 精确性)
 * - Max Tokens (最大 Token 数，响应长度)
 * - Stream Response (流式响应，启用/禁用流式传输)
 */
@Composable
fun ModelSettingsDialog(
    uiState: ConversationUiState,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Model Settings",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 温度设置滑块
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Precise",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f", uiState.temperature),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Creative",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = uiState.temperature,
                    onValueChange = { uiState.temperature = it },
                    valueRange = 0f..2f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 最大 Token 数设置滑块
                Text(
                    text = "Max Tokens",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Short",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${uiState.maxTokens}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Long",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = uiState.maxTokens.toFloat(),
                    onValueChange = { uiState.maxTokens = it.roundToInt() },
                    valueRange = 100f..4096f,
                    steps = 39, // (4096-100)/100 约等于 40 步
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 流式响应开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stream Response",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = uiState.streamResponse,
                        onCheckedChange = { uiState.streamResponse = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(24.dp))

                // Auto-Loop 开关
                Text(
                    text = "Agent Auto-Loop",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enable Auto-follow up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = uiState.isAutoLoopEnabled,
                        onCheckedChange = { uiState.isAutoLoopEnabled = it }
                    )
                }

                // Max Loop Count 滑块
                if (uiState.isAutoLoopEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Max Loops: ${uiState.maxLoopCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Slider(
                        value = uiState.maxLoopCount.toFloat(),
                        onValueChange = { uiState.maxLoopCount = it.roundToInt() },
                        valueRange = 1f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Done")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

const val ConversationTestTag = "ConversationTestTag"

@Composable
fun Messages(messages: List<Message>, navigateToProfile: (String) -> Unit, scrollState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Box(modifier = modifier) {

        val authorMe = stringResource(id = R.string.author_me)
        LazyColumn(
            reverseLayout = true,
            state = scrollState,
            modifier = Modifier
                .testTag(ConversationTestTag)
                .fillMaxSize(),
        ) {
            for (index in messages.indices) {
                val prevAuthor = messages.getOrNull(index - 1)?.author
                val nextAuthor = messages.getOrNull(index + 1)?.author
                val content = messages[index]
                val isFirstMessageByAuthor = prevAuthor != content.author
                val isLastMessageByAuthor = nextAuthor != content.author

                // 为了简单起见，硬编码日期分隔线
                if (index == messages.size - 1) {
                    item {
                        DayHeader("20 Aug")
                    }
                } else if (index == 2) {
                    item {
                        DayHeader("Today")
                    }
                }

                item {
                    Message(
                        onAuthorClick = { name -> navigateToProfile(name) },
                        msg = content,
                        isUserMe = content.author == authorMe,
                        isFirstMessageByAuthor = isFirstMessageByAuthor,
                        isLastMessageByAuthor = isLastMessageByAuthor,
                    )
                }
            }
        }
        // 当用户滚动超过阈值时显示跳转到底部按钮。
        // 转换为像素：
        val jumpThreshold = with(LocalDensity.current) {
            JumpToBottomThreshold.toPx()
        }

        // 如果第一个可见项不是第一个，或者偏移量大于阈值，则显示该按钮。
        val jumpToBottomButtonEnabled by remember {
            derivedStateOf {
                scrollState.firstVisibleItemIndex != 0 ||
                        scrollState.firstVisibleItemScrollOffset > jumpThreshold
            }
        }

        JumpToBottom(
            // 仅当滚动条不在底部时显示
            enabled = jumpToBottomButtonEnabled,
            onClicked = {
                scope.launch {
                    scrollState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun Message(
    onAuthorClick: (String) -> Unit,
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
) {
    val borderColor = if (isUserMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    val spaceBetweenAuthors = if (isLastMessageByAuthor) Modifier.padding(top = 8.dp) else Modifier

    // 根据用户类型调整布局方向
    Row(
        modifier = spaceBetweenAuthors.fillMaxWidth(),
        horizontalArrangement = if (isUserMe) Arrangement.End else Arrangement.Start
    ) {
        // AI 消息：头像在左侧
        if (!isUserMe && isLastMessageByAuthor) {
            Image(
                modifier = Modifier
                    .clickable(onClick = { onAuthorClick(msg.author) })
                    .padding(start = 16.dp, end = 8.dp)
                    .size(42.dp)
                    .border(1.5.dp, borderColor, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .align(Alignment.Top),
                painter = painterResource(id = msg.authorImage),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        } else if (!isUserMe) {
            // AI 消息头像占位
            Spacer(modifier = Modifier.width(58.dp))
        }

        // ✅ 修改：消息内容 - 让气泡自适应宽度
        // 不使用 Box，直接调用 AuthorAndTextMessage
        AuthorAndTextMessage(
            msg = msg,
            isUserMe = isUserMe,
            isFirstMessageByAuthor = isFirstMessageByAuthor,
            isLastMessageByAuthor = isLastMessageByAuthor,
            authorClicked = onAuthorClick,
            modifier = Modifier
                .widthIn(min = 48.dp, max = 280.dp)  // ✅ 设置最小和最大宽度
                .wrapContentWidth()  // ✅ 让内容决定宽度
        )

        // 用户消息：头像在右侧
        if (isUserMe && isLastMessageByAuthor) {
            Image(
                modifier = Modifier
                    .clickable(onClick = { onAuthorClick(msg.author) })
                    .padding(start = 8.dp, end = 16.dp)
                    .size(42.dp)
                    .border(1.5.dp, borderColor, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .align(Alignment.Top),
                painter = painterResource(id = msg.authorImage),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        } else if (isUserMe) {
            // 用户消息头像占位
            Spacer(modifier = Modifier.width(58.dp))
        }
    }
}

@Composable
fun AuthorAndTextMessage(
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
    authorClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 不再显示作者名和时间戳
        ChatItemBubble(msg, isUserMe, authorClicked = authorClicked)
        if (isFirstMessageByAuthor) {
            // 下一个作者之前的最后一个气泡
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // 气泡之间
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AuthorNameTimestamp(msg: Message) {
    // 为辅助功能合并作者和时间戳。
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = msg.author,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .alignBy(LastBaseline)
                .paddingFrom(LastBaseline, after = 8.dp), // 距离第一个气泡的空间
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = msg.timestamp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alignBy(LastBaseline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

//f3
private val UserChatBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 4.dp // 右下角尖角
)

// AI 消息气泡（左侧带尾巴）
private val AIChatBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 4.dp, // 左下角尖角
    bottomEnd = 20.dp
)

@Composable
fun DayHeader(dayString: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .height(16.dp),
    ) {
        DayHeaderLine()
        Text(
            text = dayString,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DayHeaderLine()
    }
}

@Composable
private fun RowScope.DayHeaderLine() {
    HorizontalDivider(
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

@Composable
fun ChatItemBubble(message: Message, isUserMe: Boolean, authorClicked: (String) -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val backgroundBubbleColor = if (isUserMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    // 根据用户类型选择不同的气泡形状
    val bubbleShape = if (isUserMe) UserChatBubbleShape else AIChatBubbleShape

    // ✅ 判断是否为纯文本内容
    val isPureText = isPureTextContent(message.content)

    Column {
        // ✅ 修改：整体结构 - 气泡 + 复制按钮分开布局
        // 消息气泡
        Surface(
            color = backgroundBubbleColor,
            shape = bubbleShape,
        ) {
            // 检查是否正在加载
            if (message.isLoading) {
                LoadingIndicator()  // 显示加载动画
            } else {
                ClickableMessage(
                    message = message,
                    isUserMe = isUserMe,
                    authorClicked = authorClicked,
                )
            }
        }

        // ✅ 新增：复制按钮 - 在气泡外部的右下角
        // 只在 AI 消息 + 非加载状态 + 有内容 + 纯文本时显示
        if (!isUserMe && !message.isLoading && message.content.isNotEmpty() && isPureText) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp, top = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制消息",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 显示图片（如果存在）
        if (message.imageUrl != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = backgroundBubbleColor,
                shape = bubbleShape,
            ) {
                AsyncImage(
                    model = message.imageUrl,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(160.dp),
                    contentDescription = stringResource(id = R.string.attached_image),
                )
            }
        } else if (message.image != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = backgroundBubbleColor,
                shape = bubbleShape,
            ) {
                Image(
                    painter = painterResource(message.image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(160.dp),
                    contentDescription = stringResource(id = R.string.attached_image),
                )
            }
        }
    }
}

@Composable
fun ClickableMessage(message: Message, isUserMe: Boolean, authorClicked: (String) -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val textColor = if (isUserMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val codeBlockBackground = if (isUserMe) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val codeTextColor = if (isUserMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.padding(16.dp)) {
        SimpleMarkdownRenderer(
            markdown = message.content,
            textColor = textColor,
            codeBlockBackground = codeBlockBackground,
            codeTextColor = codeTextColor,
            onCodeBlockCopy = { code ->
                clipboardManager.setText(AnnotatedString(code))
                Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SimpleMarkdownRenderer(
    markdown: String,
    textColor: Color,
    codeBlockBackground: Color,
    codeTextColor: Color,
    onCodeBlockCopy: (String) -> Unit
) {
    val codeBlockRegex = Regex("```([\\w]*)?\\n([\\s\\S]*?)```")
    val matches = codeBlockRegex.findAll(markdown).toList()

    if (matches.isEmpty()) {
        // 没有代码块，渲染带格式的文本
        RenderMarkdownText(markdown, textColor, codeBlockBackground)
    } else {
        // 有代码块，逐段渲染
        var lastIndex = 0

        // ✅ 修改：移除 fillMaxWidth()
        Column {
            matches.forEach { match ->
                val beforeCode = markdown.substring(lastIndex, match.range.first)
                if (beforeCode.isNotEmpty()) {
                    RenderMarkdownText(beforeCode, textColor, codeBlockBackground)
                }

                val language = match.groupValues[1].takeIf { it.isNotEmpty() } ?: "text"
                val code = match.groupValues[2].trim()

                Spacer(modifier = Modifier.height(8.dp))
                CodeBlockWithCopyButton(
                    code = code,
                    language = language,
                    onCopy = { onCodeBlockCopy(code) },
                    backgroundColor = codeBlockBackground,
                    textColor = codeTextColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                lastIndex = match.range.last + 1
            }

            val afterCode = markdown.substring(lastIndex)
            if (afterCode.isNotEmpty()) {
                RenderMarkdownText(afterCode, textColor, codeBlockBackground)
            }
        }
    }
}

@Composable
fun RenderMarkdownText(
    markdown: String,
    textColor: Color,
    codeBlockBackground: Color
) {
    val lines = markdown.split("\n")
    var inTable = false
    val tableRows = mutableListOf<List<String>>()

    // ✅ 修改：移除 fillMaxWidth()
    Column {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()

            // 处理表格
            if (line.contains("|") && line.trim().startsWith("|")) {
                if (!inTable) {
                    inTable = true
                    tableRows.clear()
                }
                tableRows.add(line.split("|").map { it.trim() }.filter { it.isNotEmpty() })
                i++
                continue
            } else if (inTable) {
                // 表格结束，渲染表格
                if (tableRows.size >= 2) {
                    RenderTable(tableRows, textColor, codeBlockBackground)
                }
                inTable = false
                tableRows.clear()
            }

            // 处理分隔线
            if (line.matches(Regex("^[-*_]{3,}$"))) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()  // ✅ 分隔线保留 fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = textColor.copy(alpha = 0.3f)
                )
                i++
                continue
            }

            // 处理标题
            val headerMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val text = headerMatch.groupValues[2]
                Text(
                    text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                    style = when (level) {
                        1 -> MaterialTheme.typography.headlineLarge
                        2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        4 -> MaterialTheme.typography.titleLarge
                        5 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    // ✅ 修改：移除 fillMaxWidth()
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                i++
                continue
            }

            // 处理无序列表
            val unorderedListMatch = Regex("^[*-]\\s+(.+)$").find(line)
            if (unorderedListMatch != null) {
                val text = unorderedListMatch.groupValues[1]
                // ✅ 修改：移除 fillMaxWidth()
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                i++
                continue
            }

            // 处理有序列表
            val orderedListMatch = Regex("^(\\d+)\\.\\s+(.+)$").find(line)
            if (orderedListMatch != null) {
                val number = orderedListMatch.groupValues[1]
                val text = orderedListMatch.groupValues[2]
                // ✅ 修改：移除 fillMaxWidth()
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "$number. ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                i++
                continue
            }

            // 处理引用块
            val quoteMatch = Regex("^>\\s+(.+)$").find(line)
            if (quoteMatch != null) {
                val text = quoteMatch.groupValues[1]
                Surface(
                    color = codeBlockBackground.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                    // ✅ 修改：移除 fillMaxWidth()
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .background(textColor.copy(alpha = 0.5f))
                        )
                        Text(
                            text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic
                            ),
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                        )
                    }
                }
                i++
                continue
            }

            // 处理普通段落
            if (line.isNotEmpty()) {
                Text(
                    text = parseInlineMarkdown(line, textColor, codeBlockBackground),
                    style = MaterialTheme.typography.bodyLarge,
                    // ✅ 修改：移除 fillMaxWidth()
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            i++
        }

        // 如果最后还有未渲染的表格
        if (inTable && tableRows.size >= 2) {
            RenderTable(tableRows, textColor, codeBlockBackground)
        }
    }
}

@Composable
fun RenderTable(
    rows: List<List<String>>,
    textColor: Color,
    codeBlockBackground: Color
) {
    Surface(
        color = codeBlockBackground.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            rows.forEachIndexed { rowIndex, cells ->
                // 跳过分隔行（第二行通常是 |---|---|）
                if (rowIndex == 1 && cells.all { it.matches(Regex("^:?-+:?$")) }) {
                    HorizontalDivider(color = textColor.copy(alpha = 0.3f))
                    return@forEachIndexed
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = parseInlineMarkdown(cell, textColor, codeBlockBackground),
                            style = if (rowIndex == 0) {
                                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        )
                    }
                }
                if (rowIndex < rows.lastIndex && rowIndex != 0) {
                    HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                }
            }
        }
    }
}

fun parseInlineMarkdown(text: String, baseColor: Color, codeBlockBackground: Color): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0

        // 定义所有匹配规则（优先级从高到低）
        val patterns = listOf(
            Regex("\\*\\*(.+?)\\*\\*") to "bold",        // **粗体**
            Regex("__(.+?)__") to "bold",                // __粗体__
            Regex("\\*(.+?)\\*") to "italic",            // *斜体*
            Regex("_(.+?)_") to "italic",                // _斜体_
            Regex("~~(.+?)~~") to "strikethrough",       // ~~删除线~~
            Regex("`(.+?)`") to "code",                  // `行内代码`
            Regex("\\[(.+?)\\]\\((.+?)\\)") to "link"    // [链接](url)
        )

        val allMatches = mutableListOf<Triple<IntRange, String, String>>()

        // 收集所有匹配
        patterns.forEach { (regex, type) ->
            regex.findAll(text).forEach { match ->
                val content = if (type == "link") {
                    match.groupValues[1] // 链接文本
                } else {
                    match.groupValues[1]
                }
                allMatches.add(Triple(match.range, type, content))
            }
        }

        // 按位置排序并去重（避免嵌套冲突）
        val sortedMatches = allMatches
            .sortedBy { it.first.first }
            .fold(mutableListOf<Triple<IntRange, String, String>>()) { acc, match ->
                if (acc.isEmpty() || match.first.first >= acc.last().first.last) {
                    acc.add(match)
                }
                acc
            }

        sortedMatches.forEach { (range, type, content) ->
            // 添加普通文本
            if (currentIndex < range.first) {
                append(text.substring(currentIndex, range.first))
            }

            when (type) {
                "bold" -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(content)
                    }
                }
                "italic" -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(content)
                    }
                }
                "strikethrough" -> {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, color = baseColor)) {
                        append(content)
                    }
                }
                "code" -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBlockBackground.copy(alpha = 0.3f),
                            color = baseColor
                        )
                    ) {
                        append(content)
                    }
                }
                "link" -> {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF2196F3), // 蓝色链接
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    ) {
                        append(content)
                    }
                }
            }

            currentIndex = range.last + 1
        }

        // 添加剩余文本
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}


@Composable
fun CodeBlockWithCopyButton(
    code: String,
    language: String,
    onCopy: () -> Unit,
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // 代码块顶部栏（语言标签 + 复制按钮）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 语言标签
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    // 复制按钮
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制代码",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = textColor.copy(alpha = 0.1f),
                    thickness = 1.dp
                )

                // 代码内容
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = textColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun ConversationPreview() {
    JetchatTheme {
        ConversationContent(
            uiState = exampleUiState,
            navigateToProfile = { },
        )
    }
}

@Preview
@Composable
fun ChannelBarPrev() {
    JetchatTheme {
        ChannelNameBar(channelName = "composers", channelMembers = 52)
    }
}

@Preview
@Composable
fun DayHeaderPrev() {
    DayHeader("Aug 6")
}

/**
 * AI 思考中的加载动画
 * 小巧的圆形进度指示器，包裹在气泡中
 */
@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .padding(16.dp)  // 气泡内边距，与正常消息一致
            .size(32.dp),    // 小巧的加载图标
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 判断消息内容是否为纯文本（不包含代码块、表格等复杂格式）
 *
 * @param content 消息内容
 * @return true 表示纯文本，false 表示包含复杂格式
 */
fun isPureTextContent(content: String): Boolean {
    if (content.isEmpty()) return false

    // 检查是否包含代码块
    val codeBlockRegex = Regex("```[\\s\\S]*?```")
    if (codeBlockRegex.containsMatchIn(content)) return false

    // 检查是否包含表格（Markdown 表格格式）
    val tableRegex = Regex("\\|.+\\|")
    if (tableRegex.containsMatchIn(content)) return false

    // 检查是否包含图片（Markdown 图片格式）
    val imageRegex = Regex("!\\[.*?\\]\\(.*?\\)")
    if (imageRegex.containsMatchIn(content)) return false

    // 其他可以接受的 Markdown 格式（粗体、斜体、链接等）
    // 这些不影响我们显示复制按钮
    return true
}

private val JumpToBottomThreshold = 56.dp

