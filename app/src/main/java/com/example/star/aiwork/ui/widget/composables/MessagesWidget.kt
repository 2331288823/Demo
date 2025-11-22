/*
 * Copyright 2024 The Android Open Source Project
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

package com.example.star.aiwork.ui.widget.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.star.aiwork.R
import com.example.star.aiwork.ui.conversation.Message
import com.example.star.aiwork.ui.theme.GlanceJetchatTheme

/**
 * 消息小部件组件。
 *
 * 显示一个带有标题栏和消息列表的 Glance 小部件。
 *
 * @param messages 要显示的消息列表。
 */
@Composable
fun MessagesWidget(messages: List<Message>) {
    // 使用 Glance 的 Scaffold 提供基本布局结构
    Scaffold(
        titleBar = {
            // 标题栏，包含应用图标和标题
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_jetchat),
                title = "Jetchat"
            )
        },
        backgroundColor = GlanceJetchatTheme.colors.surface,
        modifier = GlanceModifier.padding(8.dp)
    ) {
        // 消息列表区域
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(messages) { message ->
                MessageItem(message)
            }
        }
    }
}

/**
 * 单个消息列表项组件。
 *
 * @param message 消息数据。
 */
@Composable
fun MessageItem(message: Message) {
    // 消息容器
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(GlanceJetchatTheme.colors.surfaceVariant) // 设置背景色
    ) {
        // 顶部：作者和时间
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            // 作者头像
            Image(
                provider = ImageProvider(message.authorImage),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            // 作者名称
            Text(
                text = message.author,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GlanceJetchatTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            // 时间戳
            Text(
                text = message.timestamp,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceJetchatTheme.colors.onSurfaceVariant
                )
            )
        }
        
        // 消息内容
        Text(
            text = message.content,
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceJetchatTheme.colors.onSurface
            ),
            modifier = GlanceModifier.padding(top = 4.dp)
        )
    }
}
