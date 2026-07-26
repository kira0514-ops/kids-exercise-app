package com.kira.stockscope.data.network.dto

import kotlinx.serialization.Serializable

/** Yahoo Finance represents most numeric fields as {"raw": 1.23, "fmt": "1.23"}. */
@Serializable
data class RawFmt(
    val raw: Double? = null,
    val fmt: String? = null
)
