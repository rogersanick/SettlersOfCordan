package com.contractsAndStates.states

import net.corda.core.contracts.Amount
import net.corda.core.serialization.CordaSerializable
import net.corda.sdk.token.contracts.utilities.AMOUNT
import net.corda.sdk.token.money.Money
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.util.*


// Wheat.
val Wheat = Resource.getInstance("Wheat")
fun Wheat(amount: Int): Amount<Resource> = AMOUNT(amount, Wheat)
fun Wheat(amount: Long): Amount<Resource> = AMOUNT(amount, Wheat)
fun Wheat(amount: Double): Amount<Resource> = AMOUNT(amount, Wheat)
val Int.Wheat: Amount<Resource> get() = Wheat(this)
val Long.Wheat: Amount<Resource> get() = Wheat(this)
val Double.Wheat: Amount<Resource> get() = Wheat(this)

// Ore.
val Ore = Resource.getInstance("Ore")
fun Ore(amount: Int): Amount<Resource> = AMOUNT(amount, Ore)
fun Ore(amount: Long): Amount<Resource> = AMOUNT(amount, Ore)
fun Ore(amount: Double): Amount<Resource> = AMOUNT(amount, Ore)
val Int.Ore: Amount<Resource> get() = Ore(this)
val Long.Ore: Amount<Resource> get() = Ore(this)
val Double.Ore: Amount<Resource> get() = Ore(this)

// Sheep.
val Sheep = Resource.getInstance("Sheep")
fun Sheep(amount: Int): Amount<Resource> = AMOUNT(amount, Sheep)
fun Sheep(amount: Long): Amount<Resource> = AMOUNT(amount, Sheep)
fun Sheep(amount: Double): Amount<Resource> = AMOUNT(amount, Sheep)
val Int.Sheep: Amount<Resource> get() = Sheep(this)
val Long.Sheep: Amount<Resource> get() = Sheep(this)
val Double.Sheep: Amount<Resource> get() = Sheep(this)

// Wood.
val Wood = Resource.getInstance("Wood")
fun Wood(amount: Int): Amount<Resource> = AMOUNT(amount, Wood)
fun Wood(amount: Long): Amount<Resource> = AMOUNT(amount, Wood)
fun Wood(amount: Double): Amount<Resource> = AMOUNT(amount, Wood)
val Int.Wood: Amount<Resource> get() = Wood(this)
val Long.Wood: Amount<Resource> get() = Wood(this)
val Double.Wood: Amount<Resource> get() = Wood(this)

// Brick.
val Brick = Resource.getInstance("Brick")
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
        // Uses the java money registry.
        fun getInstance(currencyCode: String) = Resource(GameCurrency(currencyCode))
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
class GameCurrency(val currencyCode: String) {
    val displayName: String get() = when (currencyCode) {
        "Wheat" -> "Wh"
        "Ore" -> "Or"
        "Sheep" -> "Sh"
        "Wood" -> "Wd"
        "Brick" -> "Br"
        else -> throw IllegalArgumentException("A game currency must be of type Wheat, Ore, Sheep, Wood or Brick.")
    }
    val defaultFractionDigits: Int get() = 0
}