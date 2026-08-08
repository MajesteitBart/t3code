package com.t3tools.t3code.compose.core.protocol

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

public sealed interface ProtocolFailure {
  public val safeMessage: String
}

public class MalformedInputFailure internal constructor(
  override val safeMessage: String,
) : IllegalArgumentException(safeMessage), ProtocolFailure

public class LocalNetworkPermissionDeniedFailure internal constructor(
  public val host: String,
  cause: Throwable? = null,
) : IOException("Local network access was denied for $host.", cause), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class UnreachableFailure internal constructor(
  public val host: String,
  cause: Throwable? = null,
) : IOException("The environment at $host is unreachable.", cause), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class TimeoutFailure internal constructor(
  public val host: String,
  cause: Throwable? = null,
) : IOException("The environment at $host did not respond in time.", cause), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class CancelledFailure internal constructor(
  cause: Throwable? = null,
) : CancellationException("The transport attempt was cancelled."), ProtocolFailure {
  init {
    cause?.let(::initCause)
  }

  override val safeMessage: String = message.orEmpty()
}

public class AuthorizationRejectedFailure internal constructor(
  public val status: Int,
  public val code: String?,
  public val reason: String?,
  public val remoteMessage: String?,
  public val traceId: String?,
) : IOException(remoteMessage ?: reason ?: "The environment credential was rejected."), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class ServerRejectedFailure internal constructor(
  public val status: Int,
  public val code: String?,
  public val reason: String?,
  public val remoteMessage: String?,
  public val traceId: String?,
) : IOException(remoteMessage ?: reason ?: "The environment rejected the request."), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class TransportFailure internal constructor(
  public val host: String,
  cause: Throwable? = null,
) : IOException("The transport attempt for $host failed.", cause), ProtocolFailure {
  override val safeMessage: String = message.orEmpty()
}

public class ProtocolViolationFailure internal constructor(
  override val safeMessage: String,
  cause: Throwable? = null,
) : IOException(safeMessage, cause), ProtocolFailure

internal object ProtocolFailureMapper {
  fun fromIOException(host: String, error: IOException, cancelled: Boolean): Throwable {
    if (cancelled) return CancelledFailure(error)
    if (error is SocketTimeoutException) return TimeoutFailure(host, error)
    if (isLocalHost(host) && error.causeChain().any(::looksLikePermissionDenial)) {
      return LocalNetworkPermissionDeniedFailure(host, error)
    }
    if (
      error is UnknownHostException ||
      error is ConnectException ||
      error is NoRouteToHostException
    ) {
      return UnreachableFailure(host, error)
    }
    return TransportFailure(host, error)
  }

  fun isLocalHost(host: String): Boolean {
    val normalized = host.lowercase().removePrefix("[").removeSuffix("]")
    if (normalized == "localhost" || normalized.endsWith(".local")) return true
    if (normalized == "::1" || normalized.startsWith("fe80:")) return true
    if (normalized.startsWith("fc") || normalized.startsWith("fd")) return true

    val octets = normalized.split('.').mapNotNull(String::toIntOrNull)
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    return octets[0] == 10 ||
      octets[0] == 127 ||
      octets[0] == 169 && octets[1] == 254 ||
      octets[0] == 192 && octets[1] == 168 ||
      octets[0] == 172 && octets[1] in 16..31
  }

  private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

  private fun looksLikePermissionDenial(error: Throwable): Boolean {
    val detail = "${error::class.simpleName.orEmpty()} ${error.message.orEmpty()}".lowercase()
    return error is SocketException && (detail.contains("permission denied") || detail.contains("eacces")) ||
      detail.contains("eperm") ||
      detail.contains("errnoexception") && detail.contains("13")
  }
}

internal fun redactProtocolDetail(value: String?, secrets: Iterable<String>): String? {
  var redacted: String = value ?: return null
  for (secret in secrets) {
    if (secret.isNotEmpty()) redacted = redacted.replace(secret, "<redacted>")
  }
  return redacted
    .replace(Regex("(?i)(wsTicket|pairingUrl|subject_token)=([^&\\s]+)"), "$1=<redacted>")
    .take(512)
}
