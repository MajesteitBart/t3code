package com.t3tools.t3code.compose.core.protocol

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

public class RedactedSecret private constructor(private val value: String) {
  public fun reveal(): String = value

  override fun toString(): String = "<redacted>"

  override fun equals(other: Any?): Boolean = other is RedactedSecret && value == other.value

  override fun hashCode(): Int = value.hashCode()

  public companion object {
    public fun from(value: String): RedactedSecret {
      val trimmed = value.trim()
      if (trimmed.isEmpty()) throw MalformedInputFailure("Pairing input is missing its token.")
      return RedactedSecret(trimmed)
    }
  }
}

public data class PairingInputFields(
  public val host: String,
  public val pairingCode: RedactedSecret?,
  public val label: String?,
)

public class PairingTarget internal constructor(
  public val bootstrapCredential: RedactedSecret,
  public val httpBaseUrl: String,
  public val webSocketBaseUrl: String,
) {
  override fun toString(): String =
    "PairingTarget(bootstrapCredential=<redacted>, httpBaseUrl=$httpBaseUrl, webSocketBaseUrl=$webSocketBaseUrl)"
}

public object PairingUrl {
  private val supportedSchemes = setOf("http", "https", "ws", "wss")
  private val explicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
  private val compactCode = Regex("^[A-Za-z0-9_-]{4,256}$")

  public fun resolve(rawValue: String): PairingTarget {
    val fields = parseFields(rawValue)
    val credential = fields.pairingCode
      ?: throw MalformedInputFailure("Pairing input is missing its token.")
    return target(normalizedBaseUri(fields.host), credential)
  }

  public fun resolve(host: String, pairingCode: String): PairingTarget {
    val fields = parseFields(host)
    val credential = fields.pairingCode ?: RedactedSecret.from(pairingCode)
    return target(normalizedBaseUri(fields.host), credential)
  }

  public fun parseFields(rawValue: String): PairingInputFields {
    val extracted = extractPairingUrl(rawValue)
    looseHostAndCode(extracted)?.let { (host, code) ->
      return PairingInputFields(
        host = displayHost(normalizedBaseUri(host)),
        pairingCode = RedactedSecret.from(code),
        label = null,
      )
    }

    if (!explicitScheme.containsMatchIn(extracted)) {
      return PairingInputFields(
        host = displayHost(normalizedBaseUri(extracted)),
        pairingCode = null,
        label = null,
      )
    }

    val uri = parseUri(extracted)
    requireSupportedScheme(uri.scheme)
    val query = parseQuery(uri.rawQuery)
    val fragment = parseQuery(uri.rawFragment)
    val token = (fragment + query)
      .firstOrNull { it.first.equals("token", ignoreCase = true) }
      ?.second
      ?.trim()
      ?.takeIf(String::isNotEmpty)
      ?.let(RedactedSecret::from)
    val label = query
      .firstOrNull { it.first.equals("label", ignoreCase = true) }
      ?.second
      ?.trim()
      ?.takeIf(String::isNotEmpty)
    val hosted = query
      .firstOrNull { it.first.equals("host", ignoreCase = true) }
      ?.second
      ?.trim()
      ?.takeIf(String::isNotEmpty)

    val base = if (hosted == null) normalizedBaseUri(extracted) else normalizedBaseUri(hosted)
    return PairingInputFields(
      host = displayHost(base),
      pairingCode = token,
      label = label,
    )
  }

  public fun pairingUrlFromWrapper(rawValue: String): String = extractPairingUrl(rawValue)

  private fun extractPairingUrl(rawValue: String): String {
    val trimmed = rawValue.trim()
    if (trimmed.isEmpty()) throw MalformedInputFailure("Enter a server address.")
    val uri = runCatching { URI(trimmed) }.getOrNull()
    if (!uri?.scheme.equals("t3code", ignoreCase = true)) return trimmed
    val wrapped = parseQuery(uri?.rawQuery)
      .firstOrNull { it.first.equals("pairingUrl", ignoreCase = true) }
      ?.second
      ?.trim()
      .orEmpty()
    if (wrapped.isEmpty()) throw MalformedInputFailure("Pairing wrapper is missing pairingUrl.")
    return wrapped
  }

  private fun looseHostAndCode(value: String): Pair<String, String>? {
    val fields = value.split(Regex("\\s+")).filter(String::isNotEmpty)
    if (fields.size < 2) return null
    val code = fields.last()
    if (!compactCode.matches(code)) return null
    val host = fields.dropLast(1).joinToString(" ")
    if (!(host.contains('.') || host.contains(':') || host.startsWith('/'))) return null
    return host to code
  }

  private fun normalizedBaseUri(rawValue: String): URI {
    val trimmed = rawValue.trim()
    if (trimmed.isEmpty()) throw MalformedInputFailure("Enter a server address.")
    val withoutLeadingSlashes = trimmed.replace(Regex("^/+"), "")
    val normalized = if (explicitScheme.containsMatchIn(withoutLeadingSlashes)) {
      withoutLeadingSlashes
    } else {
      "https://$withoutLeadingSlashes"
    }
    val uri = parseUri(normalized)
    requireSupportedScheme(uri.scheme)
    val host = uri.host ?: throw MalformedInputFailure("Pairing input is missing its environment host.")
    if (uri.userInfo != null) throw MalformedInputFailure("Pairing input must not include user information.")
    return runCatching { URI(uri.scheme.lowercase(), null, host, uri.port, "/", null, null) }
      .getOrElse { throw MalformedInputFailure("Pairing URL is invalid.") }
  }

  private fun target(baseUri: URI, credential: RedactedSecret): PairingTarget {
    val scheme = baseUri.scheme.lowercase()
    val httpScheme = when (scheme) {
      "ws" -> "http"
      "wss" -> "https"
      else -> scheme
    }
    val webSocketScheme = when (scheme) {
      "http" -> "ws"
      "https" -> "wss"
      else -> scheme
    }
    val http = URI(httpScheme, null, baseUri.host, baseUri.port, "/", null, null)
    val webSocket = URI(webSocketScheme, null, baseUri.host, baseUri.port, "/", null, null)
    return PairingTarget(credential, http.toString(), webSocket.toString())
  }

  private fun displayHost(uri: URI): String = uri.toString().removeSuffix("/")

  private fun parseUri(value: String): URI = runCatching { URI(value) }
    .getOrElse { throw MalformedInputFailure("Pairing URL is invalid.") }

  private fun requireSupportedScheme(scheme: String?) {
    if (scheme?.lowercase() !in supportedSchemes) {
      throw MalformedInputFailure("Pairing URL uses an unsupported scheme.")
    }
  }

  private fun parseQuery(rawQuery: String?): List<Pair<String, String>> {
    if (rawQuery.isNullOrEmpty()) return emptyList()
    return try {
      rawQuery.split('&').map { entry ->
        val separator = entry.indexOf('=')
        val rawName = if (separator < 0) entry else entry.substring(0, separator)
        val rawValue = if (separator < 0) "" else entry.substring(separator + 1)
        URLDecoder.decode(rawName, StandardCharsets.UTF_8.name()) to
          URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
      }
    } catch (_: IllegalArgumentException) {
      throw MalformedInputFailure("Pairing URL contains invalid percent encoding.")
    }
  }
}
