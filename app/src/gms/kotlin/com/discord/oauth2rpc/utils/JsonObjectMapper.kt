package com.discord.oauth2rpc.utils

object JsonObjectMapper {
    fun mapToJson(map: Map<String, Any?>): String {
        val sb = StringBuilder()
        serialize(sb, map)
        return sb.toString()
    }

    private fun serialize(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> {
                sb.append('"')
                value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .also { sb.append(it) }
                sb.append('"')
            }
            is Number -> sb.append(value)
            is Boolean -> sb.append(value)
            is Map<*, *> -> {
                sb.append('{')
                val entries = value.entries.toList()
                for ((i, entry) in entries.withIndex()) {
                    if (i > 0) sb.append(',')
                    serialize(sb, entry.key.toString())
                    sb.append(':')
                    serialize(sb, entry.value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                val list = value.toList()
                for ((i, item) in list.withIndex()) {
                    if (i > 0) sb.append(',')
                    serialize(sb, item)
                }
                sb.append(']')
            }
            else -> sb.append(value)
        }
    }
}
