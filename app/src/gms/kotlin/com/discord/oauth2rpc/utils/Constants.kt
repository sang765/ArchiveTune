package com.discord.oauth2rpc.utils

object GatewayOp {
    const val DISPATCH = 0
    const val HEARTBEAT = 1
    const val IDENTIFY = 2
    const val PRESENCE_UPDATE = 3
    const val VOICE_STATE_UPDATE = 4
    const val RESUME = 6
    const val RECONNECT = 7
    const val REQUEST_GUILD_MEMBERS = 8
    const val INVALID_SESSION = 9
    const val HELLO = 10
    const val HEARTBEAT_ACK = 11
}

val NON_RESUMABLE_CLOSE_CODES: Set<Int> = setOf(
    4004, 4010, 4011, 4012, 4013, 4014
)

object DEFAULTS {
    const val API_BASE = "https://discord.com/api"
    const val API_SDK_BASE = "https://gaming-sdk.com/api"
    const val API_VERSION = 9
    const val GATEWAY_URL = "wss://gateway.discord.gg"
    const val GATEWAY_VERSION = 9
    const val USER_AGENT = "Discord Embedded/1.9.15780"
    const val REQUEST_TIMEOUT_MS = 15_000L
    const val HELLO_TIMEOUT_MS = 20_000L
    const val CLOSE_TIMEOUT_MS = 5_000L
}

object DEFAULT_SUPER_PROPERTIES : HashMap<String, Any>() {
    init {
        put("browser", "Discord Embedded")
        put("browser_user_agent", "Discord Embedded/1.9.15780")
        put("browser_version", "1.9.15780")
        put("client_build_number", 15780)
        put("client_version", "1.9.15780")
        put("design_id", 0)
        put("device", "console")
        put("native_build_number", 15780)
        put("os", "Android")
        put("release_channel", "unknown")
    }
}

object ActivityTypes {
    private val keys = listOf("PLAYING", "STREAMING", "LISTENING", "WATCHING", "CUSTOM", "COMPETING", "HANG")
    private val forward = mutableMapOf<String, Int>()
    private val reverse = mutableMapOf<Int, String>()

    init {
        for ((index, key) in keys.withIndex()) {
            if (key.isNotEmpty()) {
                forward[key] = index
                reverse[index] = key
            }
        }
    }

    fun fromString(name: String): Int? = forward[name]
    fun fromInt(value: Int): String? = reverse[value]
    fun values(): Map<String, Int> = forward.toMap()
}
