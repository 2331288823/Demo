package com.example.star.aiwork.data

import android.content.Context
import com.example.star.aiwork.domain.model.Agent
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.PresetMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class AgentRepository(private val context: Context) {

    private val agentsFile: File by lazy {
        val dataDir = context.getExternalFilesDir("data") ?: File(context.filesDir, "data")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        File(dataDir, "agents.json")
    }

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: Flow<List<Agent>> = _agents.asStateFlow()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadAgents() {
        withContext(Dispatchers.IO) {
            if (agentsFile.exists()) {
                try {
                    val jsonString = agentsFile.readText()
                    val loadedAgents = json.decodeFromString(ListSerializer(Agent.serializer()), jsonString)
                    _agents.value = loadedAgents
                } catch (e: Exception) {
                    e.printStackTrace()
                    // If loading fails, potentially reset or keep empty
                    _agents.value = getDefaultAgents()
                    saveAgents(_agents.value)
                }
            } else {
                // Initialize with default agents if file doesn't exist
                _agents.value = getDefaultAgents()
                saveAgents(_agents.value)
            }
        }
    }

    suspend fun addAgent(agent: Agent) {
        val currentList = _agents.value.toMutableList()
        currentList.add(agent)
        _agents.value = currentList
        saveAgents(currentList)
    }
    
    suspend fun updateAgent(updatedAgent: Agent) {
         val currentList = _agents.value.toMutableList()
         val index = currentList.indexOfFirst { it.id == updatedAgent.id }
         if (index != -1) {
             currentList[index] = updatedAgent
             _agents.value = currentList
             saveAgents(currentList)
         }
    }

    suspend fun removeAgent(agentId: String) {
        val currentList = _agents.value.toMutableList()
        currentList.removeAll { it.id == agentId }
        _agents.value = currentList
        saveAgents(currentList)
    }

    private suspend fun saveAgents(agents: List<Agent>) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(ListSerializer(Agent.serializer()), agents)
                agentsFile.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDefaultAgents(): List<Agent> {
        return listOf(
            Agent(
                id = UUID.randomUUID().toString(),
                name = "General Assistant",
                description = "A helpful general purpose assistant.",
                systemPrompt = "You are a helpful assistant.",
                isDefault = true
            ),
            Agent(
                id = UUID.randomUUID().toString(),
                name = "Translator",
                description = "Translate text between languages.",
                systemPrompt = "You are a professional translator. Please translate the user input.",
                presetMessages = listOf(
                    PresetMessage(MessageRole.USER, "Hello"),
                    PresetMessage(MessageRole.ASSISTANT, "你好")
                ),
                isDefault = true
            ),
             Agent(
                id = UUID.randomUUID().toString(),
                name = "Code Expert",
                description = "Helps with programming tasks.",
                systemPrompt = "You are an expert software engineer. Help the user with code.",
                messageTemplate = "Fix this code:\n{{ message }}",
                isDefault = true
            )
        )
    }
}
