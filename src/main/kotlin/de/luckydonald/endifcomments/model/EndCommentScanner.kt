package de.luckydonald.endifcomments.model

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyCaseClause
import com.jetbrains.python.psi.PyMatchStatement

/**
 * Matches a whole-line `# end <keyword> ...` comment, in either its correct form (`# end if`) or a
 * wrong form that still repeats a name (`# end def foobar`) — both count as "a real marker already
 * exists here" for [findRealEndComment], and both are what [de.luckydonald.endifcomments.inspection.RedundantEndCommentInspection]
 * flags, since once this plugin renders the marker virtually, no real one should be hand-maintained.
 */
val END_COMMENT_LINE_REGEX: Regex =
    Regex("""^#\s*end\s+(${ALL_END_KEYWORDS.joinToString("|")})\b.*$""", RegexOption.IGNORE_CASE)

object EndCommentScanner {

    /** Walks [file] and returns one [EndMarker] per block-opening statement it covers. */
    fun collectMarkers(file: PsiFile, document: Document): List<EndMarker> {
        val markers = mutableListOf<EndMarker>()

        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                endKeywordFor(element)?.let { keyword ->
                    markers += markerFor(element, "# end $keyword", document)
                }

                if (element is PyMatchStatement) {
                    val lastCase = PsiTreeUtil.getChildrenOfType(element, PyCaseClause::class.java)?.lastOrNull()
                    if (lastCase != null) {
                        markers += markerFor(lastCase, "# end case", document)
                    }
                }
            }
        })

        return markers
    }

    /**
     * If a real comment already sits on the line right after [marker]'s block, at the block's own
     * indentation, and matches [END_COMMENT_LINE_REGEX], returns it — the caller should suppress the
     * virtual hint for that marker and let the inspection flag the real comment instead.
     */
    fun findRealEndComment(file: PsiFile, document: Document, marker: EndMarker): PsiComment? {
        val anchorLine = document.getLineNumber(marker.anchorOffset)
        val nextLine = anchorLine + 1
        if (nextLine >= document.lineCount) return null

        val lineStart = document.getLineStartOffset(nextLine)
        val lineEnd = document.getLineEndOffset(nextLine)
        val lineText = document.charsSequence.subSequence(lineStart, lineEnd)
        val trimmed = lineText.trim()
        if (!END_COMMENT_LINE_REGEX.matches(trimmed)) return null

        val firstNonWsOffset = lineStart + (lineText.length - lineText.trimStart().length)
        val element = file.findElementAt(firstNonWsOffset) ?: return null
        return element as? PsiComment
            ?: PsiTreeUtil.getNextSiblingOfType(element, PsiComment::class.java)
            ?: element.parent as? PsiComment
    }

    /**
     * [collectMarkers], with markers suppressed by a real [findRealEndComment] removed, sorted by
     * anchor line then by descending [EndMarker.indentColumn] — the exact list the rendering pass
     * turns into stacked block inlays (innermost/deepest block first at a shared anchor line).
     */
    fun visibleMarkers(file: PsiFile, document: Document): List<EndMarker> =
        collectMarkers(file, document)
            .filterNot { findRealEndComment(file, document, it) != null }
            .sortedWith(compareBy({ it.anchorOffset }, { -it.indentColumn }))

    private fun markerFor(statement: PsiElement, text: String, document: Document): EndMarker {
        val startOffset = statement.textRange.startOffset
        val startLine = document.getLineNumber(startOffset)
        val indentColumn = startOffset - document.getLineStartOffset(startLine)

        val effectiveEndOffset = lastMeaningfulOffset(statement)
        val endLine = document.getLineNumber((effectiveEndOffset - 1).coerceAtLeast(startOffset))
        val anchorOffset = document.getLineEndOffset(endLine)

        return EndMarker(statement, text, anchorOffset, indentColumn)
    }

    /**
     * End offset of the last non-blank content inside [statement], ignoring trailing whitespace.
     *
     * While a block is still being typed (e.g. right after `if True:` + Enter), the parser attaches
     * the freshly auto-indented, still-empty next line to the statement's suite as it reparses — if
     * we anchored on the raw [PsiElement.getTextRange] end, the marker would then jump to sit right
     * after that blank line, i.e. right where the user is about to type, instead of staying under the
     * last real statement. Walking down through trailing whitespace nodes keeps the anchor pinned to
     * the last actual content regardless of how much blank trailing indentation the parser has (or
     * hasn't yet) attached.
     */
    private fun lastMeaningfulOffset(statement: PsiElement): Int {
        var node: ASTNode = statement.node
        while (true) {
            var child = node.lastChildNode
            while (child != null && (child.elementType == TokenType.WHITE_SPACE || child.textLength == 0)) {
                child = child.treePrev
            }
            if (child == null) break
            node = child
        }
        return node.startOffset + node.textLength
    }
}
