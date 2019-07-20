package com.contractsAndStates.states

import com.oracleClientStatesAndContracts.states.RollTrigger
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class HexTile(
        val resourceType: HexTileType,
        val rollTrigger: RollTrigger?,
        val robberPresent: Boolean,
        val hexTileIndex: HexTileIndex,
        val sides: TileSides = TileSides()
) : TileSideLocator by sides, RoadLocator by sides, NeighborLocator by sides {

    companion object {
        const val SIDE_COUNT = 6
    }

    fun toBuilder() = Builder(this)

    class Builder(
            val hexTileIndex: HexTileIndex,
            resourceType: HexTileType? = null,
            rollTrigger: RollTrigger? = null,
            robberPresent: Boolean? = null,
            val sidesBuilder: TileSides.Builder = TileSides.Builder()
    ): TileSideLocator by sidesBuilder, TileSideBuilder by sidesBuilder, RoadLocator by sidesBuilder,
            RoadBuilder by sidesBuilder, NeighborLocator by sidesBuilder, NeighborBuilder by sidesBuilder {

        constructor(tile: HexTile) : this(
                tile.hexTileIndex,
                tile.resourceType,
                tile.rollTrigger,
                tile.robberPresent,
                TileSides.Builder(tile.sides))

        var resourceType: HexTileType?
            private set
        var rollTrigger: RollTrigger?
            private set
        var robberPresent: Boolean?
            private set

        init {
            this.resourceType = resourceType
            this.rollTrigger = rollTrigger
            this.robberPresent = robberPresent
        }

        fun with(resourceType: HexTileType) = apply {
            require(this.resourceType.let { it == null || it == resourceType }) {
                "You cannot replace an existing resource type"
            }
            this.resourceType = resourceType
        }

        fun with(rollTrigger: RollTrigger?) = apply {
            require(this.rollTrigger.let { it == null || it == rollTrigger }) {
                "You cannot replace an existing rollTrigger"
            }
            this.rollTrigger = rollTrigger
        }

        fun with(robberPresent: Boolean) = apply {
            require(this.robberPresent.let { it == null || it == robberPresent }) {
                "You cannot replace an existing robberPresent"
            }
            this.robberPresent = robberPresent
        }

        /**
         * This method is used to create a fully connected graph of HexTiles. This enables some
         * funky maths that we will use later on to calculate the validity of transactions.
         */
        fun connect(mySideIndex: TileSideIndex, hexTileToConnect: Builder): Builder = apply {
            val myTileIndex = hexTileIndex
            setNeighborOn(mySideIndex, hexTileToConnect.hexTileIndex)
            if (!hexTileToConnect.isNeighborOn(mySideIndex.opposite(), myTileIndex))
                hexTileToConnect.connect(mySideIndex.opposite(), this)
        }

        fun build() = HexTile(
                resourceType!!,
                rollTrigger,
                robberPresent!!,
                hexTileIndex,
                sidesBuilder.build()
        )
    }
}
