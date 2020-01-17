package com.r3.cordan.primary.states.board

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PlacedSettlements @ConstructorForDeserialization constructor(
        val value: MutableList<UniqueIdentifier?> = MutableList(HexTile.SIDE_COUNT) { null }
) : SettlementLocator {

    init {
        require(value.size == HexTile.SIDE_COUNT) {
            "value.size cannot be ${value.size}"
        }
        require(value.filterNotNull().let { it.size == it.distinct().size }) {
            "There should be no non-null duplicates"
        }
    }

    override fun getSettlementOn(corner: TileCornerIndex) = value[corner.value]

    fun toBuilder() = Builder(value.toMutableList())

    class Builder(private val value: MutableList<UniqueIdentifier?> =
                          MutableList(HexTile.SIDE_COUNT) { null }
    ) : SettlementLocator,
            SettlementBuilder {

        init {
            require(value.size == HexTile.SIDE_COUNT) {
                "value.size cannot be ${value.size}"
            }
        }

        override fun getSettlementOn(corner: TileCornerIndex) = value[corner.value]
        override fun setSettlementOn(corner: TileCornerIndex, settlementId: UniqueIdentifier): Builder = apply {
            require(getSettlementOn(corner).let { it == null || it == settlementId }) {
                "You cannot set a settlement twice"
            }
            value[corner.value] = settlementId
        }

        fun getAllSettlements(): List<UniqueIdentifier?> = ImmutableList(value)
        fun build() = PlacedSettlements(getAllSettlements().toMutableList())
    }
}

interface SettlementLocator {
    fun getSettlementOn(corner: TileCornerIndex): UniqueIdentifier?
    fun hasSettlementOn(corner: TileCornerIndex): Boolean = getSettlementOn(corner) != null
}

interface SettlementBuilder {
    fun setSettlementOn(corner: TileCornerIndex, settlementId: UniqueIdentifier): SettlementBuilder
}