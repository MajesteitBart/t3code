package com.t3tools.t3code.compose.core.testing.integration

import com.t3tools.t3code.compose.core.protocol.ContractJson
import com.t3tools.t3code.compose.core.protocol.DirectPairingResult
import com.t3tools.t3code.compose.core.protocol.DispatchResult
import com.t3tools.t3code.compose.core.protocol.EnvironmentHttpClient
import com.t3tools.t3code.compose.core.protocol.OneAttemptHttpClient
import com.t3tools.t3code.compose.core.protocol.OrchestrationShellSnapshot
import com.t3tools.t3code.compose.core.protocol.OrchestrationThreadDetailSnapshot
import com.t3tools.t3code.compose.core.protocol.PairingTarget
import com.t3tools.t3code.compose.core.protocol.PairingUrl
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.ConnectionPool

internal const val COLLIDING_PROJECT_ID: String = "project-native-android-collision"
internal const val COLLIDING_THREAD_ID: String = "thread-native-android-collision"
internal const val PRECREATED_TURN_THREAD_ID: String = "thread-native-android-turn"

private const val CONTROL_PREFIX = "T3_NATIVE_ANDROID_CONTROL "
private const val STARTUP_TIMEOUT_SECONDS = 90L
private const val CONTROL_TIMEOUT_SECONDS = 30L
private const val PROCESS_STOP_TIMEOUT_SECONDS = 20L
private const val MAX_DIAGNOSTIC_LINES = 80
private const val CREATED_AT = "2026-08-08T00:00:00.000Z"

@Serializable
internal data class IntegrationServerDescription(
  val kind: String? = null,
  val pid: Long,
  val port: Int,
  val workingDirectory: String,
  val baseDirectory: String,
)

@Serializable
internal data class IntegrationCommandReceipt(
  val commandId: String,
  val aggregateKind: String,
  val aggregateId: String,
  val acceptedAt: String,
  val resultSequence: Long,
  val status: String,
  val error: String?,
)

@Serializable
internal data class IntegrationDrainResult(
  val latestSequence: Long,
  val snapshotSequence: Long,
)

internal object HarnessDiagnostics {
  private val ansiEscape = Regex("\\u001B\\[[;?0-9]*[ -/]*[@-~]")
  private val fragmentToken = Regex("(?i)(#token=)[^&\\s]+")
  private val bearerToken = Regex("(?i)(bearer\\s+)[^\\s]+")
  private val labeledSecret = Regex(
    "(?i)((?:token|credential|authorization|secret)\\s*[:=]\\s*)[^\\s]+",
  )

  fun redact(line: String): String {
    val plain = ansiEscape.replace(line, "")
    return labeledSecret.replace(
      bearerToken.replace(fragmentToken.replace(plain, "$1<redacted>"), "$1<redacted>"),
      "$1<redacted>",
    )
  }
}

internal class DisposableT3IntegrationHarness private constructor(
  val repositoryRoot: Path,
  val fixtureRoot: Path,
  first: DisposableT3Server,
  second: DisposableT3Server,
) : AutoCloseable {
  var first: DisposableT3Server = first
    private set
  var second: DisposableT3Server = second
    private set

  fun stopFirst() {
    first.close()
  }

  fun stopSecond() {
    second.close()
  }

  suspend fun restartFirst(): DisposableT3Server {
    first = restart(first)
    return first
  }

  suspend fun restartSecond(): DisposableT3Server {
    second = restart(second)
    return second
  }

  private suspend fun restart(previous: DisposableT3Server): DisposableT3Server {
    check(!previous.isAlive) { "The disposable server must be stopped before restart." }
    return DisposableT3Server.start(
      repositoryRoot = repositoryRoot,
      baseDirectory = previous.baseDirectory,
      name = previous.name,
      requiredPort = previous.port,
      existingPairing = previous.pairing,
    )
  }

  override fun close() {
    val failures = mutableListOf<Throwable>()
    listOf(second, first).forEach { server ->
      runCatching(server::close).exceptionOrNull()?.let(failures::add)
    }
    runCatching { deleteFixtureRoot(repositoryRoot, fixtureRoot) }.exceptionOrNull()?.let(failures::add)
    if (failures.isNotEmpty()) {
      val failure = IllegalStateException("Failed to tear down the disposable T3 fixture.")
      failures.forEach(failure::addSuppressed)
      throw failure
    }
  }

  companion object {
    suspend fun start(repositoryRoot: Path = configuredRepositoryRoot()): DisposableT3IntegrationHarness {
      val canonicalRepositoryRoot = repositoryRoot.toRealPath()
      val worktreeStateRoot = canonicalRepositoryRoot.resolve(".t3")
      Files.createDirectories(worktreeStateRoot)
      check(worktreeStateRoot.toRealPath() == worktreeStateRoot.toAbsolutePath().normalize()) {
        "The worktree test-state root may not redirect outside the repository."
      }
      val fixtureRoot = Files.createTempDirectory(worktreeStateRoot, "native-android-integration-")
      var first: DisposableT3Server? = null
      var second: DisposableT3Server? = null
      try {
        first = DisposableT3Server.start(
          repositoryRoot = canonicalRepositoryRoot,
          baseDirectory = fixtureRoot.resolve("environment-a"),
          name = "environment-a",
        )
        second = DisposableT3Server.start(
          repositoryRoot = canonicalRepositoryRoot,
          baseDirectory = fixtureRoot.resolve("environment-b"),
          name = "environment-b",
        )
        first.seedCollidingState()
        second.seedCollidingState()
        return DisposableT3IntegrationHarness(
          repositoryRoot = canonicalRepositoryRoot,
          fixtureRoot = fixtureRoot,
          first = first,
          second = second,
        )
      } catch (failure: Throwable) {
        runCatching { second?.close() }
        runCatching { first?.close() }
        runCatching { deleteFixtureRoot(canonicalRepositoryRoot, fixtureRoot) }
        throw failure
      }
    }

    private fun configuredRepositoryRoot(): Path {
      val configured = System.getProperty("t3.repositoryRoot")
        ?: throw IllegalStateException("The t3.repositoryRoot test property is required.")
      return Paths.get(configured)
    }
  }
}

internal class DisposableT3Server private constructor(
  private val repositoryRoot: Path,
  val name: String,
  val baseDirectory: Path,
  private val process: Process,
) : AutoCloseable {
  private val json = ContractJson.format
  private val client = EnvironmentHttpClient(
    OneAttemptHttpClient(
      OneAttemptHttpClient.defaultClient().newBuilder()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .build(),
    ),
  )
  private val requestSequence = AtomicLong()
  private val ready = CompletableFuture<IntegrationServerDescription>()
  private val pairingTarget = CompletableFuture<PairingTarget>()
  private val pendingControls = ConcurrentHashMap<String, CompletableFuture<JsonElement>>()
  private val diagnostics = ConcurrentLinkedDeque<String>()
  private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
  private val readerThread = Thread(::readProcessOutput, "t3-native-integration-$name-output").apply {
    isDaemon = true
    start()
  }
  @Volatile
  private var closed = false

  lateinit var description: IntegrationServerDescription
    private set
  lateinit var pairing: DirectPairingResult
    private set

  val pid: Long get() = hostProcessPid(process)
  val port: Int get() = description.port
  val isAlive: Boolean get() = process.isAlive

  suspend fun seedCollidingState() {
    val workspaceRoot = baseDirectory.resolve("synthetic-workspace").absolutePathString()
    Files.createDirectories(Paths.get(workspaceRoot))
    dispatch(
      buildJsonObject {
        put("type", "project.create")
        put("commandId", "$name-project-create")
        put("projectId", COLLIDING_PROJECT_ID)
        put("title", "Synthetic project for $name")
        put("workspaceRoot", workspaceRoot)
        put("createWorkspaceRootIfMissing", false)
        put("createdAt", CREATED_AT)
      },
    )
    dispatch(threadCreateCommand(COLLIDING_THREAD_ID, "$name-thread-create", "Collision thread"))
    dispatch(
      threadCreateCommand(
        PRECREATED_TURN_THREAD_ID,
        "$name-turn-thread-create",
        "Stable turn identity thread",
      ),
    )
    drainWorkers()
  }

  suspend fun dispatch(command: JsonObject): DispatchResult = client.dispatch(
    httpBaseUrl = pairing.httpBaseUrl,
    credential = pairing.accessCredential,
    command = command,
    timeout = 30.seconds,
  )

  suspend fun shellSnapshot(): OrchestrationShellSnapshot = client.shellSnapshot(
    httpBaseUrl = pairing.httpBaseUrl,
    credential = pairing.accessCredential,
    timeout = 30.seconds,
  )

  suspend fun threadSnapshot(threadId: String): OrchestrationThreadDetailSnapshot =
    client.threadSnapshot(
      httpBaseUrl = pairing.httpBaseUrl,
      credential = pairing.accessCredential,
      threadId = threadId,
      timeout = 30.seconds,
    )

  suspend fun controlledShellSnapshot(): OrchestrationShellSnapshot =
    json.decodeFromJsonElement(control("snapshot"))

  suspend fun controlledThreadSnapshot(threadId: String): OrchestrationThreadDetailSnapshot? {
    val value = control("threadSnapshot", "threadId" to JsonPrimitive(threadId))
    return if (value is JsonNull) null else json.decodeFromJsonElement(value)
  }

  suspend fun receipt(commandId: String): IntegrationCommandReceipt? {
    val value = control("receipt", "commandId" to JsonPrimitive(commandId))
    return if (value is JsonNull) null else json.decodeFromJsonElement(value)
  }

  suspend fun events(commandId: String): List<JsonObject> =
    control("events", "commandId" to JsonPrimitive(commandId))
      .jsonArray
      .map(JsonElement::jsonObject)

  suspend fun drainWorkers(): IntegrationDrainResult =
    json.decodeFromJsonElement(control("drain"))

  suspend fun revokeMobileSessions(): Int =
    control("revokeMobileSessions")
      .jsonObject
      .getValue("revokedCount")
      .jsonPrimitive
      .content
      .toInt()

  fun redactedDiagnostics(): List<String> = diagnostics.toList()

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    val expectedPid = pid
    check(pid == expectedPid) { "Spawned process identity changed before teardown." }
    runCatching { writer.close() }
    if (process.isAlive) {
      process.destroy()
      if (!process.waitFor(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        check(pid == expectedPid) { "Refusing to force-stop an unexpected process." }
        process.destroyForcibly()
        check(process.waitFor(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          "Exact server PID $expectedPid did not stop."
        }
      }
    }
    readerThread.join(TimeUnit.SECONDS.toMillis(5))
  }

  private fun threadCreateCommand(threadId: String, commandId: String, title: String): JsonObject =
    buildJsonObject {
      put("type", "thread.create")
      put("commandId", commandId)
      put("threadId", threadId)
      put("projectId", COLLIDING_PROJECT_ID)
      put("title", title)
      put(
        "modelSelection",
        buildJsonObject {
          put("instanceId", "native-integration-missing-provider")
          put("model", "synthetic-model")
        },
      )
      put("runtimeMode", "approval-required")
      put("interactionMode", "default")
      put("branch", JsonNull)
      put("worktreePath", JsonNull)
      put("createdAt", CREATED_AT)
    }

  private suspend fun control(
    operation: String,
    vararg fields: Pair<String, JsonElement>,
  ): JsonElement = withContext(Dispatchers.IO) {
    check(process.isAlive) { failureMessage("Disposable server is not running.") }
    val requestId = "$name-${requestSequence.incrementAndGet()}"
    val response = CompletableFuture<JsonElement>()
    check(pendingControls.putIfAbsent(requestId, response) == null)
    val request = buildJsonObject {
      put("requestId", requestId)
      put("operation", operation)
      fields.forEach { (key, value) -> put(key, value) }
    }
    try {
      synchronized(writer) {
        writer.write(json.encodeToString(request))
        writer.newLine()
        writer.flush()
      }
      response.get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (timeout: TimeoutException) {
      throw IllegalStateException(failureMessage("Timed out waiting for control operation $operation."), timeout)
    } finally {
      pendingControls.remove(requestId)
    }
  }

  private fun readProcessOutput() {
    try {
      BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).useLines { lines ->
        lines.forEach(::handleOutputLine)
      }
      val exitCode = process.waitFor()
      failPending(IllegalStateException(failureMessage("Disposable server exited with code $exitCode.")))
    } catch (failure: Throwable) {
      failPending(IllegalStateException(failureMessage("Disposable server output reader failed."), failure))
    }
  }

  private fun handleOutputLine(rawLine: String) {
    val line = HarnessDiagnostics.redact(rawLine)
    if (rawLine.startsWith(CONTROL_PREFIX)) {
      handleControlLine(rawLine.removePrefix(CONTROL_PREFIX))
      return
    }
    if (rawLine.trimStart().startsWith("Pairing URL:")) {
      val rawPairingUrl = rawLine.substringAfter("Pairing URL:").trim()
      runCatching { PairingUrl.resolve(rawPairingUrl) }
        .onSuccess(pairingTarget::complete)
        .onFailure(pairingTarget::completeExceptionally)
    }
    diagnostics.addLast(line)
    while (diagnostics.size > MAX_DIAGNOSTIC_LINES) diagnostics.pollFirst()
  }

  private fun handleControlLine(payload: String) {
    val value = runCatching { json.parseToJsonElement(payload).jsonObject }
      .getOrElse {
        failPending(IllegalStateException(failureMessage("Server emitted malformed control JSON."), it))
        return
      }
    if (value["kind"]?.jsonPrimitive?.contentOrNull == "ready") {
      runCatching { json.decodeFromJsonElement<IntegrationServerDescription>(value) }
        .onSuccess(ready::complete)
        .onFailure(ready::completeExceptionally)
      return
    }
    val requestId = value["requestId"]?.jsonPrimitive?.contentOrNull ?: return
    val response = pendingControls[requestId] ?: return
    if (value["ok"]?.jsonPrimitive?.contentOrNull == "true") {
      response.complete(value["value"] ?: JsonNull)
    } else {
      val code = value["error"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
        ?: "UNKNOWN_CONTROL_ERROR"
      response.completeExceptionally(IllegalStateException("Control operation failed with $code."))
    }
  }

  private fun failPending(failure: Throwable) {
    ready.completeExceptionally(failure)
    pairingTarget.completeExceptionally(failure)
    pendingControls.values.forEach { it.completeExceptionally(failure) }
  }

  private fun failureMessage(message: String): String {
    val output = redactedDiagnostics().takeLast(20).joinToString(separator = "\n")
    return if (output.isEmpty()) message else "$message\nRedacted server output:\n$output"
  }

  companion object {
    suspend fun start(
      repositoryRoot: Path,
      baseDirectory: Path,
      name: String,
      requiredPort: Int? = null,
      existingPairing: DirectPairingResult? = null,
    ): DisposableT3Server =
      withContext(Dispatchers.IO) {
        Files.createDirectories(baseDirectory)
        val entry = repositoryRoot.resolve(
          "apps/server/integration/nativeAndroidIntegrationServer.ts",
        )
        check(Files.isRegularFile(entry)) { "Missing native Android integration server entry." }
        val command = mutableListOf(
          "node",
          entry.absolutePathString(),
          "--host",
          "127.0.0.1",
          "--base-dir",
          baseDirectory.absolutePathString(),
          "--no-browser",
        )
        requiredPort?.let { port -> command += listOf("--port", port.toString()) }
        val processBuilder = ProcessBuilder(command)
          .directory(repositoryRoot.toFile())
          .redirectErrorStream(true)
        isolateEnvironment(processBuilder.environment())
        val server = DisposableT3Server(
          repositoryRoot = repositoryRoot,
          name = name,
          baseDirectory = baseDirectory,
          process = processBuilder.start(),
        )
        try {
          server.description = server.ready.get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          validateDescription(server, repositoryRoot, baseDirectory)
          verifyExactPortOwner(server.description.port, server.pid, repositoryRoot)
          requiredPort?.let { port ->
            check(server.description.port == port) { "Restart did not reclaim its exact port." }
          }
          if (existingPairing == null) {
            val target = server.pairingTarget.get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            check(java.net.URI(target.httpBaseUrl).port == server.description.port) {
              "Pairing target did not use the captured server port."
            }
            server.pairing = server.client.pair(
              target = target,
              clientLabel = "native-android-integration-$name",
              timeout = 30.seconds,
            )
          } else {
            val descriptor = server.client.descriptor(existingPairing.httpBaseUrl, 30.seconds)
            check(descriptor.environmentId == existingPairing.descriptor.environmentId) {
              "Restarted server changed its persisted environment identity."
            }
            server.pairing = existingPairing
          }
          server
        } catch (failure: Throwable) {
          runCatching(server::close)
          throw IllegalStateException(server.failureMessage("Failed to start $name."), failure)
        }
      }

    private fun isolateEnvironment(environment: MutableMap<String, String>) {
      listOf(
        "T3CODE_HOME",
        "T3CODE_PORT",
        "T3CODE_HOST",
        "T3CODE_BOOTSTRAP_FD",
        "T3CODE_AUTO_BOOTSTRAP_PROJECT_FROM_CWD",
        "T3CODE_TAILSCALE_SERVE",
        "VITE_DEV_SERVER_URL",
        "VITE_HTTP_URL",
        "VITE_WS_URL",
      ).forEach(environment::remove)
    }

    private fun validateDescription(
      server: DisposableT3Server,
      repositoryRoot: Path,
      baseDirectory: Path,
    ) {
      check(server.description.pid == server.pid) { "Control PID did not match the exact spawned PID." }
      check(Paths.get(server.description.workingDirectory).toRealPath() == repositoryRoot.toRealPath()) {
        "Disposable server working directory did not match the repository root."
      }
      check(Paths.get(server.description.baseDirectory).toRealPath() == baseDirectory.toRealPath()) {
        "Disposable server base directory did not match its isolated fixture directory."
      }
      check(server.description.port in 1..65_535) { "Disposable server reported an invalid port." }
    }
  }
}

private fun verifyExactPortOwner(port: Int, expectedPid: Long, repositoryRoot: Path) {
  val os = System.getProperty("os.name").orEmpty().lowercase()
  val ownsPort = when {
    os.contains("win") -> windowsPortOwners(port, repositoryRoot) == setOf(expectedPid)
    os.contains("linux") -> linuxProcessOwnsPort(port, expectedPid)
    os.contains("mac") -> macPortOwners(port, repositoryRoot) == setOf(expectedPid)
    else -> false
  }
  check(ownsPort) { "Port $port was not owned exclusively by captured PID $expectedPid." }
}

private fun windowsPortOwners(port: Int, repositoryRoot: Path): Set<Long> {
  val command =
    "\$owners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction Stop | " +
      "Select-Object -ExpandProperty OwningProcess -Unique); [Console]::Out.Write((\$owners -join ','))"
  return runOwnershipCommand(
    listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command),
    repositoryRoot,
  )
}

private fun macPortOwners(port: Int, repositoryRoot: Path): Set<Long> = runOwnershipCommand(
  listOf("/usr/sbin/lsof", "-nP", "-iTCP:$port", "-sTCP:LISTEN", "-t"),
  repositoryRoot,
)

private fun runOwnershipCommand(command: List<String>, repositoryRoot: Path): Set<Long> {
  val checker = ProcessBuilder(command)
    .directory(repositoryRoot.toFile())
    .redirectErrorStream(true)
    .start()
  if (!checker.waitFor(15, TimeUnit.SECONDS)) {
    checker.destroyForcibly()
    checker.waitFor(15, TimeUnit.SECONDS)
    throw IllegalStateException("Port ownership check did not exit.")
  }
  val output = BufferedReader(InputStreamReader(checker.inputStream, StandardCharsets.UTF_8))
    .use { it.readText() }
    .trim()
  check(checker.exitValue() == 0) { "Port ownership check failed." }
  return output
    .split(',', '\n', '\r')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .map(String::toLong)
    .toSet()
}

private fun hostProcessPid(process: Process): Long {
  val method = Process::class.java.getMethod("pid")
  return (method.invoke(process) as Number).toLong()
}

private fun linuxProcessOwnsPort(port: Int, expectedPid: Long): Boolean {
  val listeningInodes = sequenceOf("/proc/net/tcp", "/proc/net/tcp6")
    .map(Paths::get)
    .filter(Files::isReadable)
    .flatMap { table ->
      Files.readAllLines(table).asSequence().drop(1).mapNotNull { line ->
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.size < 10) return@mapNotNull null
        val localPort = fields[1].substringAfterLast(':').toIntOrNull(16)
        val state = fields[3]
        if (localPort == port && state == "0A") fields[9] else null
      }
    }
    .toSet()
  if (listeningInodes.isEmpty()) return false
  val descriptors = Paths.get("/proc/$expectedPid/fd")
  if (!descriptors.isDirectory()) return false
  return Files.list(descriptors).use { stream ->
    stream.anyMatch { descriptor ->
      val target = runCatching { Files.readSymbolicLink(descriptor).toString() }.getOrNull()
      target != null && listeningInodes.any { inode -> target == "socket:[$inode]" }
    }
  }
}

private fun deleteFixtureRoot(repositoryRoot: Path, fixtureRoot: Path) {
  val allowedRoot = repositoryRoot.resolve(".t3").toAbsolutePath().normalize()
  val target = fixtureRoot.toAbsolutePath().normalize()
  check(target.parent == allowedRoot && target.name.startsWith("native-android-integration-")) {
    "Refusing to delete an unexpected fixture directory."
  }
  if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return
  Files.walk(target).use { paths ->
    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
  }
}
