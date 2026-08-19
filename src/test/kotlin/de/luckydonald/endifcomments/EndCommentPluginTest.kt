package de.luckydonald.endifcomments

import com.intellij.psi.PsiDocumentManager
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

    fun testMixedCaseEndCommentIsRecognizedCaseInsensitively() {
        myFixture.enableInspections(RedundantEndCommentInspection())
        myFixture.configureByFile("mixedCaseComment.py")

        // The virtual "# end if" is suppressed even though the real comment is mixed-case.
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

    fun testStyleSettingsPersist() {
        val settings = EndCommentSettingsState.getInstance()
        val originalItalic = settings.inlayItalic
        val originalColorSource = settings.inlayColorSource
        val originalHighlightType = settings.redundantHighlightType
        try {
            settings.inlayItalic = true
            settings.inlayColorSource = "CUSTOM"
            settings.redundantHighlightType = "WEAK_WARNING"

            assertTrue(EndCommentSettingsState.getInstance().inlayItalic)
            assertEquals("CUSTOM", EndCommentSettingsState.getInstance().inlayColorSource)
            assertEquals("WEAK_WARNING", EndCommentSettingsState.getInstance().redundantHighlightType)
        } finally {
            settings.inlayItalic = originalItalic
            settings.inlayColorSource = originalColorSource
            settings.redundantHighlightType = originalHighlightType
        }
    }

    /**
     * Regression test for a bug where, right after typing a block header + Enter, the editor's
     * auto-indent leaves a blank indented line for the caret — and the parser briefly attaches that
     * blank line to the still-empty suite, which used to drag every open block's `# end ...` marker
     * down onto the caret's own line, swapping places with whatever the user types next. Types a
     * class containing a function that recursively nests one of every supported block-opening
     * keyword, checking after each `Enter` that every currently visible marker still anchors above
     * the caret's (blank, not-yet-typed) line rather than on top of it.
     */
    fun testTypingNestedBlocksNeverPlacesEndMarkersOnTheCaretLine() {
        myFixture.configureByText("typing.py", "")

        for (line in listOf(
            "class Foo:\n",
            "def bar():\n",
            "if True:\n",
            "with x:\n",
            "for i in y:\n",
            "while True:\n",
            "try:\n",
            "match z:\n",
            "case 1:\n",
        )) {
            myFixture.type(line)
            assertMarkersAnchorAboveCaretLine()
        }
    }

    private fun assertMarkersAnchorAboveCaretLine() {
        val document = myFixture.editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)

        val caretLine = document.getLineNumber(myFixture.editor.caretModel.offset)
        for (marker in EndCommentScanner.visibleMarkers(myFixture.file, document)) {
            val anchorLine = document.getLineNumber(marker.anchorOffset)
            assertTrue(
                "marker '${marker.text}' anchored at line $anchorLine, expected strictly before caret line $caretLine",
                anchorLine < caretLine,
            )
        }
    }

    private fun visibleMarkerTexts(): List<String> =
        EndCommentScanner.visibleMarkers(myFixture.file, myFixture.editor.document).map { it.text }
}
