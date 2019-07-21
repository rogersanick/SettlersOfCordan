package com.contractsAndStates.states

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

val Brick = Resource.getInstance("BRICK")
val Ore = Resource.getInstance("ORE")
val Sheep = Resource.getInstance("SHEEP")
val Wheat = Resource.getInstance("WHEAT")
val Wood = Resource.getInstance("WOOD")

val allResources = Resource.allResourceTypes.map { Resource.getInstance(it) }

@CordaSerializable
enum class HexTileType(val resourceYielded: TokenType?) {
    Field(Wheat),
    Mountain(Ore),
    Pasture(Sheep),
    Forest(Wood),
    Hill(Brick),
    Desert(null)
}

fun Iterable<TokenType>.mapOf(amount: Int) = map { amount of it }

// Underlying Resource Property
@CordaSerializable
data class Resource(val type: String) : TokenType(type, FRACTION_DIGITS) {
    override val tokenClass: Class<*> get() = javaClass
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-FRACTION_DIGITS)
    override fun toString(): String = type

    init {
        require(allResourceTypes.contains(type)) { "$type is not a valid type" }
    }

    companion object {
        const val FRACTION_DIGITS = 0
        val allResourceTypes = listOf("BRICK", "ORE", "SHEEP", "WHEAT", "WOOD")
        internal fun getInstance(resourceType: String) = Resource(resourceType) as TokenType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Resource) return false
        if (type != other.type) return false
        return true
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }
}
