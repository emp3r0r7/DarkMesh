package com.geeksville.mesh.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryAlertSettingsTest {

    @Test
    fun `defaults to all nodes scope when preference value is unknown`() {
        assertEquals(
            BatteryAlertScope.ALL_NODES,
            BatteryAlertScope.fromPreferenceValue("something_else")
        )
    }

    @Test
    fun `connected node scope filters out mesh alerts`() {
        val settings = BatteryAlertSettings(
            enabled = true,
            scope = BatteryAlertScope.CONNECTED_NODE_ONLY,
        )

        assertTrue(settings.allows(BatteryAlertSource.CONNECTED_NODE))
        assertFalse(settings.allows(BatteryAlertSource.MESH))
    }

    @Test
    fun `returns source specific custom sounds`() {
        val settings = BatteryAlertSettings(
            enabled = true,
            connectedNodeSoundUri = "content://sounds/connected",
            meshSoundUri = "content://sounds/mesh",
        )

        assertEquals("content://sounds/connected", settings.soundUriFor(BatteryAlertSource.CONNECTED_NODE))
        assertEquals("content://sounds/mesh", settings.soundUriFor(BatteryAlertSource.MESH))
    }
}
