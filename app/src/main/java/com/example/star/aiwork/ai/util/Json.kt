package com.example.star.aiwork.ai.util

import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    isLenient = true
}
