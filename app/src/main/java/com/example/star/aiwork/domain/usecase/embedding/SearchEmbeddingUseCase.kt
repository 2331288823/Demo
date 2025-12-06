package com.example.star.aiwork.domain.usecase.embedding

import com.example.star.aiwork.data.repository.EmbeddingRepository
import com.example.star.aiwork.domain.model.embedding.Embedding
import kotlin.math.sqrt

/**
 * 计算两个向量的余弦相似度。
 *
 * @param a 第一个向量
 * @param b 第二个向量
 * @return 余弦相似度值，范围在 [-1, 1] 之间
 */
fun cosineSim(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / (sqrt(normA) * sqrt(normB))
}

/**
 * 搜索向量嵌入的用例。
 * 根据查询向量，在所有向量中查找最相似的 k 个向量。
 *
 * @param repository 向量嵌入仓库
 */
class SearchEmbeddingUseCase(
    private val repository: EmbeddingRepository
) {
    /**
     * 执行向量搜索。
     *
     * @param queryVector 查询向量
     * @param k 返回前 k 个最相似的结果
     * @return 按相似度降序排列的向量和相似度分数对列表
     */
    suspend operator fun invoke(queryVector: FloatArray, k: Int): List<Pair<Embedding, Float>> {
        val all = repository.getAllEmbeddings()
        val scored = all.map { emb ->
            val score = cosineSim(queryVector, emb.embedding)
            emb to score
        }
        return scored.sortedByDescending { it.second }.take(k)
    }
}

