package com.contractsAndStates.states

import net.corda.core.contracts.Amount
import net.corda.core.serialization.CordaSerializable
import net.corda.sdk.token.contracts.utilities.AMOUNT
import net.corda.sdk.token.money.Money
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.util.*


fun getCurrency(resourceType: String, amount: Int) {
    when (resourceType) {
        "Field" -> Wheat(amount)
        "Mountain" -> Ore(amount)
        "Pasture" -> Sheep(amount)
        "Forest" -> Wood(amount)
        "Hill" -> Brick(amount)
        else -> "NOTCURRENCY"
    }
}

// Wheat.
val Wheat = Resource.getInstance("Field")
fun Wheat(amount: Int): Amount<Resource> = AMOUNT(amount, Wheat)
fun Wheat(amount: Long): Amount<Resource> = AMOUNT(amount, Wheat)
fun Wheat(amount: Double): Amount<Resource> = AMOUNT(amount, Wheat)
val Int.Wheat: Amount<Resource> get() = Wheat(this)
val Long.Wheat: Amount<Resource> get() = Wheat(this)
val Double.Wheat: Amount<Resource> get() = Wheat(this)

// Ore.
val Ore = Resource.getInstance("Mountain")
fun Ore(amount: Int): Amount<Resource> = AMOUNT(amount, Ore)
fun Ore(amount: Long): Amount<Resource> = AMOUNT(amount, Ore)
fun Ore(amount: Double): Amount<Resource> = AMOUNT(amount, Ore)
val Int.Ore: Amount<Resource> get() = Ore(this)
val Long.Ore: Amount<Resource> get() = Ore(this)
val Double.Ore: Amount<Resource> get() = Ore(this)

// Sheep.
val Sheep = Resource.getInstance("Pasture")
fun Sheep(amount: Int): Amount<Resource> = AMOUNT(amount, Sheep)
fun Sheep(amount: Long): Amount<Resource> = AMOUNT(amount, Sheep)
fun Sheep(amount: Double): Amount<Resource> = AMOUNT(amount, Sheep)
val Int.Sheep: Amount<Resource> get() = Sheep(this)
val Long.Sheep: Amount<Resource> get() = Sheep(this)
val Double.Sheep: Amount<Resource> get() = Sheep(this)

// Wood.
val Wood = Resource.getInstance("Forest")
fun Wood(amount: Int): Amount<Resource> = AMOUNT(amount, Wood)
fun Wood(amount: Long): Amount<Resource> = AMOUNT(amount, Wood)
fun Wood(amount: Double): Amount<Resource> = AMOUNT(amount, Wood)
val Int.Wood: Amount<Resource> get() = Wood(this)
val Long.Wood: Amount<Resource> get() = Wood(this)
val Double.Wood: Amount<Resource> get() = Wood(this)

// Brick.
val Brick = Resource.getInstance("Hill")
fun Brick(amount: Int): Amount<Resource> = AMOUNT(amount, Brick)
fun Brick(amount: Long): Amount<Resource> = AMOUNT(amount, Brick)
fun Brick(amount: Double): Amount<Resource> = AMOUNT(amount, Brick)
val Int.Brick: Amount<Resource> get() = Brick(this)
val Long.Brick: Amount<Resource> get() = Brick(this)
val Double.Brick: Amount<Resource> get() = Brick(this)

// Underlying Resource Property
class Resource(private val currency: GameCurrency) : Money() {
    override val symbol: String get() = currency.currencyCode
    override val description: String get() = currency.displayName
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-currency.defaultFractionDigits)
    override fun toString(): String = symbol

    companion object {
        fun getInstance(resourceType: String) = Resource(GameCurrency(resourceType))
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
class GameCurrency(val resourceType: String) {
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