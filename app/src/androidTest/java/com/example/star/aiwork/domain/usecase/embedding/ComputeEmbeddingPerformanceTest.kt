package com.example.star.aiwork.domain.usecase.embedding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.star.aiwork.infra.embedding.EmbeddingService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * 测试嵌入向量计算的性能。
 * 测量不同长度文本的计算时间。
 */
@RunWith(AndroidJUnit4::class)
class ComputeEmbeddingPerformanceTest {

    private lateinit var context: Context
    private lateinit var embeddingService: EmbeddingService
    private lateinit var computeEmbeddingUseCase: ComputeEmbeddingUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        embeddingService = EmbeddingService(context)
        computeEmbeddingUseCase = ComputeEmbeddingUseCase(
            embeddingService = embeddingService,
            useRemote = false
        )
    }

    @After
    fun tearDown() {
        embeddingService.close()
    }

    @Test
    fun testEmbeddingPerformance_shortText() = runBlocking {
        val text = "这是一个短文本测试"
        println("\n=== 测试短文本 ===")
        println("文本长度: ${text.length} 字符")
        println("文本内容: $text")

        val time = measureTimeMillis {
            val embedding = computeEmbeddingUseCase(text)
            println("嵌入向量维度: ${embedding?.size ?: 0}")
            assert(embedding != null) { "嵌入向量计算失败" }
        }

        println("计算时间: ${time}ms")
        println("平均每字符: ${String.format("%.2f", time.toDouble() / text.length)}ms")
    }

    @Test
    fun testEmbeddingPerformance_mediumText() = runBlocking {
        val text = "这是一个中等长度的文本测试。".repeat(10)
        println("\n=== 测试中等长度文本 ===")
        println("文本长度: ${text.length} 字符")
        println("文本预览: ${text.take(50)}...")

        val time = measureTimeMillis {
            val embedding = computeEmbeddingUseCase(text)
            println("嵌入向量维度: ${embedding?.size ?: 0}")
            assert(embedding != null) { "嵌入向量计算失败" }
        }

        println("计算时间: ${time}ms")
        println("平均每字符: ${String.format("%.2f", time.toDouble() / text.length)}ms")
    }

    @Test
    fun testEmbeddingPerformance_longText() = runBlocking {
        val text = "这是一个较长的文本测试，用于测试模型处理长文本的能力。".repeat(20)
        println("\n=== 测试长文本 ===")
        println("文本长度: ${text.length} 字符")
        println("文本预览: ${text.take(50)}...")

        val time = measureTimeMillis {
            val embedding = computeEmbeddingUseCase(text)
            println("嵌入向量维度: ${embedding?.size ?: 0}")
            assert(embedding != null) { "嵌入向量计算失败" }
        }

        println("计算时间: ${time}ms")
        println("平均每字符: ${String.format("%.2f", time.toDouble() / text.length)}ms")
    }

    @Test
    fun testEmbeddingPerformance_multipleRuns() = runBlocking {
        val text = "这是用于多次测试的文本"
        val runs = 5
        println("\n=== 多次运行测试 ===")
        println("文本长度: ${text.length} 字符")
        println("运行次数: $runs")

        val times = mutableListOf<Long>()
        repeat(runs) { runIndex ->
            val time = measureTimeMillis {
                val embedding = computeEmbeddingUseCase(text)
                assert(embedding != null) { "嵌入向量计算失败" }
            }
            times.add(time)
            println("第 ${runIndex + 1} 次: ${time}ms")
        }

        val avgTime = times.average()
        val minTime = times.minOrNull() ?: 0L
        val maxTime = times.maxOrNull() ?: 0L

        println("\n统计结果:")
        println("平均时间: ${String.format("%.2f", avgTime)}ms")
        println("最短时间: ${minTime}ms")
        println("最长时间: ${maxTime}ms")
        println("时间范围: ${maxTime - minTime}ms")
    }

    @Test
    fun testEmbeddingPerformance_warmupAndMeasure() = runBlocking {
        val text = "这是用于预热和测量的测试文本"
        println("\n=== 预热测试 ===")
        println("文本长度: ${text.length} 字符")

        // 预热：第一次运行通常较慢（模型加载等）
        println("预热运行...")
        val warmupTime = measureTimeMillis {
            val embedding = computeEmbeddingUseCase(text)
            assert(embedding != null) { "嵌入向量计算失败" }
        }
        println("预热时间: ${warmupTime}ms")

        // 实际测量：多次运行取平均
        val measurementRuns = 3
        val times = mutableListOf<Long>()
        repeat(measurementRuns) { runIndex ->
            val time = measureTimeMillis {
                val embedding = computeEmbeddingUseCase(text)
                assert(embedding != null) { "嵌入向量计算失败" }
            }
            times.add(time)
            println("测量 ${runIndex + 1}: ${time}ms")
        }

        val avgTime = times.average()
        println("\n预热后平均时间: ${String.format("%.2f", avgTime)}ms")
        println("预热开销: ${warmupTime - avgTime}ms")
    }
}

