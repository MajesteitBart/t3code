package com.t3tools.t3code.compose.core.protocol

import kotlinx.serialization.json.Json

public object ContractJson {
  public val format: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
    coerceInputValues = false
  }
}
