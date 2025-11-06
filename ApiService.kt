package com.example.helloworld.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// IMPORTANT: Replace with the actual IP address of your FastAPI server
private const val BASE_URL = "http://10.67.137.254:8000/"

// 1. Data structure sent to the server (Matches FastAPI's ChatRequest)
data class ChatRequest(
    val message: String,
    val context: String
)

// 2. Data structure received from the server (Matches FastAPI's ChatResponse)
data class ChatResponse(
    @SerializedName("type") // Use SerializedName for clarity
    val type: String,
    @SerializedName("content")
    val content: String
)

// 3. The Retrofit Interface
interface ChatService {
    @POST("chat") // The endpoint defined in FastAPI
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

// 4. Singleton object to provide access to the API service
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
