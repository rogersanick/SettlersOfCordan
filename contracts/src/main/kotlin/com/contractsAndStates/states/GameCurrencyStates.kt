package com.contractsAndStates.states

import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.money.Money
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import java.lang.IllegalArgumentException
import java.math.BigDecimal


fun getCurrency(resourceType: String, amount: Int) {
    when (resourceType) {
        "Field" -> Wheat(amount)
        "Mountain" -> Ore(amount)
        "Pasture" -> Sheep(amount)
        "Forest" -> Wood(amount)
        "Hill" -> Brick(amount)
    }
}

// Wheat.
val Wheat = Resource.getInstance("Field")
fun Wheat(amount: Int): Amount<Resource> = amount(amount, Wheat)
fun Wheat(amount: Long): Amount<Resource> = amount(amount, Wheat)
fun Wheat(amount: Double): Amount<Resource> = amount(amount, Wheat)
val Int.Wheat: Amount<Resource> get() = Wheat(this)
val Long.Wheat: Amount<Resource> get() = Wheat(this)
val Double.Wheat: Amount<Resource> get() = Wheat(this)

// Ore.
val Ore = Resource.getInstance("Mountain")
fun Ore(amount: Int): Amount<Resource> = amount(amount, Ore)
fun Ore(amount: Long): Amount<Resource> = amount(amount, Ore)
fun Ore(amount: Double): Amount<Resource> = amount(amount, Ore)
val Int.Ore: Amount<Resource> get() = Ore(this)
val Long.Ore: Amount<Resource> get() = Ore(this)
val Double.Ore: Amount<Resource> get() = Ore(this)

// Sheep.
val Sheep = Resource.getInstance("Pasture")
fun Sheep(amount: Int): Amount<Resource> = amount(amount, Sheep)
fun Sheep(amount: Long): Amount<Resource> = amount(amount, Sheep)
fun Sheep(amount: Double): Amount<Resource> = amount(amount, Sheep)
val Int.Sheep: Amount<Resource> get() = Sheep(this)
val Long.Sheep: Amount<Resource> get() = Sheep(this)
val Double.Sheep: Amount<Resource> get() = Sheep(this)

// Wood.
val Wood = Resource.getInstance("Forest")
fun Wood(amount: Int): Amount<Resource> = amount(amount, Wood)
fun Wood(amount: Long): Amount<Resource> = amount(amount, Wood)
fun Wood(amount: Double): Amount<Resource> = amount(amount, Wood)
val Int.Wood: Amount<Resource> get() = Wood(this)
val Long.Wood: Amount<Resource> get() = Wood(this)
val Double.Wood: Amount<Resource> get() = Wood(this)

// Brick.
val Brick = Resource.getInstance("Hill")
fun Brick(amount: Int): Amount<Resource> = amount(amount, Brick)
fun Brick(amount: Long): Amount<Resource> = amount(amount, Brick)
fun Brick(amount: Double): Amount<Resource> = amount(amount, Brick)
val Int.Brick: Amount<Resource> get() = Brick(this)
val Long.Brick: Amount<Resource> get() = Brick(this)
val Double.Brick: Amount<Resource> get() = Brick(this)

// Underlying Resource Property
data class Resource(private val currency: GameCurrency,
               override val tokenIdentifier: String) : Money() {
    val symbol: String get() = currency.currencyCode
    override val description: String get() = currency.displayName
    override val tokenClass: String get() = javaClass.canonicalName
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-currency.defaultFractionDigits)
    override fun toString(): String = symbol

    companion object {
        fun getInstance(resourceType: String) = Resource(GameCurrency(resourceType), resourceType)
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

// -------------------------------------------------------------------------------
// Helpers for creating an amount of a token using some quantity and a token type.
// -------------------------------------------------------------------------------

/** For creating [Int] quantities of [TokenType]s. */
fun <T : TokenType> amount(amount: Int, token: T): Amount<T> = amount(amount.toLong(), token)

/** For creating [Long] quantities of [TokenType]s. */
fun <T : TokenType> amount(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

/** For creating [Double] quantities of [TokenType]s.  */
fun <T : TokenType> amount(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

/** For creating [BigDecimal] quantities of [TokenType]s. */
fun <T : TokenType> amount(amount: BigDecimal, token: T): Amount<T> = Amount.fromDecimal(amount, token)

// ---------------------------------------------------------------------------------------
// Helpers for creating an amount of a token using some quantity and an issued token type.
// ---------------------------------------------------------------------------------------

/** For parsing [Int] quantities of [IssuedTokenType]s. */
fun <T : TokenType> amount(amount: Int, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return amount(amount.toLong(), token)
}

/** For parsing [Long] quantities of [IssuedTokenType]s. */
fun <T : TokenType> amount(amount: Long, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return Amount.fromDecimal(BigDecimal.valueOf(amount), token)
}

/** For parsing [Double] quantities of [IssuedTokenType]s. */
fun <T : TokenType> amount(amount: Double, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return Amount.fromDecimal(BigDecimal.valueOf(amount), token)
}

/** For parsing [BigDecimal] quantities of [IssuedTokenType]s. */
fun <T : TokenType> amount(amount: BigDecimal, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return Amount.fromDecimal(amount, token)
}

// ---------------------------------------------------------------------------------------------
// For creating amounts of token types using a DSL-like infix notation. E.g. "1000 of tokenType"
// ---------------------------------------------------------------------------------------------

/** For creating [Int] quantities of [TokenType]s. */
infix fun <T : TokenType> Int.of(token: T): Amount<T> = amount(this, token)

/** For creating [Long] quantities of [TokenType]s. */
infix fun <T : TokenType> Long.of(token: T): Amount<T> = amount(this, token)

/** For creating [Double] quantities of [TokenType]s. */
infix fun <T : TokenType> Double.of(token: T): Amount<T> = amount(this, token)

/** For creating [BigDecimal] quantities of [TokenType]s. */
infix fun <T : TokenType> BigDecimal.of(token: T): Amount<T> = amount(this, token)

// ----------------------------------------------------------------------------------------------
// For creating amounts of token types using a DSL-like infix notation. "1000 of issuedTokenType"
// ----------------------------------------------------------------------------------------------

/** For creating [Int] quantities of [IssuedTokenType]s. */
infix fun <T : IssuedTokenType<U>, U : TokenType> Int.of(token: T): Amount<IssuedTokenType<U>> = amount(this, token)

/** For creating [Long] quantities of [IssuedTokenType]s. */
infix fun <T : IssuedTokenType<U>, U : TokenType> Long.of(token: T): Amount<IssuedTokenType<U>> = amount(this, token)

/** For creating [Double] quantities of [IssuedTokenType]s. */
infix fun <T : IssuedTokenType<U>, U : TokenType> Double.of(token: T): Amount<IssuedTokenType<U>> = amount(this, token)

/** For creating [BigDecimal] quantities of [IssuedTokenType]s. */
infix fun <T : IssuedTokenType<U>, U : TokenType> BigDecimal.of(token: T): Amount<IssuedTokenType<U>> {
    return amount(this, token)
}

// -------------------------------------------------------------------------------------------------------
// For wrapping amounts of a token with an issuer: Amount<TokenType> -> Amount<IssuedTokenType<TokenType>>.
// -------------------------------------------------------------------------------------------------------

/** Wraps a [TokenType] with an [IssuedTokenType]. E.g. Amount<TokenType> -> Amount<IssuedTokenType<TokenType>>. */
infix fun <T : TokenType> Amount<T>.issuedBy(issuer: Party): Amount<IssuedTokenType<T>> = _issuedBy(issuer)

internal infix fun <T : TokenType> Amount<T>._issuedBy(issuer: Party): Amount<IssuedTokenType<T>> {
    return Amount(quantity, displayTokenSize, uncheckedCast(token.issuedBy(issuer)))
}

/** Wraps a [TokenType] with an [IssuedTokenType]. E.g. TokenType -> IssuedTokenType<TokenType>. */
infix fun <T : TokenType> T.issuedBy(issuer: Party): IssuedTokenType<T> = _issuedBy(issuer)

internal infix fun <T : TokenType> T._issuedBy(issuer: Party): IssuedTokenType<T> = IssuedTokenType(issuer, this)

// ------------------------------------------------------------------
// Helpers for summing amounts of token types and issued token types.
// ------------------------------------------------------------------

/** Sums [Amount]s of [IssuedTokenType]s or returns null if list is empty or there is an [IssuedTokenType] mis-match. */
fun <T : IssuedTokenType<U>, U : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrNull(): Amount<T>? {
    return if (!iterator().hasNext()) null else sumIssuedTokensOrThrow()
}

/**
 * Sums [Amount]s of [IssuedTokenType]s or throws [IllegalArgumentException] if there is an [IssuedTokenType] mis-match.
 */
fun <T : IssuedTokenType<U>, U : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrThrow(): Amount<T> {
    return reduce { left, right -> left + right }
}

/** Sums [Amount]s of [IssuedTokenType]s or returns zero if there is an [IssuedTokenType] mis-match or an empty list. */
fun <T : IssuedTokenType<U>, U : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrZero(token: T): Amount<T> {
    return if (iterator().hasNext()) sumIssuedTokensOrThrow() else Amount.zero(token)
}

/** Sums [Amount]s of [TokenType]s or returns null if list is empty or there is an [TokenType] mis-match. */
fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrNull(): Amount<T>? {
    return if (!iterator().hasNext()) null else sumTokensOrThrow()
}

/** Sums [Amount]s of [TokenType]s or throws [IllegalArgumentException] if there is an [TokenType] mis-match. */
fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrThrow(): Amount<T> {
    return reduce { left, right -> left + right }
}

/** Sums [Amount]s of [TokenType]s or returns zero if there is an [TokenType] mis-match or an empty list. */
fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrZero(token: T): Amount<T> {
    return if (iterator().hasNext()) sumTokensOrThrow() else Amount.zero(token)
}

/**
 * Strips the wrapping [IssuedTokenType] from an [Amount] of [TokenType] and returns only the [Amount] of [TokenType].
 * This is useful when you are mixing code that cares about specific issuers with code that will accept any, or which is
 * imposing issuer constraints via some other mechanism and the additional type safety is not wanted.
 */
fun <T : TokenType> Amount<IssuedTokenType<T>>.withoutIssuer(): Amount<T> {
    return Amount(quantity, displayTokenSize, token.tokenType)
}