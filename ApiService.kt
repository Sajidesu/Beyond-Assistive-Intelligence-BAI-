package com.example.helloworld.network

interface ApiService {
    // ApiService.kt
     // This will be your package name

    // Your team might use an object for configuration
    object ApiConfig {
        // THIS IS WHERE YOU MAKE THE CHANGE:
        private const val BASE_URL = "http://192.0.0.2:8000/chat"
    }

    // Interface where you define your API methods (GET, POST, etc.)
    interface ApiService {
        // Example: fun getHelloWorld(): Call<String>
    }

}