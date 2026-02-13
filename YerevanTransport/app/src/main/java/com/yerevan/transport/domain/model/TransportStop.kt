package com.yerevan.transport.domain.model

data class TransportStop(
    val id: String,
    val name: String,
    val nameRu: String?,
    val latitude: Double,
    val longitude: Double,
    val stopCode: String?,
    val routes: String
) {
    override fun toString(): String = name
}
