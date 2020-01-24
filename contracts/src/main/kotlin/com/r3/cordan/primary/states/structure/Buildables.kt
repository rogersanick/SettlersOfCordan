package com.r3.cordan.primary.states.structure

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.states.resources.*

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
@Suspendable
fun getBuildableCosts(buildable: Buildable, multiple: Int = 1) = when (buildable) {
    Buildable.Road -> mapOf(Brick to 1L * multiple, Wood to 1L * multiple)
    Buildable.Settlement -> mapOf(Brick to 1L * multiple, Sheep to 1L * multiple, Wheat to 1L * multiple, Wood to 1L * multiple)
    Buildable.City -> mapOf(Ore to 3L * multiple, Wheat to 2L * multiple)
    Buildable.DevelopmentCard -> mapOf(Ore to 1L * multiple, Sheep to 1L * multiple, Wheat to 1L * multiple)
}