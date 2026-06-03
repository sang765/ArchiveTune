package com.discord.oauth2rpc.utils

open class BitField(bits: Int = defaultBit) {

    var bitfield: Int = resolve(bits)
        protected set

    open val flags: Map<String, Int> get() = emptyMap()

    fun any(bit: Int): Boolean = (bitfield and resolve(bit)) != defaultBit

    fun equals(bit: Int): Boolean = bitfield == resolve(bit)

    fun has(bit: Int): Boolean {
        val resolved = resolve(bit)
        return (bitfield and resolved) == resolved
    }

    fun add(bit: Int) {
        if (isFrozen) return
        bitfield = bitfield or resolve(bit)
    }

    fun remove(bit: Int) {
        if (isFrozen) return
        bitfield = bitfield and resolve(bit).inv()
    }

    fun freeze(): BitField {
        isFrozen = true
        return this
    }

    fun toArray(): List<String> = flags.filter { (_, bit) -> has(bit) }.keys.toList()

    fun serialize(): Map<String, Boolean> = flags.mapValues { (_, bit) -> has(bit) }

    override fun toString(): String = bitfield.toString()

    fun valueOf(): Int = bitfield

    protected open fun resolve(bit: Int): Int {
        if (bit >= defaultBit) return bit
        return flags[bit.toString()] ?: bit
    }

    private var isFrozen = false

    companion object {
        const val defaultBit: Int = 0

        fun resolve(bit: Int?, defaultBit: Int = this.defaultBit): Int {
            if (bit == null) return defaultBit
            return bit
        }
    }
}
