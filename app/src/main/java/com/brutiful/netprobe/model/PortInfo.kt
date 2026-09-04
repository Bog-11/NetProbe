package com.brutiful.netprobe.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "port_info")
data class PortInfo(
    @PrimaryKey val port: Int,
    val name: String,
    val description: String
)
