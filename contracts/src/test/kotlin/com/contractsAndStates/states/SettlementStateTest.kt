package com.contractsAndStates.states

import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementStateTest {

    private fun makeBasicSettlement() = SettlementState(
            gameBoardPointer = LinearPointer(UniqueIdentifier(), GameBoardState::class.java),
            absoluteCorner= AbsoluteCorner(HexTileIndex(1), TileCornerIndex(0)),
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