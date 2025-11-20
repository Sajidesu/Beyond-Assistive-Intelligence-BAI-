package com.example.helloworld.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


private const val BASE_URL = "http://192.168.254.160:8000/" 

data class HistoryMessage(
    @SerializedName("role") val role: String,
    @SerializedName("text") val text: String
)

data class ChatRequest(
    @SerializedName("permanent_context") val permanentContext: String,
    @SerializedName("chat_history") val chatHistory: List<HistoryMessage>
)

data class ToolResult(
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String?,
    @SerializedName("time") val time: String?,
    @SerializedName("label") val label: String?,
    @SerializedName("task_title") val taskTitle: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("name") val name: String?
)

data class ChatResponse(
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: List<ToolResult>?,

    
    @SerializedName("error_type") val errorType: String?
)

interface ChatService {
    @POST("chat") suspend fun chat(@Body request: ChatRequest): ChatResponse
}

object ChatApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: ChatService = retrofit.create(ChatService::class.java)
}
