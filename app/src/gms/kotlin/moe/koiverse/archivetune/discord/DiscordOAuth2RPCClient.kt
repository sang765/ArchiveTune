package moe.koiverse.archivetune.discord

import com.discord.oauth2rpc.GatewayClient
import com.discord.oauth2rpc.GatewayConnectOptions
import com.discord.oauth2rpc.IdentifyPayload
import com.discord.oauth2rpc.structures.RichPresence
import com.discord.oauth2rpc.utils.GatewayOp
import com.discord.oauth2rpc.utils.JsonObjectMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import timber.log.Timber

object DiscordOAuth2RPCClient {
    private const val TAG = "DiscordOAuth2RPCClient"

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gateway: GatewayClient? = null
    private var sessionId: String? = null
    private var activeToken: String? = null
    private var ready = false
    private var connectJob: Job? = null

    val isConnected: Boolean
        get() = ready && gateway != null

    suspend fun connect(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isConnected && activeToken == accessToken) {
                return@withLock Result.success(Unit)
            }

            disconnectInternal()

            val token = accessToken.trim()
            if (token.isBlank()) {
                return@withLock Result.failure(IllegalArgumentException("Discord access token is missing"))
            }

            activeToken = token
            ready = false

            val gws = GatewayClient()
            val connectResult = CompletableDeferred<Result<Unit>>()
            var timeout = true

            gws.onReady = { ev ->
                sessionId = ev.sessionId
                ready = true
                timeout = false
                if (!connectResult.isCompleted) {
                    connectResult.complete(Result.success(Unit))
                }
                Timber.tag(TAG).i("Gateway connected: user=%s session=%s", ev.user.username, ev.sessionId)
            }

            gws.onClose = { info ->
                ready = false
                gateway = null
                if (!connectResult.isCompleted) {
                    connectResult.complete(Result.failure(Exception("Gateway closed: code=${info.code} reason=${info.reason}")))
                }
                Timber.tag(TAG).w("Gateway closed: code=%d reason=%s resumable=%s", info.code, info.reason, info.resumable)
            }

            gws.onError = { err ->
                Timber.tag(TAG).e(err, "Gateway error")
            }

            gws.onDebug = { msg ->
                Timber.tag(TAG).v("Gateway: %s", msg)
            }

            gateway = gws

            connectJob = scope.launch {
                try {
                    gws.connect(GatewayConnectOptions(
                        token = token,
                        identify = IdentifyPayload(capabilities = 0),
                        helloTimeoutMs = 15_000L,
                    ))
                } catch (e: Exception) {
                    if (!connectResult.isCompleted) {
                        connectResult.complete(Result.failure(e))
                    }
                }
            }

            val result = withTimeout(20_000L) {
                connectResult.await()
            }

            if (result.isFailure) {
                gateway = null
                activeToken = null
                sessionId = null
            }

            result
        }
    }

    suspend fun updatePresence(
        accessToken: String,
        activity: DiscordPresenceActivity,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val connectResult = connect(accessToken)
            if (connectResult.isFailure) {
                return@withLock connectResult
            }

            val gws = gateway ?: return@withLock Result.failure(IllegalStateException("Gateway not connected"))
            if (!ready) {
                return@withLock Result.failure(IllegalStateException("Gateway not ready"))
            }

            try {
                val presence = buildRichPresence(activity)
                val activityJson = Json.parseToJsonElement(JsonObjectMapper.mapToJson(presence))
                val payload = buildJsonObject {
                    putJsonArray("activities") {
                        add(activityJson)
                    }
                    put("afk", false)
                    put("since", 0)
                    val status = when (activity.onlineStatus) {
                        DiscordOnlineStatus.Online -> "online"
                        DiscordOnlineStatus.Idle -> "idle"
                        DiscordOnlineStatus.Dnd -> "dnd"
                        DiscordOnlineStatus.Invisible -> "invisible"
                        DiscordOnlineStatus.Streaming -> "online"
                    }
                    put("status", status)
                }
                gws.send(GatewayOp.PRESENCE_UPDATE, payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to update presence")
                Result.failure(e)
            }
        }
    }

    suspend fun clearPresence(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val gws = gateway ?: return@withLock Result.success(Unit)
            if (!ready) return@withLock Result.success(Unit)
            try {
                val payload = buildJsonObject {
                    putJsonArray("activities") { }
                    put("afk", false)
                    put("since", 0)
                    put("status", "online")
                }
                gws.send(GatewayOp.PRESENCE_UPDATE, payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to clear presence")
                Result.failure(e)
            }
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            disconnectInternal()
            Result.success(Unit)
        }
    }

    private fun disconnectInternal() {
        connectJob?.cancel()
        connectJob = null
        gateway?.disconnect()
        gateway = null
        sessionId = null
        ready = false
        activeToken = null
    }

    private fun buildRichPresence(activity: DiscordPresenceActivity): Map<String, Any?> {
        val rp = RichPresence(sessionId)
            .setApplicationId(activity.applicationId.toString())
            .setType(activity.type.nativeValue)
            .setName(activity.name)
            .setDetails(activity.details)
            .setState(activity.state)
            .setURL(activity.detailsUrl)

        if (activity.timestamps.startEpochSeconds != null) {
            rp.setStartTimestamp(activity.timestamps.startEpochSeconds!! * 1000L)
        }
        if (activity.timestamps.endEpochSeconds != null) {
            rp.setEndTimestamp(activity.timestamps.endEpochSeconds!! * 1000L)
        }

        if (activity.assets.largeImage != null) {
            rp.setAssetsLargeImage(activity.assets.largeImage)
        }
        if (activity.assets.largeText != null) {
            rp.setAssetsLargeText(activity.assets.largeText)
        }
        if (activity.assets.smallImage != null) {
            rp.setAssetsSmallImage(activity.assets.smallImage)
        }
        if (activity.assets.smallText != null) {
            rp.setAssetsSmallText(activity.assets.smallText)
        }

        val platform = when (activity.supportedPlatforms) {
            DiscordActivityPlatform.Desktop.bit -> "desktop"
            DiscordActivityPlatform.Xbox.bit -> "xbox"
            DiscordActivityPlatform.Samsung.bit -> "samsung"
            DiscordActivityPlatform.Ios.bit -> "ios"
            DiscordActivityPlatform.Embedded.bit -> "embedded"
            DiscordActivityPlatform.Ps4.bit -> "ps4"
            DiscordActivityPlatform.Ps5.bit -> "ps5"
            else -> "android"
        }
        rp.setPlatform(platform)

        activity.buttons.forEach { button ->
            rp.addButton(button.label, button.url)
        }

        return rp.toJSON()
    }
}
