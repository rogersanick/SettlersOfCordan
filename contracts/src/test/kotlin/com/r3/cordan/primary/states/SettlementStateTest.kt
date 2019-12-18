package com.r3.cordan.primary.states

import com.r3.cordan.primary.states.board.AbsoluteCorner
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.board.TileCornerIndex
import com.r3.cordan.primary.states.structure.SettlementState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementStateTest {

    private fun makeBasicSettlement() = SettlementState(
            gameBoardLinearId = UniqueIdentifier(),
            absoluteCorner = AbsoluteCorner(HexTileIndex(1), TileCornerIndex(0)),
            players = listOf(mock(Party::class.java), mock(Party::class.java), mock(Party::class.java)),
            owner = mock(Party::class.java))

    @Test
    fun `upgradedToCity is correct`() {
        makeBasicSettlement().also {
            assertFalse(it.upgradedToCity)
            assertTrue(it.upgradeToCity().upgradedToCity)
        }
    }

    @Test
    fun `resourceAmountClaim is correct`() {
        makeBasicSettlement().also {
            assertEquals(SettlementState.settlementAmountClaim, it.resourceAmountClaim)
            assertEquals(SettlementState.cityAmountClaim, it.upgradeToCity().resourceAmountClaim)
        }
    }
}