package com.discord.oauth2rpc

import com.discord.oauth2rpc.utils.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.random.Random

class GatewayClient {

    private var httpClient: HttpClient? = null
    private var wsSession: WebSocketSession? = null
    private var processingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var helloTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var lastAck = true
    private var lastHeartbeatAt = 0L
    private var ping = -1

    private var sessionState: SessionState? = null
    private var liveSeq = 0
    private var token = ""
    private var closed = false

    var onReady: ((ReadyEvent) -> Unit)? = null
    var onClose: ((GatewayCloseInfo) -> Unit)? = null
    var onDispatch: ((eventName: String, data: JsonElement?, seq: Int?) -> Unit)? = null
    var onSent: ((Any?) -> Unit)? = null
    var onSession: ((SessionUpdateEvent) -> Unit)? = null
    var onInvalidSession: ((Boolean) -> Unit)? = null
    var onOpen: (() -> Unit)? = null
    var onHello: ((heartbeatInterval: Int) -> Unit)? = null
    var onIdentify: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onResumed: ((Any?) -> Unit)? = null
    var onPacket: ((GatewayPacket) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onDebug: ((String) -> Unit)? = null

    val latency: Int get() = ping

    fun getSession(): SessionState? = sessionState?.copy()

    suspend fun connect(opts: GatewayConnectOptions) {
        if (wsSession != null) throw IllegalStateException("GatewayClient already connected")

        token = opts.token
        sessionState = opts.session?.copy()
        liveSeq = opts.session?.seq ?: 0
        closed = false
        lastAck = true

        val base = opts.session?.resumeGatewayUrl ?: opts.gatewayUrl ?: DEFAULTS.GATEWAY_URL
        val version = opts.version ?: DEFAULTS.GATEWAY_VERSION
        val url = "$base/?v=$version&encoding=json"
        val ready = CompletableDeferred<Unit>()

        debug("[gateway] connecting $url")

        httpClient = HttpClient { install(WebSockets) }
        val session = httpClient!!.webSocketSession(url)
        wsSession = session
        onOpen?.invoke()

        val helloTimeout = opts.helloTimeoutMs ?: DEFAULTS.HELLO_TIMEOUT_MS
        helloTimerJob = scope.launch {
            delay(helloTimeout)
            debug("[gateway] HELLO timeout")
            session.close(CloseReason(4009, "HELLO timeout"))
            ready.completeExceptionally(Exception("HELLO timeout"))
        }

        processingJob = scope.launch {
            try {
                for (frame in session.incoming) {
                    when (frame) {
                        is Frame.Text -> handleMessage(frame.readText(), opts, ready)
                        is Frame.Close -> {
                            handleClose("", 1000)
                            if (!ready.isCompleted) ready.completeExceptionally(Exception("Gateway closed before ready"))
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                if (!ready.isCompleted) ready.completeExceptionally(e)
                onError?.invoke(e)
            }
        }

        ready.await()
    }

    fun send(op: Int, d: JsonObject?): Boolean {
        val session = wsSession ?: return false
        scope.launch {
            try {
                val jsonStr = """{"op":$op,"d":$d}"""
                session.send(Frame.Text(jsonStr))
                onSent?.invoke(mapOf("op" to op, "d" to d))
            } catch (e: Exception) { onError?.invoke(e) }
        }
        return true
    }

    fun close(code: Int = 1000, reason: String? = null) {
        scope.launch { try { wsSession?.close(CloseReason(code.toShort(), reason ?: "")) } catch (_: Exception) {} }
    }

    fun disconnect() {
        closed = true
        stopHeartbeat()
        helloTimerJob?.cancel()
        processingJob?.cancel()
        scope.launch {
            try { wsSession?.close() } catch (_: Exception) {}
            wsSession = null
            httpClient?.close()
            httpClient = null
        }
    }

    private fun handleMessage(raw: String, opts: GatewayConnectOptions, ready: CompletableDeferred<Unit>) {
        try {
            val json = Json.parseToJsonElement(raw).jsonObject
            val op = json["op"]!!.jsonPrimitive.int
            val d = json["d"]
            val s = json["s"]?.jsonPrimitive?.intOrNull
            val t = json["t"]?.jsonPrimitive?.contentOrNull

            if (s != null && s > liveSeq) { liveSeq = s; touchSession(seq = s) }

            onPacket?.invoke(GatewayPacket(op, d, s, t))

            when (op) {
                GatewayOp.HELLO -> {
                    helloTimerJob?.cancel()
                    val interval = d!!.jsonObject["heartbeat_interval"]!!.jsonPrimitive.int
                    startHeartbeat(interval.toLong())
                    debug("[gateway] HELLO received, heartbeat_interval=${interval}ms")
                    onHello?.invoke(interval)
                    if (sessionState != null) sendResume() else sendIdentify(opts)
                }
                GatewayOp.HEARTBEAT_ACK -> {
                    lastAck = true
                    ping = (System.currentTimeMillis() - lastHeartbeatAt).toInt()
                    debug("[gateway] heartbeat ack (${ping}ms)")
                }
                GatewayOp.HEARTBEAT -> {
                    debug("[gateway] received server heartbeat, sending forced heartbeat")
                    sendHeartbeat(force = true)
                }
                GatewayOp.RECONNECT -> {
                    debug("[gateway] server requested RECONNECT")
                    forceClose(4000, "server reconnect")
                }
                GatewayOp.INVALID_SESSION -> {
                    val resumable = d?.jsonPrimitive?.boolean ?: false
                    debug("[gateway] INVALID_SESSION resumable=$resumable")
                    if (!resumable) { sessionState = null; touchSession(sessionId = null, resumeGatewayUrl = null, seq = 0) }
                    onInvalidSession?.invoke(resumable)
                    forceClose(if (resumable) 4000 else 1000, "invalid session")
                }
                GatewayOp.DISPATCH -> handleDispatch(t ?: "", d, s)
            }
        } catch (e: Exception) { onError?.invoke(e) }
    }

    private fun handleDispatch(t: String, d: JsonElement?, s: Int?) {
        when (t) {
            "READY" -> {
                val obj = d!!.jsonObject
                val userObj = obj["user"]!!.jsonObject
                val re = ReadyEvent(
                    ReadyUser(userObj["id"]!!.jsonPrimitive.content, userObj["username"]!!.jsonPrimitive.content, userObj["global_name"]?.jsonPrimitive?.contentOrNull),
                    obj["session_id"]!!.jsonPrimitive.content, obj["resume_gateway_url"]!!.jsonPrimitive.content
                )
                debug("[gateway] READY: user=${re.user.username} (${re.user.id}) session=${re.sessionId}")
                sessionState = SessionState(re.sessionId, liveSeq, re.resumeGatewayUrl)
                touchSession(re.sessionId, liveSeq, re.resumeGatewayUrl)
                onReady?.invoke(re)
            }
            "RESUMED" -> {
                debug("[gateway] RESUMED: session restored, seq=$liveSeq")
                touchSession(sessionState?.sessionId, liveSeq, sessionState?.resumeGatewayUrl)
                onResumed?.invoke(d)
            }
            else -> debug("[gateway] dispatch $t seq=${s ?: liveSeq}")
        }
        onDispatch?.invoke(t, d, s)
    }

    private fun sendIdentify(opts: GatewayConnectOptions) {
        val id = opts.identify ?: IdentifyPayload()
        val caps = GatewayCapabilities(id.capabilities ?: 0).apply {
            if (id.capabilities == null) {
                add(GatewayCapabilities.FLAGS["DEDUPE_USER_OBJECTS"]!!)
                add(GatewayCapabilities.FLAGS["PRIORITIZED_READY_PAYLOAD"]!!)
                add(GatewayCapabilities.FLAGS["AUTO_CALL_CONNECT"]!!)
                add(GatewayCapabilities.FLAGS["AUTO_LOBBY_CONNECT"]!!)
            }
            freeze()
        }
        val ints = Intents(id.intents ?: 0).apply {
            if (id.intents == null) {
                add(Intents.FLAGS["DIRECT_MESSAGES"]!!); add(Intents.FLAGS["PRIVATE_CHANNELS"]!!)
                add(Intents.FLAGS["CALLS"]!!); add(Intents.FLAGS["USER_RELATIONSHIPS"]!!)
                add(Intents.FLAGS["USER_PRESENCE"]!!); add(Intents.FLAGS["LOBBIES"]!!)
                add(Intents.FLAGS["LOBBY_DELETE"]!!); add(Intents.FLAGS["UNKNOWN_29"]!!)
            }
            freeze()
        }
        val d = buildJsonObject {
            put("capabilities", caps.bitfield); put("intents", ints.bitfield); put("token", token)
            putJsonObject("properties") {
                DEFAULT_SUPER_PROPERTIES.forEach { (k, v) ->
                    when (v) { is String -> put(k, v); is Int -> put(k, v); is Boolean -> put(k, v) }
                }
            }
        }
        onIdentify?.invoke(); debug("[gateway] sending IDENTIFY")
        send(GatewayOp.IDENTIFY, d)
    }

    private fun sendResume() {
        val s = sessionState ?: return
        onResume?.invoke(); debug("[gateway] sending RESUME")
        send(GatewayOp.RESUME, buildJsonObject {
            put("token", token); put("session_id", s.sessionId); put("seq", s.seq)
        })
    }

    private fun startHeartbeat(intervalMs: Long) {
        stopHeartbeat()
        debug("[gateway] heartbeat every ${intervalMs}ms")
        val firstDelay = (intervalMs * Random.nextDouble()).toLong()
        heartbeatJob = scope.launch {
            delay(firstDelay)
            if (wsSession != null) {
                sendHeartbeat()
                while (isActive) { delay(intervalMs); sendHeartbeat() }
            }
        }
    }

    private fun sendHeartbeat(force: Boolean = false) {
        if (!force && !lastAck) { debug("[gateway] zombie connection; closing 4009"); forceClose(4009, "heartbeat ack missed"); return }
        lastAck = false; lastHeartbeatAt = System.currentTimeMillis()
        val seq = if (liveSeq > 0) liveSeq else null
        val data = if (seq != null) buildJsonObject { put("seq", seq) } else null
        send(GatewayOp.HEARTBEAT, data ?: buildJsonObject {})
        debug("[gateway] heartbeat dispatched seq=$seq")
    }

    private fun stopHeartbeat() { heartbeatJob?.cancel(); heartbeatJob = null }

    private fun touchSession(sessionId: String? = null, seq: Int = liveSeq, resumeGatewayUrl: String? = null) {
        onSession?.invoke(SessionUpdateEvent(sessionId ?: sessionState?.sessionId, seq, resumeGatewayUrl ?: sessionState?.resumeGatewayUrl))
    }

    private fun forceClose(code: Int, reason: String) {
        scope.launch { try { wsSession?.close(CloseReason(code.toShort(), reason)) } catch (_: Exception) {} }
    }

    private fun handleClose(reason: String, code: Int) {
        if (closed) return
        closed = true; stopHeartbeat(); helloTimerJob?.cancel()
        val fatal = NON_RESUMABLE_CLOSE_CODES.contains(code)
        val snapshot = sessionState?.copy(seq = liveSeq)
        if (fatal) sessionState = null
        wsSession = null
        onClose?.invoke(GatewayCloseInfo(code, reason, !fatal && snapshot != null, snapshot))
        debug("[gateway] close code=$code reason=$reason resumable=${!fatal && snapshot != null}")
    }

    private fun debug(msg: String) { onDebug?.invoke(msg) }
}
