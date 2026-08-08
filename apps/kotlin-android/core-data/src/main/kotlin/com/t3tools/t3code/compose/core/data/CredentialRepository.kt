package com.t3tools.t3code.compose.core.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.t3tools.t3code.compose.core.protocol.ContractJson
import com.t3tools.t3code.compose.core.protocol.RedactedSecret
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

public enum class CredentialKind(public val storedValue: String) {
  DIRECT_BEARER("direct-bearer"),
}

public data class EnvironmentCredential(
  public val kind: CredentialKind,
  public val secret: RedactedSecret,
) {
  public companion object {
    public fun directBearer(secret: RedactedSecret): EnvironmentCredential =
      EnvironmentCredential(CredentialKind.DIRECT_BEARER, secret)
  }
}

public interface CredentialRepository {
  public suspend fun read(environmentId: String): EnvironmentCredential?

  public suspend fun write(environmentId: String, credential: EnvironmentCredential)

  public suspend fun delete(environmentId: String)
}

public enum class CredentialUnavailableReason {
  MISSING_KEY_MATERIAL,
  INVALIDATED_KEY_MATERIAL,
  CORRUPT_CIPHERTEXT,
  UNSUPPORTED_ENVELOPE,
}

public class CredentialUnavailableFailure(
  public val reason: CredentialUnavailableReason,
  cause: Throwable? = null,
) : IOException(
  when (reason) {
    CredentialUnavailableReason.MISSING_KEY_MATERIAL ->
      "The protected environment credential can no longer be decrypted. Pair again."
    CredentialUnavailableReason.INVALIDATED_KEY_MATERIAL ->
      "Android invalidated the environment credential key. Pair again."
    CredentialUnavailableReason.CORRUPT_CIPHERTEXT ->
      "The protected environment credential is corrupt. Pair again."
    CredentialUnavailableReason.UNSUPPORTED_ENVELOPE ->
      "The protected environment credential uses an unsupported kind. Pair again."
  },
  cause,
)

public class CredentialStorageFailure(
  operation: String,
  cause: Throwable? = null,
) : IOException("Protected credential $operation failed.", cause)

/**
 * Stores only AES-GCM ciphertext in app-private preferences. The non-exportable AES key remains in
 * Android Keystore, and the environment ID is authenticated as additional data to prevent swaps.
 */
public class AndroidKeystoreCredentialRepository(
  context: Context,
  keyAlias: String = DEFAULT_KEY_ALIAS,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CredentialRepository {
  private val store = SharedPreferencesCredentialStore(
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
  )
  private val protector = AndroidKeystoreCredentialProtector(keyAlias)
  private val ioDispatcher = dispatcher

  override suspend fun read(environmentId: String): EnvironmentCredential? =
    withContext(ioDispatcher) {
      val protectedValue = store.read(environmentId) ?: return@withContext null
      val plaintext = protector.decrypt(environmentId, protectedValue)
      try {
        CredentialEnvelopeCodec.decode(plaintext)
      } finally {
        plaintext.fill(0)
      }
    }

  override suspend fun write(environmentId: String, credential: EnvironmentCredential): Unit =
    withContext(ioDispatcher) {
      val plaintext = CredentialEnvelopeCodec.encode(credential)
      try {
        store.write(environmentId, protector.encrypt(environmentId, plaintext))
      } finally {
        plaintext.fill(0)
      }
    }

  override suspend fun delete(environmentId: String): Unit = withContext(ioDispatcher) {
    store.delete(environmentId)
  }

  public companion object {
    public const val DEFAULT_KEY_ALIAS: String = "t3-code-compose-direct-credentials-v1"
    private const val PREFERENCES_NAME: String = "protected-environment-credentials"
  }
}

@Serializable
private data class CredentialEnvelope(
  val version: Int,
  val kind: String,
  val secret: String,
)

internal object CredentialEnvelopeCodec {
  private const val CURRENT_VERSION = 1

  fun encode(credential: EnvironmentCredential): ByteArray = ContractJson.format.encodeToString(
    CredentialEnvelope(
      version = CURRENT_VERSION,
      kind = credential.kind.storedValue,
      secret = credential.secret.reveal(),
    ),
  ).encodeToByteArray()

  fun decode(encoded: ByteArray): EnvironmentCredential {
    val envelope = try {
      ContractJson.format.decodeFromString<CredentialEnvelope>(encoded.decodeToString())
    } catch (error: SerializationException) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE, error)
    } catch (error: IllegalArgumentException) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE, error)
    }
    if (envelope.version != CURRENT_VERSION) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE)
    }
    val kind = CredentialKind.entries.singleOrNull { it.storedValue == envelope.kind }
      ?: throw CredentialUnavailableFailure(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE)
    if (envelope.secret.isBlank()) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.UNSUPPORTED_ENVELOPE)
    }
    return EnvironmentCredential(kind, RedactedSecret.from(envelope.secret))
  }
}

private data class ProtectedCredential(
  val version: Int,
  val initializationVector: ByteArray,
  val ciphertext: ByteArray,
) {
  fun encode(): String = listOf(
    version.toString(),
    Base64.encodeToString(initializationVector, Base64.NO_WRAP),
    Base64.encodeToString(ciphertext, Base64.NO_WRAP),
  ).joinToString(".")

  companion object {
    private const val CURRENT_VERSION = 1

    fun create(initializationVector: ByteArray, ciphertext: ByteArray): ProtectedCredential =
      ProtectedCredential(CURRENT_VERSION, initializationVector.copyOf(), ciphertext.copyOf())

    fun decode(value: String): ProtectedCredential {
      val parts = value.split('.')
      if (parts.size != 3 || parts[0].toIntOrNull() != CURRENT_VERSION) {
        throw CredentialUnavailableFailure(CredentialUnavailableReason.CORRUPT_CIPHERTEXT)
      }
      return try {
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        if (iv.size != GCM_IV_BYTES || ciphertext.isEmpty()) {
          throw CredentialUnavailableFailure(CredentialUnavailableReason.CORRUPT_CIPHERTEXT)
        }
        ProtectedCredential(CURRENT_VERSION, iv, ciphertext)
      } catch (error: IllegalArgumentException) {
        throw CredentialUnavailableFailure(CredentialUnavailableReason.CORRUPT_CIPHERTEXT, error)
      }
    }

    private const val GCM_IV_BYTES = 12
  }
}

private class SharedPreferencesCredentialStore(
  private val preferences: SharedPreferences,
) {
  fun read(environmentId: String): ProtectedCredential? =
    preferences.getString(storageKey(environmentId), null)?.let(ProtectedCredential::decode)

  fun write(environmentId: String, credential: ProtectedCredential) {
    if (!preferences.edit().putString(storageKey(environmentId), credential.encode()).commit()) {
      throw CredentialStorageFailure("write")
    }
  }

  fun delete(environmentId: String) {
    if (!preferences.edit().remove(storageKey(environmentId)).commit()) {
      throw CredentialStorageFailure("deletion")
    }
  }

  private fun storageKey(environmentId: String): String =
    Base64.encodeToString(environmentId.encodeToByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
}

private class AndroidKeystoreCredentialProtector(
  private val keyAlias: String,
) {
  fun encrypt(environmentId: String, plaintext: ByteArray): ProtectedCredential {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    try {
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
      cipher.updateAAD(environmentId.encodeToByteArray())
      return ProtectedCredential.create(cipher.iv, cipher.doFinal(plaintext))
    } catch (error: CredentialUnavailableFailure) {
      throw error
    } catch (error: Exception) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.INVALIDATED_KEY_MATERIAL, error)
    }
  }

  fun decrypt(environmentId: String, protectedCredential: ProtectedCredential): ByteArray {
    return try {
      val key = existingKey()
        ?: throw CredentialUnavailableFailure(CredentialUnavailableReason.MISSING_KEY_MATERIAL)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(
        Cipher.DECRYPT_MODE,
        key,
        GCMParameterSpec(GCM_TAG_BITS, protectedCredential.initializationVector),
      )
      cipher.updateAAD(environmentId.encodeToByteArray())
      cipher.doFinal(protectedCredential.ciphertext)
    } catch (error: CredentialUnavailableFailure) {
      throw error
    } catch (error: KeyPermanentlyInvalidatedException) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.INVALIDATED_KEY_MATERIAL, error)
    } catch (error: AEADBadTagException) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.CORRUPT_CIPHERTEXT, error)
    } catch (error: Exception) {
      throw CredentialUnavailableFailure(CredentialUnavailableReason.INVALIDATED_KEY_MATERIAL, error)
    }
  }

  private fun getOrCreateKey(): SecretKey = existingKey() ?: run {
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        keyAlias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build(),
    )
    generator.generateKey()
  }

  private fun existingKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    return keyStore.getKey(keyAlias, null) as? SecretKey
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
  }
}
