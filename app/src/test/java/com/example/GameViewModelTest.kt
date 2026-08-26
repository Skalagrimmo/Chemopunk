package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.Perk
import com.example.data.SkillType
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for GameViewModel Phase 2 feature surfaces:
 * modal state machine, view-mode/palette toggles, zone travel, and
 * safe no-op guards for skill/perk allocation before a profile is loaded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTest {

    private lateinit var context: Application
    private lateinit var viewModel: GameViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        viewModel = GameViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testModalStateMachine() {
        assertEquals(ActiveModal.NONE, viewModel.uiState.value.activeModal)

        viewModel.openModal(ActiveModal.COMBAT)
        assertEquals(ActiveModal.COMBAT, viewModel.uiState.value.activeModal)

        viewModel.openSkillsModal()
        assertEquals(ActiveModal.SKILLS, viewModel.uiState.value.activeModal)

        viewModel.openSynthesisModal()
        assertEquals(ActiveModal.SYNTHESIS, viewModel.uiState.value.activeModal)

        viewModel.openModal(ActiveModal.TRADE)
        assertEquals(ActiveModal.TRADE, viewModel.uiState.value.activeModal)

        viewModel.closeModal()
        assertEquals(ActiveModal.NONE, viewModel.uiState.value.activeModal)
    }

    @Test
    fun testViewModeAndPaletteToggle() {
        assertEquals(ViewMode.ISOMETRIC_WORLD, viewModel.uiState.value.currentViewMode)

        viewModel.setViewMode(ViewMode.STORY_DIALOGUE)
        assertEquals(ViewMode.STORY_DIALOGUE, viewModel.uiState.value.currentViewMode)

        viewModel.setViewMode(ViewMode.MARKDOWN_EDITOR)
        assertEquals(ViewMode.MARKDOWN_EDITOR, viewModel.uiState.value.currentViewMode)

        val before = viewModel.uiState.value.colorPaletteIndex
        viewModel.togglePalette()
        val after = viewModel.uiState.value.colorPaletteIndex
        assertTrue("palette index should advance", after == (before + 1) % 7)
    }

    @Test
    fun testTravelToZoneUpdatesCurrentZone() {
        assertEquals("sector7", viewModel.uiState.value.currentZoneId)

        viewModel.travelToZone("sewers")
        assertEquals("sewers", viewModel.uiState.value.currentZoneId)

        viewModel.travelToZone("surface")
        assertEquals("surface", viewModel.uiState.value.currentZoneId)

        // Unknown zone is ignored (no-op).
        val stuck = viewModel.uiState.value.currentZoneId
        viewModel.travelToZone("does_not_exist")
        assertEquals(stuck, viewModel.uiState.value.currentZoneId)
    }

    @Test
    fun testSkillAndPerkAllocationSafeBeforeProfileLoaded() {
        // Before a profile is initialized these must no-op rather than crash.
        val beforePoints = viewModel.uiState.value.unspentSkillPoints
        viewModel.allocateSkillPoint(SkillType.GUNS)
        assertEquals(beforePoints, viewModel.uiState.value.unspentSkillPoints)

        viewModel.confirmPerkChoice(Perk.POOL.first())
        assertTrue(viewModel.uiState.value.pendingPerkChoices.isEmpty())
        assertFalse(viewModel.uiState.value.activeModal == ActiveModal.PERK_SELECT)
    }
}
