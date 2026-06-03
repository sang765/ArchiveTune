package com.discord.oauth2rpc

data class SessionState(
    val sessionId: String,
    val seq: Int,
    val resumeGatewayUrl: String
)

data class IdentifyPayload(
    val capabilities: Int? = null,
    val intents: Int? = null,
    val properties: Map<String, Any>? = null,
    val extra: Map<String, Any> = emptyMap()
)

data class GatewayConnectOptions(
    val token: String,
    val identify: IdentifyPayload? = null,
    val session: SessionState? = null,
    val gatewayUrl: String? = null,
    val version: Int? = null,
    val helloTimeoutMs: Long? = null,
)

data class GatewayPacket(
    val op: Int,
    val d: Any?,
    val s: Int?,
    val t: String?
)

data class GatewayCloseInfo(
    val code: Int,
    val reason: String,
    val resumable: Boolean,
    val session: SessionState?
)

data class SessionUpdateEvent(
    val sessionId: String?,
    val seq: Int,
    val resumeGatewayUrl: String?
)

data class ReadyEvent(
    val user: ReadyUser,
    val sessionId: String,
    val resumeGatewayUrl: String
)

data class ReadyUser(
    val id: String,
    val username: String,
    val globalName: String? = null
)
