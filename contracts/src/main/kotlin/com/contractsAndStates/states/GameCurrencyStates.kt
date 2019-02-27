package com.contractsAndStates.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.FungibleState
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
open class GameCurrency(override val amount: Amount<Any>, val owner: Party): FungibleState<Any> {
    override val participants: List<Party> get() = listOf(owner)
}

interface SpecificGameCurrency {
    val currencyType: String
}

class Wheat(amount: Amount<Any>, owner: Party): GameCurrency(amount, owner), SpecificGameCurrency {
    override val currencyType: String get() = "Wheat"
}

class Ore(amount: Amount<Any>, owner: Party): GameCurrency(amount, owner), SpecificGameCurrency {
    override val currencyType: String get() = "Ore"
}

class Brick(amount: Amount<Any>, owner: Party): GameCurrency(amount, owner), SpecificGameCurrency {
    override val currencyType: String get() = "Brick"
}

class Sheep(amount: Amount<Any>, owner: Party): GameCurrency(amount, owner), SpecificGameCurrency {
    override val currencyType: String get() = "Sheep"
}

class Wood(amount: Amount<Any>, owner: Party): GameCurrency(amount, owner), SpecificGameCurrency {
    override val currencyType: String get() = "Wood"
}