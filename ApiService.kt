package com.example.helloworld.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// IMPORTANT: Make sure this is still your server IP
private const val BASE_URL = "http://10.125.149.254:8000/"

// --- NEW DATA MODELS TO MATCH PYTHON BACKEND ---

// 1. A single message in the chat history
data class HistoryMessage(
    @SerializedName("role")
    val role: String, // "user" or "model"
    @SerializedName("text")
    val text: String
)

// 2. The new ChatRequest your app will SEND
data class ChatRequest(
    @SerializedName("permanent_context")
    val permanentContext: String,
    @SerializedName("chat_history")
    val chatHistory: List<HistoryMessage>
)

// 3. The new ChatResponse your app will RECEIVE
// All fields are optional (nullable) except "type"
data class ChatResponse(
    @SerializedName("type")
    val type: String,
    @SerializedName("content")
    val content: String?,
    @SerializedName("time")
    val time: String?,
    @SerializedName("label")
    val label: String?
)

// 4. The Retrofit Interface (remains the same)
interface ChatService {
    @POST("chat") // The endpoint defined in FastAPI
    suspend fun chat(@Body request: ChatRequest): ChatResponse // Updated request/response
}

// 5. Singleton object (remains the same)
object ChatApi {
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Lazily initialized service instance
    val service: ChatService by lazy {
        retrofit.create(ChatService::class.java)
    }
}
