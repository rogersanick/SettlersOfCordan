package com.contractsAndStates.states

enum class Buildable {
    Road,
    Settlement,
    City,
    DevelopmentCard
}

/**
 * When building game elements in Settlers of Catan, the player must provide input resources. The function
 * below represents the effective rate card shared mutually amongst participants to verify proposed transactions
 * are consuming the appropriate number of resources.
 */
fun getBuildableCosts(buildable: Buildable) = when (buildable) {
    Buildable.Road -> mapOf(Brick to 1L, Wood to 1L)
    Buildable.Settlement -> mapOf(Brick to 1L, Sheep to 1L, Wheat to 1L, Wood to 1L)
    Buildable.City -> mapOf(Ore to 3L, Wheat to 3L)
    Buildable.DevelopmentCard -> mapOf(Ore to 1L, Sheep to 1L, Wheat to 1L)
}