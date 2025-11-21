/*
 * Copyright 2020 The Android Open Source Project
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

package com.example.star.aiwork.conversation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.input.TextFieldValue
import com.example.star.aiwork.R

class ConversationUiState(
    val channelName: String, 
    val channelMembers: Int, 
    initialMessages: List<Message>
) {
    private val _messages: MutableList<Message> = initialMessages.toMutableStateList()
    val messages: List<Message> = _messages

    var temperature: Float by mutableFloatStateOf(0.7f)
    var maxTokens: Int by mutableIntStateOf(2000)
    var streamResponse: Boolean by mutableStateOf(true)

    var isRecording: Boolean by mutableStateOf(false)
    var textFieldValue: TextFieldValue by mutableStateOf(TextFieldValue())

    fun addMessage(msg: Message) {
        _messages.add(0, msg) // Add to the beginning of the list
    }

    fun appendToLastMessage(content: String) {
        if (_messages.isNotEmpty()) {
            val lastMsg = _messages[0]
            _messages[0] = lastMsg.copy(content = lastMsg.content + content)
        }
    }
}

@Immutable
data class Message(
    val author: String,
    val content: String,
    val timestamp: String,
    val image: Int? = null,
    val authorImage: Int = if (author == "me") R.drawable.ali else R.drawable.someone_else,
)
