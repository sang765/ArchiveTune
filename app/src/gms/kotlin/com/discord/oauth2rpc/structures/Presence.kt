package com.discord.oauth2rpc.structures

import com.discord.oauth2rpc.utils.*
import java.net.URI
import java.util.*

open class RichPresence(sessionId: String? = null) {

    var name: String? = null
    var type: Int = 0
    var url: String? = null
    var applicationId: String? = null
    var state: String? = null
    var details: String? = null
    var party: MutableMap<String, Any?>? = null
    var timestamps: MutableMap<String, Long?>? = null
    var buttons: MutableList<String> = mutableListOf()
    var platform: String? = null
    var secrets: MutableMap<String, Any?> = mutableMapOf()
    var metadata: MutableMap<String, Any?> = mutableMapOf()
    var createdTimestamp: Long = System.currentTimeMillis()
    var assets: MutableMap<String, String?> = mutableMapOf("largeImage" to null, "largeText" to null, "smallImage" to null, "smallText" to null)
    var flags: Int? = null
    var syncId: String? = null
    var id: String? = null
    var sessionId: String? = sessionId

    fun setApplicationId(id: String?): RichPresence { applicationId = id; return this }
    fun setType(type: Any): RichPresence {
        this.type = when (type) { is Int -> type; is String -> ActivityTypes.fromString(type) ?: 0; else -> 0 }
        return this
    }
    fun setURL(url: String?): RichPresence {
        if (url != null) { try { URI(url) } catch (_: Exception) { throw IllegalArgumentException("URL must be a valid URL") } }
        this.url = url; return this
    }
    fun setState(state: String?): RichPresence { this.state = state; return this }
    fun setName(name: String?): RichPresence { this.name = name; return this }
    fun setDetails(details: String?): RichPresence { this.details = details; return this }

    fun setParty(party: Map<String, Any?>?): RichPresence {
        if (party != null) {
            val maxVal = (party["max"] as? Number)?.toInt() ?: throw IllegalArgumentException("Party must have max number")
            val currentVal = (party["current"] as? Number)?.toInt() ?: throw IllegalArgumentException("Party must have current number")
            if (currentVal > maxVal) throw IllegalArgumentException("Party current must be less than max")
            this.party = mutableMapOf("size" to listOf(currentVal, maxVal), "id" to (party["id"] as? String ?: UUID.randomUUID().toString()))
        } else this.party = null
        return this
    }

    fun setStartTimestamp(timestamp: Any?): RichPresence {
        if (timestamps == null) timestamps = mutableMapOf("start" to null, "end" to null)
        timestamps!!["start"] = when (timestamp) { is Long -> timestamp; is Int -> timestamp.toLong(); is java.util.Date -> timestamp.time; else -> null }
        return this
    }

    fun setEndTimestamp(timestamp: Any?): RichPresence {
        if (timestamps == null) timestamps = mutableMapOf("start" to null, "end" to null)
        timestamps!!["end"] = when (timestamp) { is Long -> timestamp; is Int -> timestamp.toLong(); is java.util.Date -> timestamp.time; else -> null }
        return this
    }

    fun setAssetsLargeImage(image: String?): RichPresence { assets["largeImage"] = image; return this }
    fun setAssetsLargeText(text: String?): RichPresence { assets["largeText"] = text; return this }
    fun setAssetsSmallImage(image: String?): RichPresence { assets["smallImage"] = image; return this }
    fun setAssetsSmallText(text: String?): RichPresence { assets["smallText"] = text; return this }
    fun setPlatform(platform: String?): RichPresence { this.platform = platform; return this }

    fun addButton(name: String, url: String): RichPresence {
        require(name.isNotEmpty()) { "Button must have name" }; require(url.isNotEmpty()) { "Button must have url" }
        try { URI(url) } catch (_: Exception) { throw IllegalArgumentException("Button url must be a valid url") }
        buttons.add(name)
        @Suppress("UNCHECKED_CAST")
        (metadata.getOrPut("button_urls") { mutableListOf<String>() } as MutableList<String>).add(url)
        return this
    }

    fun setButtons(vararg button: Map<String, String>): RichPresence {
        if (button.isEmpty()) { buttons.clear(); metadata.remove("button_urls"); return this }
        if (button.size > 2) throw IllegalArgumentException("RichPresence can only have up to 2 buttons")
        buttons.clear(); metadata["button_urls"] = mutableListOf<String>()
        for (b in button) {
            val n = b["name"] ?: throw IllegalArgumentException("Button must have name")
            val u = b["url"] ?: throw IllegalArgumentException("Button must have url")
            try { URI(u) } catch (_: Exception) { throw IllegalArgumentException("Button url must be a valid url") }
            buttons.add(n)
            @Suppress("UNCHECKED_CAST")
            (metadata["button_urls"] as MutableList<String>).add(u)
        }
        return this
    }

    fun setJoinSecret(join: String?): RichPresence { secrets["join"] = join; return this }

    open fun toJSON(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "name" to name, "type" to type, "url" to url, "state" to state, "details" to details,
            "application_id" to applicationId,
            "timestamps" to timestamps?.let { mapOf("start" to it["start"], "end" to it["end"]) },
            "party" to party,
            "secrets" to if (secrets.isNotEmpty()) secrets.toMap() else null,
            "buttons" to if (buttons.isNotEmpty()) buttons.toList() else null,
            "metadata" to if (metadata.isNotEmpty()) metadata.toMap() else null,
            "platform" to platform, "created_at" to createdTimestamp, "session_id" to sessionId
        )
        if (flags != null) result["flags"] = flags
        if (syncId != null) result["sync_id"] = syncId
        if (id != null) result["id"] = id

        val hasAssets = assets["largeImage"] != null || assets["largeText"] != null || assets["smallImage"] != null || assets["smallText"] != null
        if (hasAssets) {
            result["assets"] = mapOf(
                "large_image" to parseImage(assets["largeImage"]),
                "large_text" to assets["largeText"],
                "small_image" to parseImage(assets["smallImage"]),
                "small_text" to assets["smallText"]
            )
        }
        return clearNullOrUndefinedObject(result) ?: emptyMap()
    }

    private fun parseImage(image: String?): String? {
        if (image == null) return null
        if (Regex("^[0-9]{17,19}$").matches(image)) return image
        if (listOf("mp:", "youtube:", "spotify:", "twitch:").any { image.startsWith(it) }) return image
        if (image.startsWith("external/")) return "mp:$image"
        return image
    }

    private fun clearNullOrUndefinedObject(obj: Map<String, Any?>): Map<String, Any?>? {
        val data = mutableMapOf<String, Any?>()
        for ((key, value) in obj) {
            when {
                value == null -> continue
                value is List<*> && value.isEmpty() -> continue
                value is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val cleaned = clearNullOrUndefinedObject(value as Map<String, Any?>)
                    if (cleaned != null) data[key] = cleaned
                }
                else -> data[key] = value
            }
        }
        return if (data.isNotEmpty()) data else null
    }
}

class CustomStatus {
    var name: String = " "
    var type: Int = ActivityTypes.fromString("CUSTOM") ?: 4
    var state: String? = null
    var emoji: Any? = null

    fun setState(state: String?): CustomStatus {
        if (state != null && state.length > 128) throw IllegalArgumentException("State must be less than 128 characters")
        this.state = state; return this
    }
    fun toJSON(): Map<String, Any?> {
        return mapOf("name" to name, "type" to type, "state" to state, "emoji" to emoji)
    }
}
