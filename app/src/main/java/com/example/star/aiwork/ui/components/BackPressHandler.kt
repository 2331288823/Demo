package com.example.star.aiwork.ui.components

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * 拦截并处理系统返回键事件的 Composable。
 *
 * 这是一个便捷的封装，允许在 Compose 层次结构中轻松处理返回操作。
 * 它利用了 AndroidX Activity 库的 [OnBackPressedDispatcher]。
 *
 * @param onBackPressed 当按下返回键时触发的回调。
 * @param enabled 是否启用拦截。默认为 true。
 */
@Composable
fun BackPressHandler(
    enabled: Boolean = true,
    onBackPressed: () -> Unit
) {
    // 使用 rememberUpdatedState 确保回调始终是最新的，避免因 lambda 变化导致不必要的重组
    val currentOnBackPressed by rememberUpdatedState(onBackPressed)

    // 创建并记住 OnBackPressedCallback 实例
    val backCallback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBackPressed()
            }
        }
    }

    // 响应 enabled 参数的变化
    DisposableEffect(enabled) {
        backCallback.isEnabled = enabled
        onDispose { }
    }

    // 获取当前的 OnBackPressedDispatcherOwner
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // 将回调添加到调度程序
    DisposableEffect(backDispatcher) {
        backDispatcher?.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }
}
