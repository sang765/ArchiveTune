package com.discord.oauth2rpc.utils

class Intents(bits: Int = 0) : BitField(bits) {
    override val flags: Map<String, Int> get() = Companion.FLAGS

    companion object {
        val FLAGS: Map<String, Int> = mapOf(
            "GUILDS" to (1 shl 0),
            "GUILD_MEMBERS" to (1 shl 1),
            "GUILD_BANS" to (1 shl 2),
            "GUILD_EMOJIS_AND_STICKERS" to (1 shl 3),
            "GUILD_INTEGRATIONS" to (1 shl 4),
            "GUILD_WEBHOOKS" to (1 shl 5),
            "GUILD_INVITES" to (1 shl 6),
            "GUILD_VOICE_STATES" to (1 shl 7),
            "GUILD_PRESENCES" to (1 shl 8),
            "GUILD_MESSAGES" to (1 shl 9),
            "GUILD_MESSAGE_REACTIONS" to (1 shl 10),
            "GUILD_MESSAGE_TYPING" to (1 shl 11),
            "DIRECT_MESSAGES" to (1 shl 12),
            "DIRECT_MESSAGE_REACTIONS" to (1 shl 13),
            "DIRECT_MESSAGE_TYPING" to (1 shl 14),
            "MESSAGE_CONTENT" to (1 shl 15),
            "GUILD_SCHEDULED_EVENTS" to (1 shl 16),
            "PRIVATE_CHANNELS" to (1 shl 18),
            "CALLS" to (1 shl 19),
            "USER_RELATIONSHIPS" to (1 shl 22),
            "USER_PRESENCE" to (1 shl 23),
            "LOBBIES" to (1 shl 27),
            "LOBBY_DELETE" to (1 shl 28),
            "UNKNOWN_29" to (1 shl 29),
        )

        val ALL: Int = FLAGS.values.reduce { a, b -> a or b }
    }
}
