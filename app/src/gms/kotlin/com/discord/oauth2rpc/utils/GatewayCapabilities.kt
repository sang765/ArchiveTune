package com.discord.oauth2rpc.utils

class GatewayCapabilities(bits: Int = 0) : BitField(bits) {
    override val flags: Map<String, Int> get() = Companion.FLAGS

    companion object {
        val FLAGS: Map<String, Int> = mapOf(
            "LAZY_USER_NOTES" to (1 shl 0),
            "NO_AFFINE_USER_IDS" to (1 shl 1),
            "VERSIONED_READ_STATES" to (1 shl 2),
            "VERSIONED_USER_GUILD_SETTINGS" to (1 shl 3),
            "DEDUPE_USER_OBJECTS" to (1 shl 4),
            "PRIORITIZED_READY_PAYLOAD" to (1 shl 5),
            "MULTIPLE_GUILD_EXPERIMENT_POPULATIONS" to (1 shl 6),
            "NON_CHANNEL_READ_STATES" to (1 shl 7),
            "AUTH_TOKEN_REFRESH" to (1 shl 8),
            "USER_SETTINGS_PROTO" to (1 shl 9),
            "CLIENT_STATE_V2" to (1 shl 10),
            "PASSIVE_GUILD_UPDATE" to (1 shl 11),
            "AUTO_CALL_CONNECT" to (1 shl 12),
            "DEBOUNCE_MESSAGE_REACTIONS" to (1 shl 13),
            "PASSIVE_GUILD_UPDATE_V2" to (1 shl 14),
            "AUTO_LOBBY_CONNECT" to (1 shl 16),
        )

        val ALL: Int = FLAGS.values.reduce { a, b -> a or b }
    }
}
