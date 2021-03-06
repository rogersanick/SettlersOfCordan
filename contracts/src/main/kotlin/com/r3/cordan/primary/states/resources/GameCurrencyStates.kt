package com.r3.cordan.primary.states.resources

import com.r3.cordan.primary.contracts.resources.GameCurrencyContract
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

@CordaSerializable
@BelongsToContract(GameCurrencyContract::class)
data class GameCurrencyState(val fungibleToken: FungibleToken,
                             val gameBoardId: UniqueIdentifier): FungibleToken(fungibleToken.amount, fungibleToken.holder), ContractState

val Int.BRICK: Amount<TokenType> get() = Amount(this.toLong(), Brick)
val Long.BRICK: Amount<TokenType> get() = Amount(this, Brick)
val Double.BRICK: Amount<TokenType> get() = Amount(this.toLong(), Brick)
val Int.ORE: Amount<TokenType> get() = Amount(this.toLong(), Ore)
val Long.ORE: Amount<TokenType> get() = Amount(this.toLong(), Ore)
val Double.ORE: Amount<TokenType> get() = Amount(this.toLong(), Ore)
val Int.SHEEP: Amount<TokenType> get() = Amount(this.toLong(), Sheep)
val Long.SHEEP: Amount<TokenType> get() = Amount(this.toLong(), Sheep)
val Double.SHEEP: Amount<TokenType> get() = Amount(this.toLong(), Sheep)
val Int.WHEAT: Amount<TokenType> get() = Amount(this.toLong(), Wheat)
val Long.WHEAT: Amount<TokenType> get() = Amount(this.toLong(), Wheat)
val Double.WHEAT: Amount<TokenType> get() = Amount(this.toLong(), Wheat)
val Int.WOOD: Amount<TokenType> get() = Amount(this.toLong(), Wood)
val Long.WOOD: Amount<TokenType> get() = Amount(this.toLong(), Wood)
val Double.WOOD: Amount<TokenType> get() = Amount(this.toLong(), Wood)


infix fun FungibleToken.forGameBoard(gameBoardId: UniqueIdentifier): GameCurrencyState = GameCurrencyState(this, gameBoardId)

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
