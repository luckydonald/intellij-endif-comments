package de.luckydonald.endifcomments.inlay

import de.luckydonald.endifcomments.settings.EndCommentSettingsState
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics

/**
 * The user-configurable visual style for the virtual `# end <keyword>` inlay text. Resolved once from
 * [EndCommentSettingsState] per render (or per settings-panel preview repaint) rather than read live
 * from settings inside [paintStyledText], so callers control exactly when a settings change takes effect.
 */
data class EndCommentInlayStyle(
    val italic: Boolean = false,
    val bold: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    /** Overrides the caller-supplied fallback color when non-null (i.e. "CUSTOM" color source). */
    val customColor: Color? = null,
) {
    companion object {
        fun fromSettings(settings: EndCommentSettingsState): EndCommentInlayStyle = EndCommentInlayStyle(
            italic = settings.inlayItalic,
            bold = settings.inlayBold,
            strikethrough = settings.inlayStrikethrough,
            underline = settings.inlayUnderline,
            customColor = if (settings.inlayColorSource == "CUSTOM") Color(settings.inlayCustomColorRgb) else null,
        )
    }
}

/**
 * Paints [text] at ([x], [y]) (baseline origin, like [Graphics.drawString]) using [baseFont] plus
 * [style]'s italic/bold/strikethrough/underline, and [style]'s custom color if set, else [fallbackColor].
 * Shared by [EndCommentInlayRenderer] (the real editor inlay) and the settings-panel live preview, so
 * both always render a given style identically.
 */
fun paintStyledText(g: Graphics, text: String, x: Int, y: Int, baseFont: Font, fallbackColor: Color, style: EndCommentInlayStyle) {
    var fontStyle = Font.PLAIN
    if (style.bold) fontStyle = fontStyle or Font.BOLD
    if (style.italic) fontStyle = fontStyle or Font.ITALIC

    g.color = style.customColor ?: fallbackColor
    g.font = baseFont.deriveFont(fontStyle)
    g.drawString(text, x, y)

    if (style.strikethrough || style.underline) {
        val metrics: FontMetrics = g.fontMetrics
        val width = metrics.stringWidth(text)
        if (style.strikethrough) {
            val strikeY = y - (metrics.ascent / 3)
            g.drawLine(x, strikeY, x + width, strikeY)
        }
        if (style.underline) {
            val underlineY = y + (metrics.descent / 2)
            g.drawLine(x, underlineY, x + width, underlineY)
        }
    }
}
