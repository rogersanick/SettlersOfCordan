package com.contractsAndStates.states

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

enum class ResourceType(val displayName: String) {
    Wheat("Wh"),
    Ore("Or"),
    Sheep("Sh"),
    Wood("Wd"),
    Brick("Br")
}

// TODO find a way to make resourceYielded not nullable
enum class HexTileType(val resourceYielded: ResourceType?) {
    Field(ResourceType.Wheat),
    Mountain(ResourceType.Ore),
    Pasture(ResourceType.Sheep),
    Forest(ResourceType.Wood),
    Hill(ResourceType.Brick),
    Desert(null)
}

fun getTokenTypeByName(resourceType: String): TokenType {
    return when (resourceType) {
        "Wheat" -> Wheat
        "Ore" -> Ore
        "Sheep" -> Sheep
        "Wood" -> Wood
        "Brick" -> Brick
        else -> throw IllegalArgumentException("There is no currency of that type.")
    }
}

// Wheat.
val Wheat = Resource.getInstance(ResourceType.Wheat)

// Ore.
val Ore = Resource.getInstance(ResourceType.Ore)

// Sheep.
val Sheep = Resource.getInstance(ResourceType.Sheep)

// Wood.
val Wood = Resource.getInstance(ResourceType.Wood)

// Brick.
val Brick = Resource.getInstance(ResourceType.Brick)

// Underlying Resource Property
@CordaSerializable
data class Resource(private val type: ResourceType) : TokenType(type.displayName, FRACTION_DIGITS) {
    override val tokenClass: Class<*> get() = javaClass
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-FRACTION_DIGITS)
    override fun toString(): String = type.name

    companion object {
        val FRACTION_DIGITS = 0
        fun getInstance(resourceType: ResourceType) = Resource(resourceType) as TokenType
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
