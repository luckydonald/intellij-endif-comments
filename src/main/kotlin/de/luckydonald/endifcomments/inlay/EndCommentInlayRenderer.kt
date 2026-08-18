package de.luckydonald.endifcomments.inlay

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.util.EditorUtil
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Paints one virtual `# end <keyword>` line, indented to [indentColumn] columns, styled like a real
 * line comment. This never touches the file — it only occupies vertical space the editor reserves
 * for the block inlay it renders into.
 */
class EndCommentInlayRenderer(
    val text: String,
    val indentColumn: Int,
) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        return EditorUtil.getPlainSpaceWidth(editor) * (indentColumn + text.length)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlay.editor.lineHeight

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: com.intellij.openapi.editor.markup.TextAttributes) {
        val editor = inlay.editor
        val scheme = EditorColorsManager.getInstance().globalScheme
        val attributes = scheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT)
        g.color = attributes.foregroundColor ?: scheme.defaultForeground
        g.font = scheme.getFont(EditorFontType.PLAIN)

        val spaceWidth = EditorUtil.getPlainSpaceWidth(editor)
        val x = targetRegion.x + spaceWidth * indentColumn
        val y = targetRegion.y + editor.ascent
        g.drawString(text, x, y)
    }
}
