package com.example.star.aiwork.ui.conversation

import android.content.Context
import android.util.Log
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ModelType
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.ImageGenerationUseCase
import com.example.star.aiwork.domain.usecase.MessagePersistenceGateway
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.formatErrorMessage
import com.example.star.aiwork.ui.conversation.util.ConversationErrorHelper.isCancellationRelatedException
import com.example.star.aiwork.ui.conversation.util.ConversationLogHelper.logAllMessagesToSend
import com.example.star.aiwork.ui.conversation.logic.AutoLoopHandler
import com.example.star.aiwork.ui.conversation.logic.ImageGenerationHandler
import com.example.star.aiwork.ui.conversation.logic.MessageConstructionHelper
import com.example.star.aiwork.ui.conversation.logic.RollbackHandler
import com.example.star.aiwork.ui.conversation.logic.StreamingResponseHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Handles the business logic for processing messages in the conversation.
 * Includes sending messages to AI providers, handling fallbacks, and auto-looping agents.
 * 
 * Refactored to delegate responsibilities to smaller handlers:
 * - ImageGenerationHandler
 * - StreamingResponseHandler
 * - RollbackHandler
 * - AutoLoopHandler
 * - MessageConstructionHelper
 */
class ConversationLogic(
    private val uiState: ConversationUiState,
    private val context: Context,
    private val authorMe: String,
    private val timeNow: String,
    private val sendMessageUseCase: SendMessageUseCase,
    private val pauseStreamingUseCase: PauseStreamingUseCase,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val imageGenerationUseCase: ImageGenerationUseCase,
    private val sessionId: String,
    private val getProviderSettings: () -> List<ProviderSetting>,
    private val persistenceGateway: MessagePersistenceGateway? = null,
    private val onRenameSession: (sessionId: String, newName: String) -> Unit,
    private val onPersistNewChatSession: suspend (sessionId: String) -> Unit = { },
    private val isNewChat: (sessionId: String) -> Boolean = { false },
    private val onSessionUpdated: suspend (sessionId: String) -> Unit = { }
) {

    private var activeTaskId: String? = null
    // 用于保存流式收集协程的 Job，以便可以立即取消
    private var streamingJob: Job? = null
    // 用于保存提示消息流式显示的 Job，以便可以立即取消
    private var hintTypingJob: Job? = null
    // 使用 uiState 的协程作用域，这样每个会话可以管理自己的协程
    private val streamingScope: CoroutineScope = uiState.coroutineScope
    // 标记是否已被取消，用于非流式模式下避免显示已收集的内容
    @Volatile private var isCancelled = false

    // Handlers
    private val imageGenerationHandler = ImageGenerationHandler(
        uiState = uiState,
        imageGenerationUseCase = imageGenerationUseCase,
        persistenceGateway = persistenceGateway,
        sessionId = sessionId,
        timeNow = timeNow,
        onSessionUpdated = onSessionUpdated
    )

    private val streamingResponseHandler = StreamingResponseHandler(
        uiState = uiState,
        persistenceGateway = persistenceGateway,
        sessionId = sessionId,
        timeNow = timeNow,
        onSessionUpdated = onSessionUpdated
    )

    private val rollbackHandler = RollbackHandler(
        uiState = uiState,
        rollbackMessageUseCase = rollbackMessageUseCase,
        streamingResponseHandler = streamingResponseHandler,
        sessionId = sessionId,
        authorMe = authorMe,
        timeNow = timeNow
    )

    private val autoLoopHandler = AutoLoopHandler(
        uiState = uiState,
        sendMessageUseCase = sendMessageUseCase,
        getProviderSettings = getProviderSettings,
        timeNow = timeNow
    )

    /**
     * 取消当前的流式生成。
     */
    suspend fun cancelStreaming() {
        // 立即取消流式收集协程和提示消息的流式显示协程
        isCancelled = true
        streamingJob?.cancel()
        streamingJob = null
        hintTypingJob?.cancel() // 取消提示消息的流式显示
        hintTypingJob = null
        
        // 根据流式模式决定处理方式
        val currentContent: String
        withContext(Dispatchers.Main) {
            if (uiState.streamResponse) {
                // 流式模式：在消息末尾追加取消提示
                uiState.appendToLastMessage("\n（已取消生成）")
                uiState.updateLastMessageLoadingState(false)
                // 获取当前消息内容（包含取消提示）
                val lastMessage = uiState.messages.firstOrNull { it.author == "AI" }
                currentContent = lastMessage?.content ?: ""
            } else {
                // 非流式模式：清空已收集的内容，只显示取消提示
                uiState.replaceLastMessageContent("（已取消生成）")
                uiState.updateLastMessageLoadingState(false)
                currentContent = "（已取消生成）"
            }
        }
        
        // 保存当前内容到数据库（包含取消提示）
        if (currentContent.isNotEmpty()) {
            persistenceGateway?.replaceLastAssistantMessage(
                sessionId,
                ChatDataItem(
                    role = MessageRole.ASSISTANT.name.lowercase(),
                    content = currentContent
                )
            )
        }
        
        val taskId = activeTaskId
        if (taskId != null) {
            // 无论成功还是失败，都要清除状态
            pauseStreamingUseCase(taskId).fold(
                onSuccess = {
                    activeTaskId = null
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                    }
                },
                onFailure = { error ->
                    // 取消失败时也清除状态，但不显示错误（取消操作本身不应该报错）
                    activeTaskId = null
                    withContext(Dispatchers.Main) {
                        uiState.isGenerating = false
                    }
                    // 记录日志但不显示给用户
                    android.util.Log.d("ConversationLogic", "Cancel streaming failed: ${error.message}")
                }
            )
        } else {
            // 如果没有活跃任务，直接清除状态
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
            }
        }
    }

    suspend fun processMessage(
        inputContent: String,
        providerSetting: ProviderSetting?,
        model: Model?,
        isAutoTriggered: Boolean = false,
        loopCount: Int = 0,
        retrieveKnowledge: suspend (String) -> String = { "" },
        isRetry: Boolean = false
    ) {
        // Session management (New Chat / Rename)
        if (isNewChat(sessionId)) {
            onPersistNewChatSession(sessionId)
        }
        // ADDED: Auto-rename session logic
        if (!isAutoTriggered && (uiState.channelName == "New Chat" || uiState.channelName == "新聊天" || uiState.channelName == "新会话" || uiState.channelName == "new chat") && uiState.messages.none { it.author == authorMe }) {
            val newTitle = inputContent.take(20).trim()
            if (newTitle.isNotBlank()) {
                onRenameSession(sessionId, newTitle)
                onSessionUpdated(sessionId)
                Log.d("ConversationLogic", "✅ [Auto-Rename] 重命名完成，已调用 onSessionUpdated")
            }
        }

        // UI Update: Display User Message
        if (!isRetry) {
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
                uiState.selectedImageUri = null
            } else {
                uiState.addMessage(Message(authorMe, "[Auto-Loop ${loopCount}] $inputContent", timeNow))
            }
        }

        // 2. Call LLM or Image Generation
        if (providerSetting != null && model != null) {
            try {
                withContext(Dispatchers.Main) {
                    uiState.isGenerating = true
                }
                
                if (model.type == ModelType.IMAGE) {
                    imageGenerationHandler.generateImage(providerSetting, model, inputContent)
                    return
                }

                // Construct Messages
                val messagesToSend = MessageConstructionHelper.constructMessages(
                    uiState = uiState,
                    authorMe = authorMe,
                    inputContent = inputContent,
                    isAutoTriggered = isAutoTriggered,
                    activeAgent = uiState.activeAgent,
                    retrieveKnowledge = retrieveKnowledge,
                    context = context
                )

                val params = TextGenerationParams(
                    model = model,
                    temperature = uiState.temperature,
                    maxTokens = uiState.maxTokens
                )

                // Add empty AI message placeholder
                withContext(Dispatchers.Main) {
                    uiState.addMessage(Message("AI", "", timeNow, isLoading = true))
                }

                val historyChat: List<ChatDataItem> = messagesToSend.dropLast(1).map { message ->
                    MessageConstructionHelper.toChatDataItem(message)
                }
                val userMessage: ChatDataItem = MessageConstructionHelper.toChatDataItem(messagesToSend.last())

                logAllMessagesToSend(
                    sessionId = sessionId,
                    model = model,
                    params = params,
                    messagesToSend = messagesToSend,
                    historyChat = historyChat,
                    userMessage = userMessage,
                    isAutoTriggered = isAutoTriggered,
                    loopCount = loopCount
                )

                val sendResult = sendMessageUseCase(
                    sessionId = sessionId,
                    userMessage = userMessage,
                    history = historyChat,
                    providerSetting = providerSetting,
                    params = params
                )

                activeTaskId = sendResult.taskId
                isCancelled = false

                // Streaming Response Handling
                val fullResponse = streamingResponseHandler.handleStreaming(
                    scope = streamingScope,
                    stream = sendResult.stream,
                    isCancelledCheck = { isCancelled },
                    onJobCreated = { job, hintJob ->
                        streamingJob = job
                        hintTypingJob = hintJob
                    }
                )

                // Clear Jobs references after completion
                streamingJob = null
                hintTypingJob = null

                // --- Auto-Loop Logic with Planner ---
                if (uiState.isAutoLoopEnabled && loopCount < uiState.maxLoopCount && fullResponse.isNotBlank()) {
                    autoLoopHandler.handleAutoLoop(
                        fullResponse = fullResponse,
                        loopCount = loopCount,
                        currentProviderSetting = providerSetting,
                        currentModel = model,
                        retrieveKnowledge = retrieveKnowledge,
                        onProcessMessage = { content, pSetting, mod, auto, count, knowledge ->
                            processMessage(content, pSetting, mod, auto, count, knowledge)
                        }
                    )
                }

            } catch (e: Exception) {
                handleError(e, inputContent, providerSetting, isAutoTriggered, loopCount, retrieveKnowledge)
            }
        } else {
             uiState.addMessage(
                Message("System", "No AI Provider configured.", timeNow)
            )
            uiState.isGenerating = false
        }
    }

    private suspend fun handleError(
        e: Exception,
        inputContent: String,
        providerSetting: ProviderSetting?,
        isAutoTriggered: Boolean,
        loopCount: Int,
        retrieveKnowledge: suspend (String) -> String
    ) {
        if (e is CancellationException || isCancellationRelatedException(e)) {
            withContext(Dispatchers.Main) {
                uiState.isGenerating = false
                uiState.updateLastMessageLoadingState(false)
            }
            return
        }

        // Fallback logic
        val isCurrentOllama = providerSetting is ProviderSetting.Ollama
        if (!isCurrentOllama) {
            val ollamaProvider = getProviderSettings().find { it is ProviderSetting.Ollama }
            if (ollamaProvider != null && ollamaProvider.models.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    uiState.updateLastMessageLoadingState(false)
                    uiState.addMessage(
                        Message("System", "Request failed (${e.message}). Fallback to local Ollama...", timeNow)
                    )
                }
                processMessage(
                    inputContent = inputContent,
                    providerSetting = ollamaProvider,
                    model = ollamaProvider.models.first(),
                    isAutoTriggered = isAutoTriggered,
                    loopCount = loopCount,
                    retrieveKnowledge = retrieveKnowledge,
                    isRetry = true
                )
                return
            }
        }

        withContext(Dispatchers.Main) {
            uiState.updateLastMessageLoadingState(false)
            uiState.isGenerating = false
            if (uiState.messages.isNotEmpty() && 
                uiState.messages[0].author == "AI" && 
                uiState.messages[0].content.isBlank()) {
                uiState.removeFirstMessage()
            }
            
            val errorMessage = formatErrorMessage(e)
            uiState.addMessage(
                Message("System", errorMessage, timeNow)
            )
            uiState.isGenerating = false
            uiState.updateLastMessageLoadingState(false)
        }
        e.printStackTrace()
    }
    
    /**
     * 回滚最后一条助手消息并重新生成
     */
    suspend fun rollbackAndRegenerate(
        providerSetting: ProviderSetting?,
        model: Model?,
        retrieveKnowledge: suspend (String) -> String = { "" }
    ) {
        rollbackHandler.rollbackAndRegenerate(
            providerSetting = providerSetting,
            model = model,
            scope = streamingScope,
            isCancelledCheck = { isCancelled },
            onJobCreated = { job, hintJob ->
                streamingJob = job
                hintTypingJob = hintJob
            },
            onTaskIdUpdated = { taskId ->
                activeTaskId = taskId
            }
        )
    }
}
