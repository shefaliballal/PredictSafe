package com.example.predictsafe

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

// Data class for request
data class CountryData(val country: String)

// Data class for response
data class NewsRiskResponse(val risk_score: Double, val threat_count: Int, val total: Int)

interface NewsRiskService {
    @POST("/analyze_news")
    fun analyzeNews(@Body data: CountryData): Call<NewsRiskResponse>
}
