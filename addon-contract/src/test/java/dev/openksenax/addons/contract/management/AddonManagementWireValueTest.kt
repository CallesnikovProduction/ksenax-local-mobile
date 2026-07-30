package dev.openksenax.addons.contract.management

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonManagementWireValueTest {

    @Test
    fun `unknown wire enum values fail closed`() {
        assertEquals(
            AddonCommandStatus.UNKNOWN,
            AddonCommandStatus.fromWireValue("FUTURE_STATUS"),
        )
        assertEquals(
            AddonRuntimeState.UNKNOWN,
            AddonRuntimeState.fromWireValue(null),
        )
        assertEquals(
            AddonManagementOperation.UNKNOWN,
            AddonManagementOperation.fromWireValue("FUTURE_OPERATION"),
        )
        assertEquals(
            AddonManagementFailureCode.UNKNOWN,
            AddonManagementFailureCode.fromWireValue("FUTURE_FAILURE"),
        )
    }
}
