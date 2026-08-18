package de.luckydonald.endifcomments

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.luckydonald.endifcomments.inspection.RedundantEndCommentInspection
import de.luckydonald.endifcomments.model.EndCommentScanner
import de.luckydonald.endifcomments.settings.EndCommentSettingsState

class EndCommentPluginTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testNestedWithInsideIfStacksInnermostFirst() {
        myFixture.configureByFile("nested.py")

        val texts = visibleMarkerTexts()

        assertEquals(listOf("# end with", "# end if", "# end def", "# end class"), texts)
    }

    fun testMatchCaseAddsBothCaseAndMatchMarkers() {
        myFixture.configureByFile("matchcase.py")

        val texts = visibleMarkerTexts()

        assertEquals(listOf("# end case", "# end match", "# end def"), texts)
    }

    fun testNeverModifiesTheFile() {
        val before = myFixture.configureByFile("nested.py").text

        EndCommentScanner.visibleMarkers(myFixture.file, myFixture.editor.document)

        assertEquals(before, myFixture.editor.document.text)
    }

    fun testRealEndCommentSuppressesTheVirtualHintAndIsFlagged() {
        myFixture.enableInspections(RedundantEndCommentInspection())
        myFixture.configureByFile("realComment.py")

        // The virtual "# end if" is suppressed because a real one already occupies that spot.
        assertTrue(visibleMarkerTexts().none { it == "# end if" })

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("Redundant explicit block-ending comment") == true })
    }

    fun testWrongFormEndCommentIsAlsoFlagged() {
        myFixture.enableInspections(RedundantEndCommentInspection())
        myFixture.configureByFile("wrongFormComment.py")

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("Redundant explicit block-ending comment") == true })
    }

    fun testActiveSettingPersists() {
        val settings = EndCommentSettingsState.getInstance()
        val original = settings.isActive
        try {
            settings.isActive = false
            assertFalse(EndCommentSettingsState.getInstance().isActive)

            settings.isActive = true
            assertTrue(EndCommentSettingsState.getInstance().isActive)
        } finally {
            settings.isActive = original
        }
    }

    private fun visibleMarkerTexts(): List<String> =
        EndCommentScanner.visibleMarkers(myFixture.file, myFixture.editor.document).map { it.text }
}
