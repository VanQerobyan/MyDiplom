package com.yerevan.transport.data.local.entity

import com.yerevan.transport.domain.model.TransportStop

fun StopEntity.toDomain(): TransportStop = TransportStop(
    id = id,
    name = name,
    nameRu = nameRu,
    latitude = latitude,
    longitude = longitude,
    stopCode = stopCode,
    routes = routes
)
