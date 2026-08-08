package com.t3tools.t3code.compose.core.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

public class CanonicalContractFixtureTest {
  private val json = ContractJson.format

  @Test
  public fun `fixture manifest records canonical revision hashes and complete inventory`() {
    val manifest = json.parseToJsonElement(fixture("manifest.json")).jsonObject
    val provenance = manifest.requiredObject("provenance")
    val contracts = provenance.requiredObject("canonicalContracts")
    val effectRpc = provenance.requiredObject("effectRpc")
    val inventory = manifest.requiredObject("inventory")

    assertTrue(contracts.requiredString("revision").matches(Regex("[0-9a-f]{40}")))
    assertTrue(contracts.requiredString("contentHash").matches(Regex("[0-9a-f]{64}")))
    assertEquals("sha256-path-lf-v1", contracts.requiredString("algorithm"))
    assertTrue(contracts.requiredArray("files").size >= 8)
    assertTrue(effectRpc.requiredString("contentHash").matches(Regex("[0-9a-f]{64}")))
    assertTrue(effectRpc.requiredString("version").isNotBlank())
    assertEquals(6, inventory.requiredArray("http").size)
    assertEquals(2, inventory.requiredArray("rpc").size)
  }

  @Test
  public fun `descriptor fixtures cover optionals unknown fields and incompatible enums`() {
    val complete = json.decodeFromString<ExecutionEnvironmentDescriptor>(
      fixture("http/environment-descriptor.json"),
    )
    val omitted = json.decodeFromString<ExecutionEnvironmentDescriptor>(
      fixture("http/environment-descriptor-omitted-optionals.json"),
    )
    val unknown = json.decodeFromString<ExecutionEnvironmentDescriptor>(
      fixture("http/environment-descriptor-unknown-fields.json"),
    )

    assertEquals("environment-fixture", complete.environmentId)
    assertFalse(omitted.capabilities.repositoryIdentity)
    assertEquals(ExecutionEnvironmentPlatformOs.LINUX, unknown.platform.os)
    assertThrows(SerializationException::class.java) {
      json.decodeFromString<ExecutionEnvironmentDescriptor>(
        fixture("http/environment-descriptor-incompatible-enum.json"),
      )
    }
  }

  @Test
  public fun `http fixtures preserve typed results trace context and large integers`() {
    val token = json.decodeFromString<AuthAccessTokenResult>(
      fixture("http/access-token-result.json"),
    )
    val ticket = json.decodeFromString<AuthWebSocketTicketResult>(
      fixture("http/websocket-ticket-result.json"),
    )
    val shell = json.decodeFromString<OrchestrationShellSnapshot>(
      fixture("http/shell-snapshot.json"),
    )
    val dispatch = json.decodeFromString<DispatchResult>(fixture("http/dispatch-result.json"))
    val error = json.decodeFromString<EnvironmentHttpError>(fixture("http/error-with-trace.json"))

    assertEquals("Bearer", token.tokenType)
    assertEquals("<redacted>", ticket.ticket)
    assertEquals(9_007_199_254_740_991L, shell.snapshotSequence)
    assertEquals(9_007_199_254_740_991L, dispatch.sequence)
    assertEquals("trace-fixture", error.traceId)
    assertEquals("invalid_credential", error.reason)
  }

  @Test
  public fun `thread and model fixtures promote legacy aliases without a Double bridge`() {
    val canonical = json.decodeFromString<ModelSelection>(fixture("model/model-selection.json"))
    val legacy = json.decodeFromString<ModelSelection>(
      fixture("model/model-selection-legacy-alias.json"),
    )
    val snapshot = json.decodeFromString<OrchestrationThreadDetailSnapshot>(
      fixture("http/thread-snapshot.json"),
    )

    assertEquals("codex", canonical.instanceId)
    assertEquals(canonical, legacy)
    assertEquals(9_007_199_254_740_991L, snapshot.snapshotSequence)
    assertEquals("codex", snapshot.thread.modelSelection.instanceId)
    assertEquals("message-fixture", snapshot.thread.messages.single().id)
    assertThrows(SerializationException::class.java) {
      json.decodeFromString<ProviderInteractionMode>(
        fixture("model/interaction-mode-incompatible.json"),
      )
    }
  }

  @Test
  public fun `all committed fixtures are synthetic redacted and path safe`() {
    val manifest = json.parseToJsonElement(fixture("manifest.json")).jsonObject
    val fixtureEntries = manifest.requiredArray("fixtures")

    for (entry in fixtureEntries) {
      val fixturePath = entry.jsonObject.requiredString("path")
      val contents = fixture(fixturePath)
      assertFalse("absolute Windows path in $fixturePath", Regex("[A-Za-z]:\\\\").containsMatchIn(contents))
      assertFalse("absolute macOS path in $fixturePath", contents.contains("/Users/"))
      assertFalse("absolute Linux path in $fixturePath", contents.contains("/home/"))
      assertFalse("private key in $fixturePath", contents.contains("BEGIN PRIVATE KEY"))
      assertFalse("unredacted socket ticket in $fixturePath", Regex("wsTicket=[^<\\\"]").containsMatchIn(contents))
    }
  }

  private fun fixture(path: String): String {
    val resource = javaClass.classLoader?.getResourceAsStream("contracts/foundation/$path")
    assertNotNull("Missing fixture $path", resource)
    return requireNotNull(resource).bufferedReader(Charsets.UTF_8).use { it.readText() }
  }

  private fun JsonObject.requiredObject(name: String): JsonObject =
    requireNotNull(this[name]) { "Missing $name" }.jsonObject

  private fun JsonObject.requiredArray(name: String): JsonArray =
    requireNotNull(this[name]) { "Missing $name" }.jsonArray

  private fun JsonObject.requiredString(name: String): String =
    requireNotNull(this[name]) { "Missing $name" }.jsonPrimitive.content
}
