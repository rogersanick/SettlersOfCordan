package com.contractsAndStates.states

import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

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
val Wheat = Resource.getInstance("Field")

// Ore.
val Ore = Resource.getInstance("Mountain")

// Sheep.
val Sheep = Resource.getInstance("Pasture")

// Wood.
val Wood = Resource.getInstance("Forest")

// Brick.
val Brick = Resource.getInstance("Hill")

// Underlying Resource Property
@CordaSerializable
data class Resource(private val currency: GameCurrency,
               override val tokenIdentifier: String) : TokenType(currency.currencyCode, 0) {
    val symbol: String get() = currency.currencyCode
    override val tokenClass: Class<*> get() = javaClass
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-currency.defaultFractionDigits)
    override fun toString(): String = symbol

    companion object {
        fun getInstance(resourceType: String) = Resource(GameCurrency(resourceType), resourceType) as TokenType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Resource) return false
        if (currency != other.currency) return false
        return true
    }

    override fun hashCode(): Int {
        return currency.hashCode()
    }

}

// Game Currency Utility Class
@CordaSerializable
data class GameCurrency(val resourceType: String) {
    init {
        if (!listOf("Field", "Mountain", "Pasture", "Forest", "Hill").contains(resourceType)) {
            throw IllegalArgumentException("A game currency must be of type Wheat, Ore, Sheep, Wood or Brick.")
        }
    }

    val currencyCode: String
        get() = when (resourceType) {
            "Field" -> "Wheat"
            "Mountain" -> "Ore"
            "Pasture" -> "Sheep"
            "Forest" -> "Wood"
            "Hill" -> "Brick"
            else -> "NOTCURRENCY"
        }
    val displayName: String
        get() = when (resourceType) {
            "Field" -> "Wh"
            "Mountain" -> "Or"
            "Pasture" -> "Sh"
            "Forest" -> "Wd"
            "Hill" -> "Br"
            else -> "NOTCURRENCY"
        }
    val defaultFractionDigits: Int get() = 0

    override fun equals(other: Any?): Boolean {
        if (other !is GameCurrency) return false
        if (other.currencyCode != this.currencyCode) return false
        if (other.defaultFractionDigits != this.defaultFractionDigits) return false
        if (other.displayName != this.displayName) return false
        if (other.resourceType != this.resourceType) return false
        return true
    }
}
