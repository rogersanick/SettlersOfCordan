package com.contractsAndStates.states

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
open class GameCurrency

class Wheat: GameCurrency()
class Ore: GameCurrency()
class Brick: GameCurrency()
class Sheep: GameCurrency()
class Wood: GameCurrency()