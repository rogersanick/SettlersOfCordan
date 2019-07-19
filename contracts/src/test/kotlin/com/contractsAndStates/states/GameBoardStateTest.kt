package com.contractsAndStates.states

import com.oracleClientStatesAndContracts.states.RollTrigger
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class GameBoardStateTest {

    private fun getAllTileBuilders(): List<HexTile.Builder> {
        var tileIndex = 0
        return PlacedHexTiles.TILE_COUNT_PER_RESOURCE.flatMap { entry ->
            (0 until entry.value).map {
                HexTile.Builder(HexTileIndex(tileIndex).also { tileIndex++ })
                        .with(entry.key)
                        .with(RollTrigger(3))
                        .with(entry.key == HexTileType.Desert)
            }
        }
    }

    private fun buildWithBuilder() = PlacedHexTiles.Builder(getAllTileBuilders().toMutableList()).build()
    private fun Pair<Int, Int>.toAbsoluteCorner() = AbsoluteCorner(HexTileIndex(first), TileCornerIndex(second))

    @Test
    fun `getSettlementsCount is correct`() {
        val placedTiles = buildWithBuilder()
        val settlementBuilder = PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) {
            MutableList(HexTile.SIDE_COUNT) { false }
        })
        val settlementsPlaced = settlementBuilder
                .placeOn((1 to 2).toAbsoluteCorner(), placedTiles.get(HexTileIndex(1)).sides)
                .placeOn((1 to 1).toAbsoluteCorner(), placedTiles.get(HexTileIndex(1)).sides)
                .placeOn((7 to 4).toAbsoluteCorner(), placedTiles.get(HexTileIndex(7)).sides)
                .build()
        val boardState = GameBoardState(
                hexTiles = placedTiles,
                ports = PlacedPorts.Builder.createAllPorts().build(),
                players = listOf(mock(Party::class.java), mock(Party::class.java), mock(Party::class.java)),
                turnTrackerLinearId = UniqueIdentifier(),
                robberLinearId = UniqueIdentifier(),
                settlementsPlaced = settlementsPlaced)

        assertEquals(3, boardState.getSettlementsCount())
    }
}
