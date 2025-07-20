package com.example.predictsafe

data class CrimeZone(
    val lat: Double,
    val lng: Double,
    val level: String,  // "low", "medium", "high"
    val crowdLevel: String  // "low", "medium", "high"
)
