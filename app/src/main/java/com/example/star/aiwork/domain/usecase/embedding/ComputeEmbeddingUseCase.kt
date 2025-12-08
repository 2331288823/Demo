package com.example.star.aiwork.domain.usecase.embedding

import com.example.star.aiwork.infra.embedding.EmbeddingService
import com.example.star.aiwork.infra.embedding.RemoteEmbeddingAPI

/**
 * 计算文本向量嵌入的用例。
 * 根据用户输入的文本，使用 EmbeddingService 或 RemoteEmbeddingAPI 生成对应的向量表示。
 * 
 * 注意：此方法在后台线程执行，不会阻塞 UI 线程。
 * EmbeddingService 内部使用 withContext(Dispatchers.Default) 确保在后台线程执行。
 *
 * @param embeddingService 本地向量嵌入服务，用于执行实际的向量计算（可选）
 * @param remoteEmbeddingAPI 远程向量嵌入 API 服务（可选）
 * @param useRemote 是否使用远程 API，默认为 false（使用本地服务）
 */
class ComputeEmbeddingUseCase(
    private val embeddingService: EmbeddingService? = null,
    private val remoteEmbeddingAPI: RemoteEmbeddingAPI? = null,
    private val useRemote: Boolean = false
) {
    /**
     * 执行向量计算。
     * 
     * 此方法在后台线程执行，不会阻塞 UI 线程。
     *
     * @param text 用户输入的文本
     * @return 计算得到的向量数组，如果计算失败则返回 null
     */
    suspend operator fun invoke(text: String): FloatArray? {
        return if (useRemote && remoteEmbeddingAPI != null) {
            remoteEmbeddingAPI.embed(text)
        } else if (embeddingService != null) {
            embeddingService.embed(text)
        } else {
            null
        }
    }
}

