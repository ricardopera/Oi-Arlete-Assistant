package com.example.oiarlete.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface McpService {
    @POST("/mcp")
    fun execute(@Body request: McpRequest): Call<McpResponse>
}

class McpClient(
    baseUrl: String,
    okHttpClient: OkHttpClient = OkHttpClient(),
    objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
) {
    private val service: McpService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
        service = retrofit.create(McpService::class.java)
    }

    fun execute(request: McpRequest): Call<McpResponse> = service.execute(request)
}
