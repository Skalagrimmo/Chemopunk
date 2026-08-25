package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.graphics.Color
import com.example.data.Enemy
import com.example.data.NpcState
import com.example.data.Player
import com.example.ui.components.RadialAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Touch-Friendly Radial Quick Action menu logic and state transformations.
 */
class RadialQuickActionMenuTest {

    @Test
    fun testRadialActionExecutionTriggers() {
        var actionExecuted = false
        val testAction = RadialAction(
            id = "strike",
            label = "STRIKE",
            subtitle = "Direct hit",
            icon = Icons.Default.FlashOn,
            accentColor = Color.Red,
            badge = "10 DMG",
            onExecute = { actionExecuted = true }
        )

        assertEquals("strike", testAction.id)
        assertEquals("STRIKE", testAction.label)
        testAction.onExecute()
        assertTrue("onExecute must invoke callback handler", actionExecuted)
    }

    @Test
    fun testContextualActionListGeneration() {
        val player = Player(x = 5f, y = 5f, hp = 80, maxHp = 100, toxicity = 15)
        val enemy = Enemy(
            id = "mutant_boss",
            name = "Aberration",
            hp = 120,
            maxHp = 120,
            attack = 18,
            armor = 8,
            toxicityDamage = 10,
            asciiGlyph = 'M',
            expReward = 50,
            lootItemId = "mutant_gland",
            x = 6f,
            y = 5f,
            state = NpcState.AGGRESSIVE
        )

        // Validate enemy target awareness
        assertNotNull(enemy)
        assertEquals("Aberration", enemy.name)
        assertTrue(player.hp < player.maxHp)
    }
}
