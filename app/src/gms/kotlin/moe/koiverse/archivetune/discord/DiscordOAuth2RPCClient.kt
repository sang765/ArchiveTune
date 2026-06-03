package moe.koiverse.archivetune.discord

import com.discord.oauth2rpc.GatewayClient
import com.discord.oauth2rpc.GatewayConnectOptions
import com.discord.oauth2rpc.IdentifyPayload
import com.discord.oauth2rpc.structures.RichPresence
import com.discord.oauth2rpc.utils.GatewayOp
import com.discord.oauth2rpc.utils.JsonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import timber.log.Timber

object DiscordOAuth2RPCClient {
    private const val TAG = "DiscordOAuth2RPCClient"

    private val mutex = Mutex()
    private var gateway: GatewayClient? = null
    private var sessionId: String? = null
    private var activeToken: String? = null
    private var ready = false

    val isConnected: Boolean
        get() = ready && gateway != null

    suspend fun connect(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            connectInternal(accessToken)
        }
    }

    suspend fun updatePresence(
        accessToken: String,
        activity: DiscordPresenceActivity,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            Timber.tag(TAG).d("updatePresence: acquiring connection, accessToken=%s...", accessToken.take(8))

            val connectResult = connectInternal(accessToken)
            if (connectResult.isFailure) {
                Timber.tag(TAG).w("updatePresence: connectInternal failed: %s", connectResult.exceptionOrNull()?.message)
                return@withLock connectResult
            }

            val gws = gateway ?: run {
                Timber.tag(TAG).e("updatePresence: gateway is null after successful connect")
                return@withLock Result.failure(IllegalStateException("Gateway not connected"))
            }
            if (!ready) {
                Timber.tag(TAG).w("updatePresence: gateway connected but not ready yet")
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
                Timber.tag(TAG).d("updatePresence: sent PRESENCE_UPDATE opcode=3")
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
                Timber.tag(TAG).d("clearPresence: sent empty activities")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to clear presence")
                Result.failure(e)
            }
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            Timber.tag(TAG).d("disconnect: tearing down")
            disconnectInternal()
            Result.success(Unit)
        }
    }

    private suspend fun connectInternal(accessToken: String): Result<Unit> {
        Timber.tag(TAG).d("connectInternal: entered, alreadyConnected=%s sameToken=%s", isConnected, activeToken == accessToken)

        if (isConnected && activeToken == accessToken) {
            Timber.tag(TAG).d("connectInternal: already connected with same token, skipping")
            return Result.success(Unit)
        }

        Timber.tag(TAG).d("connectInternal: tearing down old connection")
        disconnectInternal()

        val token = accessToken.trim()
        if (token.isBlank()) {
            Timber.tag(TAG).e("connectInternal: token is blank")
            return Result.failure(IllegalArgumentException("Discord access token is missing"))
        }

        activeToken = token
        ready = false

        Timber.tag(TAG).d("connectInternal: creating new GatewayClient")
        val gws = GatewayClient()

        gws.onReady = { ev ->
            sessionId = ev.sessionId
            ready = true
            Timber.tag(TAG).i("Gateway ready: user=%s session=%s", ev.user.username, ev.sessionId)
        }

        gws.onClose = { info ->
            ready = false
            gateway = null
            Timber.tag(TAG).w("Gateway closed: code=%d reason=%s resumable=%s", info.code, info.reason, info.resumable)
        }

        gws.onError = { err ->
            Timber.tag(TAG).e(err, "Gateway error")
        }

        gws.onDebug = { msg ->
            Timber.tag(TAG).d("Gateway: %s", msg)
        }

        gateway = gws

        Timber.tag(TAG).d("connectInternal: calling gws.connect()")
        val result = try {
            gws.connect(GatewayConnectOptions(
                token = token,
                identify = IdentifyPayload(capabilities = 0),
                helloTimeoutMs = 20_000L,
            ))
            Timber.tag(TAG).i("connectInternal: gws.connect() returned successfully (ready=%s)", ready)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "connectInternal: gws.connect() threw")
            gateway?.disconnect()
            gateway = null
            activeToken = null
            sessionId = null
            ready = false
            Result.failure(e)
        }

        return result
    }

    private fun disconnectInternal() {
        Timber.tag(TAG).d("disconnectInternal: gatewayWasNull=%s", gateway == null)
        gateway?.disconnect()
        gateway = null
        sessionId = null
        ready = false
        activeToken = null
        Timber.tag(TAG).d("disconnectInternal: done")
    }

    private fun buildRichPresence(activity: DiscordPresenceActivity): Map<String, Any?> {
        Timber.tag(TAG).v("buildRichPresence: sessionId=%s name=%s details=%s", sessionId, activity.name, activity.details)
        val rp = RichPresence(sessionId)
            .setApplicationId(activity.applicationId.toString())
            .setType(activity.type.nativeValue)
            .setName(activity.name)
            .setDetails(activity.details)
            .setState(activity.state)
            .setURL(activity.detailsUrl)

        if (activity.timestamps.startEpochSeconds != null) {
            Timber.tag(TAG).v("  startTimestamp=%d", activity.timestamps.startEpochSeconds!! * 1000L)
            rp.setStartTimestamp(activity.timestamps.startEpochSeconds!! * 1000L)
        }
        if (activity.timestamps.endEpochSeconds != null) {
            Timber.tag(TAG).v("  endTimestamp=%d", activity.timestamps.endEpochSeconds!! * 1000L)
            rp.setEndTimestamp(activity.timestamps.endEpochSeconds!! * 1000L)
        }

        if (activity.assets.largeImage != null) {
            Timber.tag(TAG).v("  largeImage=%s", activity.assets.largeImage)
            rp.setAssetsLargeImage(activity.assets.largeImage)
        }
        if (activity.assets.largeText != null) {
            rp.setAssetsLargeText(activity.assets.largeText)
        }
        if (activity.assets.smallImage != null) {
            Timber.tag(TAG).v("  smallImage=%s", activity.assets.smallImage)
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
        Timber.tag(TAG).v("  platform=%s buttons=%d", platform, activity.buttons.size)
        rp.setPlatform(platform)

        activity.buttons.forEach { button ->
            rp.addButton(button.label, button.url)
        }

        return rp.toJSON()
    }
}
