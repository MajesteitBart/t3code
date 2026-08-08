package com.t3tools.t3code.compose.core.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

public class EffectRpcCodecTest {
  private val json = ContractJson.format

  @Test
  public fun `foundation requests match canonical envelopes and allocate monotonic integer ids`() {
    val ids = RpcRequestIdSequence()
    val canonicalDispatch = fixtureObject("rpc/request-dispatch.json")
    val canonicalSubscribe = fixtureObject("rpc/request-subscribe-shell.json")

    val dispatch = json.parseToJsonElement(
      EffectRpcCodec.encodeDispatchRequest(
        ids.next(),
        canonicalDispatch.requiredObject("payload"),
      ),
    )
    val subscribe = json.parseToJsonElement(
      EffectRpcCodec.encodeSubscribeShellRequest(
        ids.next(),
        OrchestrationSubscribeShellInput(
          afterSequence = 9_007_199_254_740_991L,
          requestCompletionMarker = true,
        ),
      ),
    )

    assertEquals(canonicalDispatch, dispatch)
    assertEquals(canonicalSubscribe, subscribe)
    assertEquals(JsonArray(emptyList()), dispatch.jsonObject["headers"])
    assertEquals(3L, ids.next())
  }

  @Test
  public fun `request ids fail closed after the JSON safe integer range is exhausted`() {
    val ids = RpcRequestIdSequence(9_007_199_254_740_991L)

    assertEquals(9_007_199_254_740_991L, ids.next())
    assertThrows(IllegalStateException::class.java) { ids.next() }
  }

  @Test
  public fun `control frames encode canonical JSON and decode to typed values`() {
    val controls = listOf(
      "rpc/ping.json" to RpcControlFrame.Ping,
      "rpc/pong.json" to RpcControlFrame.Pong,
      "rpc/ack.json" to RpcControlFrame.Ack(2L),
      "rpc/interrupt.json" to RpcControlFrame.Interrupt(2L),
    )

    for ((path, control) in controls) {
      val canonical = fixtureObject(path)
      assertEquals(canonical, json.parseToJsonElement(EffectRpcCodec.encodeControl(control)))
      when (val decoded = EffectRpcCodec.decodeFrame(fixture(path))) {
        RpcInboundFrame.Ping -> assertTrue(control === RpcControlFrame.Ping)
        RpcInboundFrame.Pong -> assertTrue(control === RpcControlFrame.Pong)
        is RpcInboundFrame.Ack -> assertEquals((control as RpcControlFrame.Ack).requestId, decoded.requestId)
        is RpcInboundFrame.Interrupt ->
          assertEquals((control as RpcControlFrame.Interrupt).requestId, decoded.requestId)
        else -> throw AssertionError("Unexpected control frame $decoded")
      }
    }
  }

  @Test
  public fun `all canonical shell variants decode with exact 64-bit sequences`() {
    val frame = EffectRpcCodec.decodeFrame(fixture("rpc/chunk-shell-variants.json"))
    assertTrue(frame is RpcInboundFrame.Chunk)
    val decoded = EffectRpcCodec.decodeShellChunk(frame as RpcInboundFrame.Chunk)

    assertEquals(6, decoded.size)
    assertTrue(decoded[0].item() is OrchestrationShellStreamItem.Synchronized)
    val snapshot = decoded[1].item() as OrchestrationShellStreamItem.Snapshot
    assertEquals(9_007_199_254_740_991L, snapshot.snapshot.snapshotSequence)
    assertEquals(
      9_007_199_254_740_991L,
      (decoded[2].item() as OrchestrationShellStreamItem.ProjectUpserted).sequence,
    )
    assertEquals(
      9_007_199_254_740_990L,
      (decoded[3].item() as OrchestrationShellStreamItem.ProjectRemoved).sequence,
    )
    assertEquals(
      9_007_199_254_740_989L,
      (decoded[4].item() as OrchestrationShellStreamItem.ThreadUpserted).sequence,
    )
    assertEquals(
      9_007_199_254_740_988L,
      (decoded[5].item() as OrchestrationShellStreamItem.ThreadRemoved).sequence,
    )
  }

  @Test
  public fun `unknown shell variants require refresh instead of partial mutation`() {
    val frame = EffectRpcCodec.decodeFrame(fixture("rpc/chunk-unknown-shell-item.json"))
      as RpcInboundFrame.Chunk
    val result = EffectRpcCodec.decodeShellChunk(frame).single()

    assertTrue(result is ShellStreamDecodeResult.RefreshRequired)
    assertEquals("future-shell-item", (result as ShellStreamDecodeResult.RefreshRequired).unknownKind)
  }

  @Test
  public fun `exit and fatal responses retain typed remote and protocol errors`() {
    val success = EffectRpcCodec.decodeFrame(fixture("rpc/exit-success.json"))
      as RpcInboundFrame.Exit
    val successValue = (success.outcome as RpcExitOutcome.Success).value.jsonObject
    assertEquals(9_007_199_254_740_991L, successValue["sequence"]?.jsonPrimitive?.long)

    val failure = EffectRpcCodec.decodeFrame(fixture("rpc/exit-remote-failure.json"))
      as RpcInboundFrame.Exit
    val remote = (failure.outcome as RpcExitOutcome.Failure).error
    assertEquals(2L, remote.requestId)
    assertEquals("OrchestrationGetSnapshotError", remote.remoteTag)
    assertEquals("Synthetic fixture rejection", remote.safeMessage)

    val defect = EffectRpcCodec.decodeFrame(fixture("rpc/defect.json"))
      as RpcInboundFrame.Defect
    assertEquals("Defect", defect.error.frameTag)
    assertEquals("Synthetic fixture defect", defect.error.safeMessage)

    val clientError = EffectRpcCodec.decodeFrame(fixture("rpc/client-protocol-error.json"))
      as RpcInboundFrame.ClientProtocolError
    assertEquals("ClientProtocolError", clientError.error.frameTag)
    assertEquals("Synthetic fixture protocol error", clientError.error.safeMessage)
  }

  @Test
  public fun `malformed or unknown frames fail as protocol violations`() {
    assertThrows(ProtocolViolationFailure::class.java) {
      EffectRpcCodec.decodeFrame("{\"_tag\":\"FutureFrame\"}")
    }
    assertThrows(ProtocolViolationFailure::class.java) {
      EffectRpcCodec.decodeFrame("{\"_tag\":\"Chunk\",\"requestId\":2,\"values\":[]}")
    }
    assertThrows(ProtocolViolationFailure::class.java) {
      EffectRpcCodec.decodeShellItem(
        json.parseToJsonElement("{\"kind\":\"thread-removed\",\"sequence\":1.5,\"threadId\":\"thread\"}"),
      )
    }
  }

  private fun fixtureObject(path: String): JsonObject = fixture(path).let(json::parseToJsonElement).jsonObject

  private fun fixture(path: String): String {
    val resource = javaClass.classLoader?.getResourceAsStream("contracts/foundation/$path")
    assertNotNull("Missing fixture $path", resource)
    return requireNotNull(resource).bufferedReader(Charsets.UTF_8).use { it.readText() }
  }

  private fun JsonObject.requiredObject(name: String): JsonObject =
    requireNotNull(this[name]) { "Missing $name" }.jsonObject

  private fun ShellStreamDecodeResult.item(): OrchestrationShellStreamItem =
    (this as ShellStreamDecodeResult.Decoded).item
}
