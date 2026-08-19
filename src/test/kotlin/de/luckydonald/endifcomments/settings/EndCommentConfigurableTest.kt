package de.luckydonald.endifcomments.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.luckydonald.endifcomments.inlay.EndCommentInlayRenderer
import de.luckydonald.endifcomments.inlay.EndCommentInlayStyle
import de.luckydonald.endifcomments.inspection.RedundantEndCommentInspection
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent

/**
 * Exercises [EndCommentConfigurable] the way the real Settings dialog does: build the panel, drive
 * the actual Swing components it exposes (not the private `pendingXxx` fields), then check
 * [EndCommentConfigurable.isModified]/`apply`/`reset` behave like the dialog's Apply/OK/Cancel buttons
 * would. "OK" = `apply()`; "Cancel" = discarding the configurable (never calling `apply()`) — the
 * platform's Settings dialog never calls `apply()` on Cancel, so that's exactly what distinguishes the
 * two paths here.
 */
class EndCommentConfigurableTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private lateinit var originalState: Snapshot

    override fun setUp() {
        super.setUp()
        originalState = Snapshot.of(EndCommentSettingsState.getInstance())
    }

    override fun tearDown() {
        try {
            originalState.restoreTo(EndCommentSettingsState.getInstance())
        } finally {
            super.tearDown()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Activate
    // ---------------------------------------------------------------------------------------

    fun testActivateTogglesInlaysAndInspectionOnAndOff() {
        myFixture.enableInspections(RedundantEndCommentInspection())

        // 0. Sanity: the features are available in the test file by default.
        assertTrue(EndCommentSettingsState.getInstance().isActive)
        assertEquals(listOf("# end with", "# end if", "# end def", "# end class"), inlayTextsFor("nested.py"))
        assertTrue(redundantWarningPresentFor("realComment.py"))

        // 1/2. Open settings: Active is on by default.
        val configurable = openConfigurable()
        assertTrue(configurable.activeCheckBox.isSelected)

        // 3/4. Disable, then OK.
        configurable.activeCheckBox.isSelected = false
        assertTrue("Apply should become available after unchecking Active", configurable.isModified())
        configurable.apply()
        assertFalse(EndCommentSettingsState.getInstance().isActive)

        // 5. Features gone.
        assertEquals(emptyList<String>(), inlayTextsFor("nested.py"))
        assertFalse(redundantWarningPresentFor("realComment.py"))

        // 6/7. Reopen settings, Active reflects the disabled state.
        val configurable2 = openConfigurable()
        assertFalse(configurable2.activeCheckBox.isSelected)

        // 7/8. Enable, then OK.
        configurable2.activeCheckBox.isSelected = true
        assertTrue(configurable2.isModified())
        configurable2.apply()
        assertTrue(EndCommentSettingsState.getInstance().isActive)

        // 9. Features back.
        assertEquals(listOf("# end with", "# end if", "# end def", "# end class"), inlayTextsFor("nested.py"))
        assertTrue(redundantWarningPresentFor("realComment.py"))
    }

    // ---------------------------------------------------------------------------------------
    // Virtual End-Comment Style
    // ---------------------------------------------------------------------------------------

    fun testEachInlayStyleToggleChangesTheResolvedPreviewStyle() {
        val configurable = openConfigurable()

        assertEquals(EndCommentInlayStyle(), configurable.currentInlayPreviewStyle())

        configurable.italicCheckBox.isSelected = true
        assertEquals(EndCommentInlayStyle(italic = true), configurable.currentInlayPreviewStyle())
        configurable.italicCheckBox.isSelected = false

        configurable.boldCheckBox.isSelected = true
        assertEquals(EndCommentInlayStyle(bold = true), configurable.currentInlayPreviewStyle())
        configurable.boldCheckBox.isSelected = false

        configurable.strikethroughCheckBox.isSelected = true
        assertEquals(EndCommentInlayStyle(strikethrough = true), configurable.currentInlayPreviewStyle())
        configurable.strikethroughCheckBox.isSelected = false

        configurable.underlineCheckBox.isSelected = true
        assertEquals(EndCommentInlayStyle(underline = true), configurable.currentInlayPreviewStyle())
        configurable.underlineCheckBox.isSelected = false
    }

    /** All 16 combinations of the 4 boolean style toggles: the resolved preview style must track them exactly. */
    fun testInlayStyleToggleCombinationMatrix() {
        val configurable = openConfigurable()

        for (italic in listOf(false, true)) {
            for (bold in listOf(false, true)) {
                for (strikethrough in listOf(false, true)) {
                    for (underline in listOf(false, true)) {
                        configurable.italicCheckBox.isSelected = italic
                        configurable.boldCheckBox.isSelected = bold
                        configurable.strikethroughCheckBox.isSelected = strikethrough
                        configurable.underlineCheckBox.isSelected = underline

                        assertEquals(
                            "italic=$italic bold=$bold strikethrough=$strikethrough underline=$underline",
                            EndCommentInlayStyle(italic = italic, bold = bold, strikethrough = strikethrough, underline = underline),
                            configurable.currentInlayPreviewStyle(),
                        )
                    }
                }
            }
        }
    }

    fun testInlayPreviewActuallyRepaintsDifferentlyWhenStyleChanges() {
        val configurable = openConfigurable()

        val plain = renderToImage(configurable.inlayPreview)

        configurable.italicCheckBox.isSelected = true
        configurable.boldCheckBox.isSelected = true
        configurable.strikethroughCheckBox.isSelected = true
        configurable.underlineCheckBox.isSelected = true
        val styled = renderToImage(configurable.inlayPreview)

        assertFalse("preview pixels should differ once every style toggle is enabled", imagesEqual(plain, styled))
    }

    fun testColorPickerEnabledOnlyWhenCustomColorSelected() {
        val configurable = openConfigurable()

        assertEquals("THEME", colorSourceOptionId(configurable))
        assertFalse("color picker should be disabled while the theme color is used", configurable.colorPanel.isEnabled)

        configurable.colorSourceCombo.selectedItem = ColorSourceOption("CUSTOM", "Custom color")
        assertEquals("CUSTOM", colorSourceOptionId(configurable))
        assertTrue("color picker should become enabled once Custom color is selected", configurable.colorPanel.isEnabled)

        // "click does nothing" while disabled: with a real (disabled) ColorPanel, Swing itself refuses
        // to dispatch mouse clicks to a disabled component's internal swatch button, so there is no
        // action to simulate here — switching back to the theme color must simply disable it again.
        configurable.colorSourceCombo.selectedItem = ColorSourceOption("THEME", "Theme line-comment color")
        assertFalse(configurable.colorPanel.isEnabled)
    }

    fun testCustomColorIsPickedUpForMultipleColors() {
        val configurable = openConfigurable()
        configurable.colorSourceCombo.selectedItem = ColorSourceOption("CUSTOM", "Custom color")

        configurable.pickCustomColorForTest(Color.RED)
        assertEquals(Color.RED, configurable.currentInlayPreviewStyle().customColor)

        configurable.pickCustomColorForTest(Color(10, 20, 30))
        assertEquals(Color(10, 20, 30), configurable.currentInlayPreviewStyle().customColor)
    }

    // ---------------------------------------------------------------------------------------
    // Redundant Comment Warning Style
    // ---------------------------------------------------------------------------------------

    fun testEachHighlightDropdownOptionIsReflectedInStateAndPreview() {
        val configurable = openConfigurable()
        val images = mutableMapOf<String, BufferedImage>()

        for (option in HIGHLIGHT_OPTIONS) {
            configurable.highlightCombo.selectedItem = option
            assertEquals(option.id, configurable.currentRedundantHighlightId())
            images[option.id] = renderToImage(configurable.redundantPreview)
        }

        // Every preset should render visibly differently from every other preset.
        for (a in HIGHLIGHT_OPTIONS) {
            for (b in HIGHLIGHT_OPTIONS) {
                if (a.id == b.id) continue
                assertFalse(
                    "expected '${a.id}' and '${b.id}' previews to differ",
                    imagesEqual(images.getValue(a.id), images.getValue(b.id)),
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Apply / OK / Cancel, for every setting
    // ---------------------------------------------------------------------------------------

    private fun mutations(): List<Pair<String, (EndCommentConfigurable) -> Unit>> = listOf(
        "isActive" to { c: EndCommentConfigurable -> c.activeCheckBox.isSelected = !c.activeCheckBox.isSelected },
        "inlayItalic" to { c: EndCommentConfigurable -> c.italicCheckBox.isSelected = true },
        "inlayBold" to { c: EndCommentConfigurable -> c.boldCheckBox.isSelected = true },
        "inlayStrikethrough" to { c: EndCommentConfigurable -> c.strikethroughCheckBox.isSelected = true },
        "inlayUnderline" to { c: EndCommentConfigurable -> c.underlineCheckBox.isSelected = true },
        "inlayColorSource+customColor" to { c: EndCommentConfigurable ->
            c.colorSourceCombo.selectedItem = ColorSourceOption("CUSTOM", "Custom color")
            c.pickCustomColorForTest(Color(1, 2, 3))
        },
        "redundantHighlightType" to { c: EndCommentConfigurable -> c.highlightCombo.selectedItem = HighlightOption("WEAK_WARNING", "Weak warning") },
    )

    fun testApplyBecomesAvailableAfterEachSettingChanges() {
        for ((name, mutate) in mutations()) {
            val configurable = openConfigurable()
            assertFalse("$name: should start unmodified", configurable.isModified())
            mutate(configurable)
            assertTrue("$name: Apply should be available after the change", configurable.isModified())
        }
    }

    fun testOkPersistsEachSettingToTheSettingsState() {
        for ((name, mutate) in mutations()) {
            val before = Snapshot.of(EndCommentSettingsState.getInstance())
            val configurable = openConfigurable()
            mutate(configurable)
            configurable.apply() // "OK"

            assertFalse("$name: Apply should be unavailable right after OK", configurable.isModified())
            assertFalse("$name: settings state should have changed after OK", before.matches(EndCommentSettingsState.getInstance()))

            before.restoreTo(EndCommentSettingsState.getInstance())
        }
    }

    fun testCancelDiscardsEachSettingChange() {
        for ((name, mutate) in mutations()) {
            val before = Snapshot.of(EndCommentSettingsState.getInstance())
            val configurable = openConfigurable()
            mutate(configurable)
            assertTrue("$name: sanity, the change should be visible as modified", configurable.isModified())

            // "Cancel": the dialog simply drops the configurable without ever calling apply().

            assertTrue("$name: settings state must be untouched after Cancel", before.matches(EndCommentSettingsState.getInstance()))

            // Reopening after Cancel must show the persisted (unchanged) value again, not the discarded edit.
            val reopened = openConfigurable()
            assertFalse("$name: reopened dialog should not carry over the cancelled edit", reopened.isModified())
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun openConfigurable(): EndCommentConfigurable {
        val configurable = EndCommentConfigurable()
        configurable.createComponent()
        configurable.reset()
        return configurable
    }

    private fun colorSourceOptionId(configurable: EndCommentConfigurable): String =
        (configurable.colorSourceCombo.selectedItem as ColorSourceOption).id

    private fun inlayTextsFor(fileName: String): List<String> {
        myFixture.configureByFile(fileName)
        myFixture.doHighlighting()
        val document = myFixture.editor.document
        return myFixture.editor.inlayModel
            .getBlockElementsInRange(0, document.textLength)
            .mapNotNull { (it.renderer as? EndCommentInlayRenderer)?.text }
    }

    private fun redundantWarningPresentFor(fileName: String): Boolean {
        myFixture.configureByFile(fileName)
        return myFixture.doHighlighting().any { it.description?.contains("Redundant explicit block-ending comment") == true }
    }

    private fun renderToImage(component: JComponent): BufferedImage {
        component.size = Dimension(200, 24)
        component.doLayout()
        val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            component.paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun imagesEqual(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (x in 0 until a.width) {
            for (y in 0 until a.height) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false
            }
        }
        return true
    }

    private class Snapshot(
        val isActive: Boolean,
        val inlayItalic: Boolean,
        val inlayBold: Boolean,
        val inlayStrikethrough: Boolean,
        val inlayUnderline: Boolean,
        val inlayColorSource: String,
        val inlayCustomColorRgb: Int,
        val redundantHighlightType: String,
    ) {
        fun matches(state: EndCommentSettingsState): Boolean =
            isActive == state.isActive &&
                inlayItalic == state.inlayItalic &&
                inlayBold == state.inlayBold &&
                inlayStrikethrough == state.inlayStrikethrough &&
                inlayUnderline == state.inlayUnderline &&
                inlayColorSource == state.inlayColorSource &&
                inlayCustomColorRgb == state.inlayCustomColorRgb &&
                redundantHighlightType == state.redundantHighlightType

        fun restoreTo(state: EndCommentSettingsState) {
            state.isActive = isActive
            state.inlayItalic = inlayItalic
            state.inlayBold = inlayBold
            state.inlayStrikethrough = inlayStrikethrough
            state.inlayUnderline = inlayUnderline
            state.inlayColorSource = inlayColorSource
            state.inlayCustomColorRgb = inlayCustomColorRgb
            state.redundantHighlightType = redundantHighlightType
        }

        companion object {
            fun of(state: EndCommentSettingsState): Snapshot = Snapshot(
                isActive = state.isActive,
                inlayItalic = state.inlayItalic,
                inlayBold = state.inlayBold,
                inlayStrikethrough = state.inlayStrikethrough,
                inlayUnderline = state.inlayUnderline,
                inlayColorSource = state.inlayColorSource,
                inlayCustomColorRgb = state.inlayCustomColorRgb,
                redundantHighlightType = state.redundantHighlightType,
            )
        }
    }
}
