package com.example.star.aiwork.infra.embedding

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Embedding 服务类，用于生成文本的向量嵌入。
 * 
 * 此类封装了 TensorFlow Lite 模型的加载和推理逻辑，为 data 层提供简单的接口。
 * 
 * @param context Android Context 对象，用于访问应用的 assets 资源。
 * @param modelPath 模型文件路径，默认为 "1.tflite"
 * 
 * 使用示例：
 * ```
 * val embeddingService = EmbeddingService(context)
 * // 在协程中调用
 * val embedding = withContext(Dispatchers.IO) {
 *     embeddingService.embed("Hello, world!")
 * }
 * // 或者在 ViewModel/Repository 中：
 * viewModelScope.launch {
 *     val embedding = embeddingService.embed("Hello, world!")
 * }
 * ```
 */
class EmbeddingService(
    private val context: Context,
    private val modelPath: String = "1.tflite"
) {
    
    private val TAG = "EmbeddingService"
    private var interpreter: Interpreter? = null
    private var isInitialized = false
    // 保存 FileInputStream 引用，以便在 close() 时正确关闭
    // 注意：MappedByteBuffer 依赖于 FileInputStream，所以需要保持打开状态
    private var modelInputStream: FileInputStream? = null
    private var modelFileDescriptor: android.content.res.AssetFileDescriptor? = null
    
    /**
     * 从 assets 文件夹加载模型文件。
     * 
     * 注意：MappedByteBuffer 依赖于 FileInputStream，所以 FileInputStream 需要保持打开状态。
     * 我们将其保存为成员变量，以便在 close() 时正确关闭。
     */
    private fun loadModelFile(path: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        
        // 保存引用以便后续关闭
        modelFileDescriptor = fileDescriptor
        modelInputStream = inputStream
        
        try {
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            return mappedBuffer
        } catch (e: Exception) {
            // 如果映射失败，关闭已打开的资源
            try {
                inputStream.close()
            } catch (closeError: Exception) {
                // Ignore
            }
            try {
                fileDescriptor.close()
            } catch (closeError: Exception) {
                // Ignore
            }
            modelInputStream = null
            modelFileDescriptor = null
            throw e
        }
    }
    
    /**
     * 初始化模型。
     * 如果模型已经初始化，则不会重复初始化。
     * 在后台线程执行以避免阻塞 UI。
     */
    private suspend fun ensureInitialized() {
        if (isInitialized && interpreter != null) {
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                val modelBuffer = loadModelFile(modelPath)
                interpreter = Interpreter(modelBuffer)
                isInitialized = true
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * 生成文本的向量嵌入。
     * 
     * 此方法在后台线程执行，不会阻塞 UI 线程。
     * 
     * @param text 输入的文本句子
     * @return 向量嵌入数组，如果生成失败则返回 null
     */
    suspend fun embed(text: String): FloatArray? {
        return withContext(Dispatchers.Default) {
            // 输入验证
            if (text.isBlank()) {
                return@withContext null
            }
            
            val textLength = text.length
            
            // 检查文本长度，避免过长导致问题
            val MAX_TEXT_LENGTH = 512  // 根据模型调整
            
            ensureInitialized()
            
            val interp = interpreter ?: run {
                return@withContext null
            }
            
            try {
            // 获取输入输出张量信息
            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)
            
            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()
            val inputDataType = inputTensor.dataType()
            val outputDataType = outputTensor.dataType()
            
            // 根据输入数据类型和形状创建输入缓冲区
            val inputSize = inputShape.fold(1) { acc, dim -> acc * dim }
            val sequenceLength = if (inputShape.size > 1) inputShape[1] else inputShape[0]
            
            // 检查文本长度是否超过序列长度
            val processedText = if (textLength > sequenceLength) {
                text.take(sequenceLength)
            } else {
                text
            }
            
            // 将字符码点映射到安全的 token ID 范围
            // 使用模运算将任意字符码点映射到词汇表范围内（常见词汇表大小 30000）
            // 这样可以避免索引越界，同时保持一定的字符区分度
            fun mapCharToTokenId(char: Char, vocabSize: Int = 5000): Int {
                val code = char.code
                // 使用模运算将字符码点映射到词汇表范围内
                return (code % vocabSize).coerceIn(0, vocabSize - 1)
            }
            
            val inputBuffer = when (inputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.INT8 -> {
                    ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.UINT8 -> {
                    ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
                }
                else -> {
                    return@withContext null
                }
            }
            
            // 填充输入数据
            when (inputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    val floatBuffer = inputBuffer.asFloatBuffer()
                    try {
                        // 将字符码点转换为浮点数，并限制范围
                        processedText.map { char ->
                            val code = char.code
                            // 限制范围，避免超出模型期望的值
                            (code.toFloat() / 1000f).coerceIn(-1f, 1f)
                        }.take(sequenceLength).forEach { 
                            floatBuffer.put(it) 
                        }
                        // 填充剩余位置
                        while (floatBuffer.position() < inputSize) {
                            floatBuffer.put(0f)
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    val intBuffer = inputBuffer.asIntBuffer()
                    try {
                        // 使用更保守的 token ID 映射
                        // 将字符码点映射到模型词汇表范围内（使用较小的词汇表大小以确保安全）
                        val vocabSize = 10000  // 使用更小的词汇表大小，更安全
                        val charCodes = processedText.map { char ->
                            val tokenId = mapCharToTokenId(char, vocabSize)
                            // 双重检查：确保 token ID 在安全范围内
                            tokenId.coerceIn(0, vocabSize - 1)
                        }
                        
                        // 验证所有 token ID 都在安全范围内
                        val invalidTokens = charCodes.filter { it < 0 || it >= vocabSize }
                        if (invalidTokens.isNotEmpty()) {
                            throw IllegalArgumentException("Token ID 超出安全范围: $invalidTokens")
                        }
                        
                        charCodes.take(sequenceLength).forEach { 
                            intBuffer.put(it) 
                        }
                        // 填充剩余位置
                        while (intBuffer.position() < inputSize) {
                            intBuffer.put(0)
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }
                org.tensorflow.lite.DataType.INT8 -> {
                    try {
                        val vocabSize = 255  // INT8 范围是 -128 到 127，我们使用 0-255
                        processedText.map { char ->
                            val tokenId = mapCharToTokenId(char, vocabSize)
                            tokenId.coerceIn(0, 255).toByte()
                        }.take(sequenceLength).forEach { 
                            inputBuffer.put(it) 
                        }
                        // 填充剩余位置
                        while (inputBuffer.position() < inputSize) {
                            inputBuffer.put(0.toByte())
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }
                org.tensorflow.lite.DataType.UINT8 -> {
                    try {
                        val vocabSize = 255  // UINT8 范围是 0 到 255
                        processedText.map { char ->
                            val tokenId = mapCharToTokenId(char, vocabSize)
                            tokenId.coerceIn(0, 255).toUByte().toByte()
                        }.take(sequenceLength).forEach { 
                            inputBuffer.put(it) 
                        }
                        // 填充剩余位置
                        while (inputBuffer.position() < inputSize) {
                            inputBuffer.put(0.toByte())
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }
                else -> {
                    return@withContext null
                }
            }
            inputBuffer.rewind()
            
            // 准备输出缓冲区
            val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }
            
            val outputBuffer = when (outputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
                }
                else -> {
                    return@withContext null
                }
            }
            
            // 运行推理
            try {
                interp.run(inputBuffer, outputBuffer)
            } catch (e: Exception) {
                throw e
            }
            
            // 读取输出结果
            outputBuffer.rewind()
            
            val embedding = when (outputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    val floatArray = FloatArray(outputSize)
                    outputBuffer.asFloatBuffer().get(floatArray)
                    floatArray
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    val intArray = IntArray(outputSize)
                    outputBuffer.asIntBuffer().get(intArray)
                    intArray.map { it.toFloat() }.toFloatArray()
                }
                else -> {
                    return@withContext null
                }
            }
            
            embedding
        } catch (e: Exception) {
            null
        }
        }
    }
    
    /**
     * 释放资源。
     */
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            isInitialized = false
            Log.d(TAG, "Interpreter 已关闭")
        } catch (e: Exception) {
            Log.w(TAG, "关闭 Interpreter 时出错: ${e.message}")
        }
        
        // 关闭 FileInputStream（MappedByteBuffer 不再需要时）
        try {
            modelInputStream?.close()
            modelInputStream = null
            Log.d(TAG, "FileInputStream 已关闭")
        } catch (e: Exception) {
            Log.w(TAG, "关闭 FileInputStream 时出错: ${e.message}")
        }
        
        // 关闭 AssetFileDescriptor
        try {
            modelFileDescriptor?.close()
            modelFileDescriptor = null
            Log.d(TAG, "AssetFileDescriptor 已关闭")
        } catch (e: Exception) {
            Log.w(TAG, "关闭 AssetFileDescriptor 时出错: ${e.message}")
        }
        
        Log.d(TAG, "✅ 所有模型资源已释放")
    }
}

