package com.t3tools.t3code.compose.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

public class PairingUrlTest {
  @Test
  public fun `canonical URL uses fragment token before query and derives both bases`() {
    val target = PairingUrl.resolve(
      " https://studio.example:3773/pair?token=query-token#token=fragment-token ",
    )

    assertEquals("fragment-token", target.bootstrapCredential.reveal())
    assertEquals("https://studio.example:3773/", target.httpBaseUrl)
    assertEquals("wss://studio.example:3773/", target.webSocketBaseUrl)
  }

  @Test
  public fun `hosted loose bare and wrapper formats parse deterministically`() {
    val hosted = PairingUrl.parseFields(
      "https://app.t3.codes/pair?host=http%3A%2F%2F192.168.1.7%3A18773" +
        "&label=Big%20O#token=PAIRING",
    )
    val loose = PairingUrl.parseFields("192.168.1.7:18773 N735KQXJ5SJW")
    val bare = PairingUrl.resolve("studio.example:18773", "FORM-CODE")
    val wrapped = PairingUrl.resolve(
      "t3code://pair?pairingUrl=https%3A%2F%2Fstudio.example%2Fpair%23token%3DQR-CODE",
    )

    assertEquals("http://192.168.1.7:18773", hosted.host)
    assertEquals("PAIRING", hosted.pairingCode?.reveal())
    assertEquals("Big O", hosted.label)
    assertEquals("https://192.168.1.7:18773", loose.host)
    assertEquals("N735KQXJ5SJW", loose.pairingCode?.reveal())
    assertEquals("https://studio.example:18773/", bare.httpBaseUrl)
    assertEquals("wss://studio.example:18773/", bare.webSocketBaseUrl)
    assertEquals("QR-CODE", wrapped.bootstrapCredential.reveal())
  }

  @Test
  public fun `ws schemes convert to matching HTTP and WebSocket schemes`() {
    val cleartext = PairingUrl.resolve("ws://studio.example/pair#token=PAIRING")
    val tls = PairingUrl.resolve("wss://studio.example/pair#token=PAIRING")

    assertEquals("http://studio.example/", cleartext.httpBaseUrl)
    assertEquals("ws://studio.example/", cleartext.webSocketBaseUrl)
    assertEquals("https://studio.example/", tls.httpBaseUrl)
    assertEquals("wss://studio.example/", tls.webSocketBaseUrl)
  }

  @Test
  public fun `bare parse remains valid before a code is entered`() {
    val fields = PairingUrl.parseFields("studio.example")

    assertEquals("https://studio.example", fields.host)
    assertNull(fields.pairingCode)
  }

  @Test
  public fun `invalid inputs fail without echoing pairing material`() {
    assertThrows(MalformedInputFailure::class.java) { PairingUrl.resolve("") }
    assertThrows(MalformedInputFailure::class.java) {
      PairingUrl.resolve("ftp://studio.example/#token=PAIRING")
    }
    assertThrows(MalformedInputFailure::class.java) { PairingUrl.resolve("https://studio.example") }
    assertThrows(MalformedInputFailure::class.java) {
      PairingUrl.resolve("t3code://pair")
    }

    val target = PairingUrl.resolve("https://studio.example/#token=PAIR-ONCE")
    assertFalse(target.toString().contains("PAIR-ONCE"))
    assertTrue(target.toString().contains("<redacted>"))
  }
}
