package com.example.star.aiwork.ui.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.star.aiwork.data.remote.StreamingChatRemoteDataSource
import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ModelType
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import com.example.star.aiwork.ui.MainViewModel
import com.example.star.aiwork.ui.theme.JetchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class RealtimeConversationFragment : Fragment() {

    private val activityViewModel: MainViewModel by activityViewModels()

    private var youdaoWebSocket: YoudaoWebSocket? = null
    private var audioRecorder: AudioRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _transcription = mutableStateOf("")
    private val _aiResponse = mutableStateOf("")
    private val _isListening = mutableStateOf(false)
    private val _isProcessing = mutableStateOf(false)
    private val _isSpeaking = mutableStateOf(false)
    
    // 存储对话历史，用于上下文
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    
    // 用于 TTS 播放队列
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlayingQueue = false
    
    // 当前生成的文本缓冲区，用于检测句子边界
    private val ttsTextBuffer = StringBuilder()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startListening()
            } else {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                JetchatTheme {
                    RealtimeChatScreen(
                        transcription = _transcription.value,
                        aiResponse = _aiResponse.value,
                        isListening = _isListening.value,
                        isProcessing = _isProcessing.value,
                        isSpeaking = _isSpeaking.value,
                        onToggleListening = { toggleListening() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        audioRecorder = AudioRecorder(requireContext())
        initYoudao()
    }

    private fun initYoudao() {
        youdaoWebSocket = YoudaoWebSocket().apply {
            listener = object : YoudaoWebSocket.TranscriptionListener {
                override fun onTranscriptionReceived(text: String, isFinal: Boolean) {
                    lifecycleScope.launch {
                        _transcription.value = text
                        if (isFinal) {
                            Log.d("RealtimeChat", "Final transcription: $text")
                            stopListening() // 停止录音，开始处理
                            processUserMessage(text)
                        }
                    }
                }

                override fun onError(error: String) {
                    lifecycleScope.launch {
                        Toast.makeText(context, "ASR Error: $error", Toast.LENGTH_SHORT).show()
                        _isListening.value = false
                        stopListening()
                    }
                }
            }

            ttsListener = object : YoudaoWebSocket.TtsListener {
                override fun onTtsSuccess(audioData: ByteArray) {
                    lifecycleScope.launch {
                        Log.d("RealtimeChat", "TTS audio received, size: ${audioData.size}")
                        audioQueue.offer(audioData)
                        playNextInQueue()
                    }
                }

                override fun onTtsError(error: String) {
                    lifecycleScope.launch {
                        Log.e("RealtimeChat", "TTS Error: $error")
                        // 可以选择 toast 提示，或者忽略错误
                    }
                }
            }
        }
    }

    private fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startListening()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening() {
        _isListening.value = true
        _isSpeaking.value = false // 如果正在说话，打断它
        stopAudioPlayback()
        audioQueue.clear()
        ttsTextBuffer.clear()
        
        _transcription.value = "Listening..."
        _aiResponse.value = ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                youdaoWebSocket?.connect()
                // 给 WebSocket 一点时间连接
                kotlinx.coroutines.delay(500) 
                
                withContext(Dispatchers.Main) {
                    audioRecorder?.startRecording(
                        onAudioData = { data, size ->
                            youdaoWebSocket?.sendAudio(data, size)
                        },
                        onError = { e ->
                            Log.e("RealtimeChat", "Audio Recorder Error", e)
                            lifecycleScope.launch {
                                _isListening.value = false
                                Toast.makeText(context, "Recorder Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("RealtimeChat", "Start listening failed", e)
                withContext(Dispatchers.Main) {
                    _isListening.value = false
                }
            }
        }
    }

    private fun stopListening() {
        _isListening.value = false
        audioRecorder?.stopRecording()
        youdaoWebSocket?.close()
    }

    private fun processUserMessage(text: String) {
        _isProcessing.value = true
        _aiResponse.value = "Thinking..."

        val okHttpClient = defaultOkHttpClient()
        val sseClient = SseClient(okHttpClient)
        val remoteDataSource = StreamingChatRemoteDataSource(sseClient)
        val repository = AiRepositoryImpl(remoteDataSource, okHttpClient)

        val providerSettings = activityViewModel.providerSettings.value
        val activeProviderId = activityViewModel.activeProviderId.value
        val activeModelId = activityViewModel.activeModelId.value
        
        val provider = providerSettings.find { it.id == activeProviderId }
        
        if (provider == null || activeModelId.isNullOrEmpty()) {
            _aiResponse.value = "No provider or model selected"
            _isProcessing.value = false
            return
        }

        // 构建简单的上下文
        val history = conversationHistory.flatMap { (userMsg, assistantMsg) ->
            listOf(
                ChatDataItem(MessageRole.USER.value, userMsg),
                ChatDataItem(MessageRole.ASSISTANT.value, assistantMsg)
            )
        }.toMutableList()
        history.add(ChatDataItem(MessageRole.USER.value, text))

        lifecycleScope.launch {
            val responseBuilder = StringBuilder()
            
            try {
                // 构造 Model 对象
                val model = Model(
                    modelId = activeModelId,
                    displayName = activeModelId, // 使用 ID 作为显示名称
                    type = ModelType.CHAT
                )
                
                // 构造 TextGenerationParams
                val params = TextGenerationParams(
                    model = model,
                    temperature = 0.7f,
                    maxTokens = 1000
                )
                
                // repository.streamChat 返回的是 Flow<String>
                repository.streamChat(
                    history = history,
                    providerSetting = provider,
                    params = params,
                    taskId = UUID.randomUUID().toString()
                ).collect { chunk ->
                    responseBuilder.append(chunk)
                    _aiResponse.value = responseBuilder.toString()
                    
                    // 处理流式 TTS
                    processStreamForTts(chunk)
                }
                
                // 流结束，处理剩余的文本
                val remainingText = ttsTextBuffer.toString().trim()
                if (remainingText.isNotEmpty()) {
                    speak(remainingText)
                    ttsTextBuffer.clear()
                }

                val fullResponse = responseBuilder.toString()
                _aiResponse.value = fullResponse
                _isProcessing.value = false

                // 保存到简易历史 (仅保留最近几轮，避免过长)
                conversationHistory.add(text to fullResponse)
                if (conversationHistory.size > 5) {
                    conversationHistory.removeAt(0)
                }

            } catch (e: Exception) {
                Log.e("RealtimeChat", "AI Request failed", e)
                _aiResponse.value = "Error: ${e.message}"
                _isProcessing.value = false
            }
        }
    }
    
    /**
     * 处理流式文本，检测句子边界并触发 TTS
     */
    private fun processStreamForTts(chunk: String) {
        ttsTextBuffer.append(chunk)
        
        // 简单的标点符号分割逻辑
        val bufferContent = ttsTextBuffer.toString()
        val sentenceEndings = listOf("。", "！", "？", "\n", ".", "!", "?")
        
        var lastIndex = -1
        for (ending in sentenceEndings) {
            val index = bufferContent.lastIndexOf(ending)
            if (index > lastIndex) {
                lastIndex = index
            }
        }
        
        if (lastIndex != -1) {
            // 提取完整句子
            val sentence = bufferContent.substring(0, lastIndex + 1).trim()
            if (sentence.isNotEmpty()) {
                speak(sentence)
            }
            
            // 移除已处理部分
            ttsTextBuffer.delete(0, lastIndex + 1)
        }
    }
    
    private fun speak(text: String) {
        _isSpeaking.value = true
        Log.d("RealtimeChat", "Speaking: $text")
        youdaoWebSocket?.synthesize(text)
    }

    /**
     * 播放队列中的下一个音频
     */
    private fun playNextInQueue() {
        if (isPlayingQueue) return
        
        val audioData = audioQueue.poll()
        if (audioData != null) {
            isPlayingQueue = true
            playAudio(audioData)
        } else {
            // 队列为空，如果AI处理已完成且没有剩余文本，则停止说话状态
            if (!_isProcessing.value) {
                _isSpeaking.value = false
            }
        }
    }

    private fun playAudio(audioData: ByteArray) {
        try {
            // 将音频数据写入临时文件
            val tempFile = File.createTempFile("tts_audio", ".mp3", requireContext().cacheDir)
            val fos = FileOutputStream(tempFile)
            fos.write(audioData)
            fos.close()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    isPlayingQueue = false
                    it.release()
                    mediaPlayer = null
                    // 播放下一个
                    playNextInQueue()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("RealtimeChat", "MediaPlayer error: $what, $extra")
                    isPlayingQueue = false
                    playNextInQueue()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("RealtimeChat", "Audio playback failed", e)
            isPlayingQueue = false
            playNextInQueue()
        }
    }
    
    private fun stopAudioPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        audioQueue.clear()
        isPlayingQueue = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListening()
        stopAudioPlayback()
        audioRecorder?.cleanup()
    }
}

@Composable
fun RealtimeChatScreen(
    transcription: String,
    aiResponse: String,
    isListening: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    onToggleListening: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "实时对话",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // 用户说的话
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .weight(1f),
             colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "You:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = transcription, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI 回复
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .weight(1f),
             colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
             Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "AI:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = aiResponse, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // 状态指示
        Text(
            text = when {
                isListening -> "正在聆听..."
                isProcessing -> "正在思考..."
                isSpeaking -> "正在说话..."
                else -> "点击麦克风开始"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // 控制按钮
        FloatingActionButton(
            onClick = onToggleListening,
            containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Record",
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
