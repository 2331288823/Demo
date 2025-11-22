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

package com.example.star.aiwork.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * JetChat 小部件的接收器。
 *
 * 这是一个广播接收器，负责接收与应用小部件相关的系统广播（如更新、启用、禁用等）。
 * 对于 Glance 小部件，我们需要继承 [GlanceAppWidgetReceiver] 并提供 [GlanceAppWidget] 的实例。
 */
class WidgetReceiver : GlanceAppWidgetReceiver() {

    // 返回此接收器管理的小部件实例
    override val glanceAppWidget: GlanceAppWidget = JetChatWidget()
}
