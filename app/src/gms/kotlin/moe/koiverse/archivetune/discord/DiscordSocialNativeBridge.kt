package moe.koiverse.archivetune.discord

import com.discord.sdk.DiscordClient
import com.discord.sdk.DiscordConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.*
import timber.log.Timber

object DiscordSocialNativeBridge {
    private const val TAG = "DiscordSocialNativeBridge"

    val isAvailable: Boolean get() = true

    private var client: DiscordClient? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(applicationId: Long, accessToken: String): Result<Unit> = runCatching {
        val config = DiscordConfig(
            clientId = applicationId.toString(),
            token = accessToken,
            isBot = false,
            intents = emptySet()
        )
        val newClient = DiscordClient(config, scope)
        newClient.connectGateway()
        client = newClient
        Timber.tag(TAG).d("Gateway connected for app %d", applicationId)
    }

    fun updatePresence(
        applicationId: Long,
        accessToken: String,
        activity: DiscordPresenceActivity,
    ): Result<Unit> = runCatching {
        val c = client ?: return@runCatching

        val buttons = activity.buttons.take(2)

        val activityJson = buildJsonObject {
            put("name", JsonPrimitive(activity.name ?: "ArchiveTune"))
            put("type", JsonPrimitive(activity.type.nativeValue))
            activity.details?.let { put("details", JsonPrimitive(it)) }
            activity.state?.let { put("state", JsonPrimitive(it)) }
            if (activity.timestamps.startEpochSeconds != null || activity.timestamps.endEpochSeconds != null) {
                put("timestamps", buildJsonObject {
                    activity.timestamps.startEpochSeconds?.let { put("start", JsonPrimitive(it)) }
                    activity.timestamps.endEpochSeconds?.let { put("end", JsonPrimitive(it)) }
                })
            }
            val assets = buildJsonObject {
                activity.assets.largeImage?.let { put("large_image", JsonPrimitive(it)) }
                activity.assets.largeText?.let { put("large_text", JsonPrimitive(it)) }
                activity.assets.smallImage?.let { put("small_image", JsonPrimitive(it)) }
                activity.assets.smallText?.let { put("small_text", JsonPrimitive(it)) }
            }
            if (assets.isNotEmpty()) {
                put("assets", assets)
            }
            if (buttons.isNotEmpty()) {
                put("buttons", buildJsonArray {
                    buttons.forEach { b ->
                        add(buildJsonObject {
                            put("label", JsonPrimitive(b.label))
                            put("url", JsonPrimitive(b.url))
                        })
                    }
                })
            }
            put("instance", JsonPrimitive(false))
        }

        val status = when (activity.onlineStatus) {
            DiscordOnlineStatus.Idle -> "idle"
            DiscordOnlineStatus.Dnd -> "dnd"
            DiscordOnlineStatus.Invisible -> "invisible"
            else -> "online"
        }

        c.sendRichPresence(activityJson, status)
        Timber.tag(TAG).d("Presence sent via Gateway")
    }

    fun clearPresence(): Result<Unit> = runCatching {
        client?.clearPresence()
    }

    fun close(): Result<Unit> = runCatching {
        client?.clearPresence()
        client?.disconnectGateway()
        client?.close()
        client = null
    }

    fun runCallbacks(): Result<Unit> = Result.success(Unit)
}
