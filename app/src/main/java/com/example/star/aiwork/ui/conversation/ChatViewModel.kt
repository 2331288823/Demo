package com.example.star.aiwork.ui.conversation

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.star.aiwork.data.local.datasource.DraftLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl
import com.example.star.aiwork.domain.model.MessageEntity
import com.example.star.aiwork.domain.model.MessageMetadata
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.MessageType
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.model.SessionMetadata
import com.example.star.aiwork.domain.usecase.draft.GetDraftUseCase
import com.example.star.aiwork.domain.usecase.draft.UpdateDraftUseCase
import com.example.star.aiwork.domain.usecase.message.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.message.SendMessageUseCase
import com.example.star.aiwork.domain.usecase.message.ObserveMessagesUseCase
import com.example.star.aiwork.domain.usecase.session.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.*
import kotlinx.coroutines.flow.SharingStarted

class ChatViewModel(
    private val getSessionListUseCase: GetSessionListUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val renameSessionUseCase: RenameSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val pinSessionUseCase: PinSessionUseCase,
    private val archiveSessionUseCase: ArchiveSessionUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val rollbackMessageUseCase: RollbackMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val getDraftUseCase: GetDraftUseCase,
    private val updateDraftUseCase: UpdateDraftUseCase,
    private val searchSessionsUseCase: SearchSessionsUseCase
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<SessionEntity?>(null)
    val currentSession: StateFlow<SessionEntity?> = _currentSession.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 独立的搜索结果列表，不会影响drawer中的sessions列表
    private val _searchResults = MutableStateFlow<List<SessionEntity>>(emptyList())
    val searchResults: StateFlow<List<SessionEntity>> = _searchResults.asStateFlow()
    
    // 管理搜索任务的Job，用于取消之前的搜索任务
    private var searchJob: Job? = null

    // 跟踪临时创建的会话（isNewChat标记）
    private val _newChatSessions = MutableStateFlow<Set<String>>(emptySet())
    val newChatSessions: StateFlow<Set<String>> = _newChatSessions.asStateFlow()

    // 使用 flatMapLatest 自动根据 currentSession 切换消息流
    val messages: StateFlow<List<MessageEntity>> = _currentSession
        .flatMapLatest { session ->
            if (session != null) {
                observeMessagesUseCase(session.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft.asStateFlow()

    // 为每个会话管理独立的 ConversationUiState
    private val _sessionUiStates = MutableStateFlow<Map<String, ConversationUiState>>(emptyMap())
    
    /**
     * 获取或创建指定会话的 ConversationUiState
     */
    fun getOrCreateSessionUiState(sessionId: String, sessionName: String): ConversationUiState {
        val currentStates = _sessionUiStates.value
        return currentStates[sessionId] ?: run {
            val newUiState = ConversationUiState(
                channelName = sessionName.ifBlank { "新对话" },
                channelMembers = 1,
                initialMessages = emptyList()
            )
            _sessionUiStates.value = currentStates + (sessionId to newUiState)
            newUiState
        }
    }
    
    /**
     * 获取指定会话的 ConversationUiState（如果不存在则返回 null）
     */
    fun getSessionUiState(sessionId: String): ConversationUiState? {
        return _sessionUiStates.value[sessionId]
    }

    init {
        loadSessions()
        // 启动时如果没有当前会话，创建一个临时会话
        // 这样用户可以直接在空对话页面发送消息，消息不会丢失
        if (_currentSession.value == null) {
            createTemporarySession("新聊天")
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            getSessionListUseCase().collect { list ->
                _sessions.value = list
                // 不再自动选择第一个会话，始终显示空对话页面
                // if (_currentSession.value == null) {
                //     _currentSession.value = list.firstOrNull()
                // }
            }
        }
    }
    
    /**
     * 手动刷新会话列表（用于在会话更新后刷新 drawer 中的列表）
     */
    suspend fun refreshSessions() {
        getSessionListUseCase().firstOrNull()?.let { list ->
            _sessions.value = list
        }
    }
    
    fun searchSessions(query: String) {
        _searchQuery.value = query
        
        // 取消之前的搜索任务
        searchJob?.cancel()
        
        if (query.isBlank()) {
            // 如果查询为空，清空搜索结果
            _searchResults.value = emptyList()
            searchJob = null
        } else {
            // 搜索时更新搜索结果，不影响原始的sessions列表
            searchJob = viewModelScope.launch {
                searchSessionsUseCase(query).collect { list ->
                    _searchResults.value = list
                }
            }
        }
    }

    fun createSession(name: String) {
        viewModelScope.launch {
            val session = SessionEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                pinned = false,
                archived = false,
                metadata = SessionMetadata()
            )
            createSessionUseCase(session)
            _currentSession.value = session
            // messages 会自动通过 flatMapLatest 加载，无需手动调用
            loadDraft()
        }
    }

    /**
     * 创建临时session（仅在内存中，不保存到数据库）
     * 只有当用户发送第一条消息时，才会真正保存到数据库
     */
    fun createTemporarySession(name: String) {
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pinned = false,
            archived = false,
            metadata = SessionMetadata()
        )
        _currentSession.value = session
        // 将 sessionId 添加到 _newChatSessions，标记为新会话
        _newChatSessions.value = _newChatSessions.value + session.id
        // 临时session不加载草稿，因为还没有保存到数据库
        _draft.value = null
    }
    
    /**
     * 更新会话的关联 Agent
     */
    fun updateSessionAgent(sessionId: String, agentId: String?) {
        viewModelScope.launch {
            val session = if (_currentSession.value?.id == sessionId) {
                _currentSession.value
            } else {
                // 如果不是当前会话，需要从列表中查找（或者从数据库重新加载，这里简化为查找当前列表）
                _sessions.value.find { it.id == sessionId }
            }

            if (session != null) {
                val newMetadata = session.metadata.copy(agentId = agentId)
                val updatedSession = session.copy(metadata = newMetadata, updatedAt = System.currentTimeMillis())
                
                createSessionUseCase(updatedSession) // 使用 createSessionUseCase 进行更新 (upsert)
                
                if (_currentSession.value?.id == sessionId) {
                    _currentSession.value = updatedSession
                }
                // 更新 UI State 中的 agent (由 NavActivity 观察并设置，但这里最好也触发一下)
                // NavActivity 会监听 currentSession 的变化并处理 UI 更新
            }
        }
    }

    fun renameSession(newName: String) {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            renameSessionUseCase(session.id, newName)
            _currentSession.value = session.copy(name = newName)
            // 更新 UI 状态的 channelName
            getSessionUiState(session.id)?.channelName = newName.ifBlank { "新对话" }
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "🔄 [renameSession] 开始重命名会话")
            Log.d("ChatViewModel", "  - 会话ID: $sessionId")
            Log.d("ChatViewModel", "  - 新名称: $newName")
            
            val currentSession = _currentSession.value
            // 如果会话不存在于数据库中，但存在于当前会话中，先创建会话
            if (currentSession?.id == sessionId) {
                // 检查会话是否已持久化（通过检查 sessions 列表中是否存在）
                val isPersisted = _sessions.value.any { it.id == sessionId }
                if (!isPersisted) {
                    Log.d("ChatViewModel", "  - 会话尚未持久化，先创建会话")
                    // 先创建会话（使用新名称）
                    val sessionToCreate = currentSession.copy(
                        name = newName,
                        updatedAt = System.currentTimeMillis()
                    )
                    createSessionUseCase(sessionToCreate)
                    Log.d("ChatViewModel", "  - 会话已创建，名称: $newName")
                } else {
                    // 会话已存在，执行重命名
                    renameSessionUseCase(sessionId, newName)
                }
                // 更新当前会话状态
                _currentSession.value = currentSession.copy(name = newName)
                Log.d("ChatViewModel", "  - 已更新当前会话状态: $newName")
            } else {
                // 不是当前会话，直接重命名
                renameSessionUseCase(sessionId, newName)
            }
            
            // 更新 UI 状态的 channelName
            getSessionUiState(sessionId)?.channelName = newName.ifBlank { "新对话" }
            // 刷新会话列表
            loadSessions()
            Log.d("ChatViewModel", "✅ [renameSession] 重命名完成，已调用 loadSessions()")
        }
    }

    fun deleteCurrentSession() {
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            deleteSessionUseCase(session.id)
            _currentSession.value = null
            // messages 会自动清空（通过 flatMapLatest 返回 emptyList）
            _draft.value = null
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            deleteSessionUseCase(sessionId)
            // 如果删除的是当前会话，清空当前会话状态
            val currentSession = _currentSession.value
            if (currentSession?.id == sessionId) {
                _currentSession.value = null
                // messages 会自动清空（通过 flatMapLatest 返回 emptyList）
                _draft.value = null
            }
            // 清理该会话的 UI 状态
            _sessionUiStates.value = _sessionUiStates.value - sessionId
            // 刷新会话列表
            loadSessions()
        }
    }

    fun pinSession(session: SessionEntity, pinned: Boolean) {
        viewModelScope.launch {
            pinSessionUseCase(session.id, pinned)
        }
    }

    fun pinSession(sessionId: String, pinned: Boolean) {
        viewModelScope.launch {
            pinSessionUseCase(sessionId, pinned)
            // 刷新会话列表
            loadSessions()
        }
    }

    fun archiveSession(sessionId: String, archived: Boolean) {
        viewModelScope.launch {
            archiveSessionUseCase(sessionId, archived)
            // 刷新会话列表
            loadSessions()
        }
    }

    fun sendMessage(content: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
//            // 检查session是否已保存到数据库（通过检查sessions列表中是否包含当前session）
//            var isSessionSaved = _sessions.value.any { it.id == session.id }
//
//            // 如果session还未保存，先保存到数据库
//            if (!isSessionSaved) {
//                createSessionUseCase(session)
//                // 等待Flow更新，确保session被包含在列表中
//                // 使用first()等待sessions列表的下一次更新
//                getSessionListUseCase().first { list -> list.any { it.id == session.id } }
//                isSessionSaved = true
//            }
            
            val message = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                role = MessageRole.USER,
                type = MessageType.TEXT,
                content = content,
                metadata = MessageMetadata(
                    localFilePath = null,
                    remoteUrl = null,
                    modelName = null,
                    tokenUsage = null,
                    errorInfo = null
                ),
                parentMessageId = null,
                createdAt = System.currentTimeMillis(),
                status = com.example.star.aiwork.domain.model.MessageStatus.SENDING
            )
            sendMessageUseCase(message)
        }
    }

    fun rollbackLastMessage() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val lastMessage = messages.value.lastOrNull() ?: return@launch
            rollbackMessageUseCase(lastMessage.id)
        }
    }

    // loadMessages 方法已不再需要，因为 messages 通过 flatMapLatest 自动加载
    // 保留此方法以保持向后兼容，但实际不会执行任何操作
    @Deprecated("Messages are now automatically loaded via flatMapLatest", ReplaceWith(""))
    fun loadMessages(sessionId: String) {
        // 消息现在通过 flatMapLatest 自动加载，无需手动调用
    }

    fun saveDraft(content: String) {
        val session = _currentSession.value ?: return
//        viewModelScope.launch {
//            // 只有当session已保存到数据库时，才保存草稿
//            val isSessionSaved = _sessions.value.any { it.id == session.id }
//            if (isSessionSaved) {
//                updateDraftUseCase(session.id, content)
//                _draft.value = content
//            } else {
//                // 临时session的草稿只保存在内存中
//                _draft.value = content
//            }
//        }
        viewModelScope.launch {
            updateDraftUseCase(session.id, content)
            _draft.value = content
        }
    }

    fun loadDraft() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val draftEntity = getDraftUseCase(session.id)
            _draft.value = draftEntity?.content
        }
    }

    fun selectSession(session: SessionEntity) {
        _currentSession.value = session
        // messages 会自动通过 flatMapLatest 加载，无需手动调用 loadMessages
        // 确保会话的 UI 状态已创建
        getOrCreateSessionUiState(session.id, session.name)
        loadDraft()
    }

    /**
     * 检查会话是否为新创建的临时会话
     */
    fun isNewChat(sessionId: String): Boolean {
        return _newChatSessions.value.contains(sessionId)
    }

    /**
     * 持久化新会话并取消isNewChat标记
     */
    suspend fun persistNewChatSession(sessionId: String) {
        val session = _currentSession.value
        if (session != null && session.id == sessionId && _newChatSessions.value.contains(sessionId)) {
            // 持久化会话
            createSessionUseCase(session)
            // 更新会话列表
            _sessions.value = _sessions.value + session
            // 取消isNewChat标记
            _newChatSessions.value = _newChatSessions.value - sessionId
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as Application

                // Create DataSources
                val sessionLocalDataSource = SessionLocalDataSourceImpl(application)
                val messageLocalDataSource = MessageLocalDataSourceImpl(application)
                val draftLocalDataSource = DraftLocalDataSourceImpl(application)

                // Create UseCases
                val getSessionListUseCase = GetSessionListUseCase(sessionLocalDataSource)
                val createSessionUseCase = CreateSessionUseCase(sessionLocalDataSource)
                val renameSessionUseCase = RenameSessionUseCase(sessionLocalDataSource)
                val deleteSessionUseCase = DeleteSessionUseCase(sessionLocalDataSource, messageLocalDataSource, draftLocalDataSource)
                val pinSessionUseCase = PinSessionUseCase(sessionLocalDataSource)
                val archiveSessionUseCase = ArchiveSessionUseCase(sessionLocalDataSource)
                val searchSessionsUseCase = SearchSessionsUseCase(sessionLocalDataSource)

                val sendMessageUseCase = SendMessageUseCase(messageLocalDataSource, sessionLocalDataSource)
                val rollbackMessageUseCase = RollbackMessageUseCase(messageLocalDataSource)
                val observeMessagesUseCase = ObserveMessagesUseCase(messageLocalDataSource)

                val getDraftUseCase = GetDraftUseCase(draftLocalDataSource)
                val updateDraftUseCase = UpdateDraftUseCase(draftLocalDataSource)

                return ChatViewModel(
                    getSessionListUseCase,
                    createSessionUseCase,
                    renameSessionUseCase,
                    deleteSessionUseCase,
                    pinSessionUseCase,
                    archiveSessionUseCase,
                    sendMessageUseCase,
                    rollbackMessageUseCase,
                    observeMessagesUseCase,
                    getDraftUseCase,
                    updateDraftUseCase,
                    searchSessionsUseCase
                ) as T
            }
        }
    }
}

