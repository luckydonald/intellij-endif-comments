package de.luckydonald.endifcomments.settings

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import de.luckydonald.endifcomments.inlay.EndCommentInlayStyle
import de.luckydonald.endifcomments.inlay.paintStyledText
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JPanel

internal data class ColorSourceOption(val id: String, val label: String) {
    override fun toString(): String = label
}

internal val COLOR_SOURCE_OPTIONS = listOf(
    ColorSourceOption("THEME", "Theme line-comment color"),
    ColorSourceOption("CUSTOM", "Custom color"),
)

internal data class HighlightOption(val id: String, val label: String) {
    override fun toString(): String = label
}

internal val HIGHLIGHT_OPTIONS = listOf(
    HighlightOption("LIKE_DEPRECATED", "Strikethrough"),
    HighlightOption("LIKE_UNUSED_SYMBOL", "Grayed out"),
    HighlightOption("GENERIC_ERROR_OR_WARNING", "Warning underline"),
    HighlightOption("WEAK_WARNING", "Weak warning"),
)

/**
 * No `groupId`/`parentId` is declared for this configurable's `<applicationConfigurable>` entry in
 * `plugin.xml`, which is what makes the IDE place it under the synthetic **Settings > Other Settings**
 * bucket rather than nesting it under an existing top-level group.
 *
 * Every component is wired with a plain `addItemListener`/`addActionListener` instead of the DSL's
 * `bindSelected`/`bindItem` — those only push the component's value into the bound property when the
 * *panel's own* `apply()`/`reset()` run (see `Cell.bind` in the platform sources), which this class
 * never calls; using them here would make every control in Settings silently no-op on user interaction.
 * Direct listeners update `pendingXxx` (and repaint the previews) immediately, exactly like a real user
 * edit should.
 *
 * The Swing components built in [createComponent] are kept as `internal` fields (rather than locals
 * inside the `panel { }` builder) purely so [EndCommentConfigurableTest] can drive them the same way a
 * real Settings dialog would (toggling checkboxes, changing combo selections) instead of poking the
 * private `pendingXxx` fields directly.
 */
class EndCommentConfigurable : Configurable {

    private val state = EndCommentSettingsState.getInstance()

    private var pendingIsActive = state.isActive
    private var pendingInlayItalic = state.inlayItalic
    private var pendingInlayBold = state.inlayBold
    private var pendingInlayStrikethrough = state.inlayStrikethrough
    private var pendingInlayUnderline = state.inlayUnderline
    private var pendingColorSource = colorSourceOptionFor(state.inlayColorSource)
    private var pendingCustomColor: Color = Color(state.inlayCustomColorRgb)
    private var pendingHighlightOption = highlightOptionFor(state.redundantHighlightType)

    internal val inlayPreview: JPanel = InlayStylePreviewPanel { currentInlayPreviewStyle() }
    internal val redundantPreview: JPanel = RedundantStylePreviewPanel { pendingHighlightOption.id }

    internal lateinit var activeCheckBox: JBCheckBox
        private set
    internal lateinit var italicCheckBox: JBCheckBox
        private set
    internal lateinit var boldCheckBox: JBCheckBox
        private set
    internal lateinit var strikethroughCheckBox: JBCheckBox
        private set
    internal lateinit var underlineCheckBox: JBCheckBox
        private set
    internal lateinit var colorSourceCombo: ComboBox<ColorSourceOption>
        private set
    internal lateinit var colorPanel: ColorPanel
        private set
    internal lateinit var highlightCombo: ComboBox<HighlightOption>
        private set

    override fun getDisplayName(): String = "Explicit Block Endings"

    override fun createComponent(): JComponent = panel {
        row {
            activeCheckBox = checkBox("Active").component.apply {
                isSelected = pendingIsActive
                addItemListener { pendingIsActive = isSelected }
            }
        }

        group("Virtual End-Comment Style") {
            row {
                italicCheckBox = checkBox("Italic").component.apply {
                    isSelected = pendingInlayItalic
                    addItemListener { pendingInlayItalic = isSelected; inlayPreview.repaint() }
                }
                boldCheckBox = checkBox("Bold").component.apply {
                    isSelected = pendingInlayBold
                    addItemListener { pendingInlayBold = isSelected; inlayPreview.repaint() }
                }
            }
            row {
                strikethroughCheckBox = checkBox("Strikethrough").component.apply {
                    isSelected = pendingInlayStrikethrough
                    addItemListener { pendingInlayStrikethrough = isSelected; inlayPreview.repaint() }
                }
                underlineCheckBox = checkBox("Underline").component.apply {
                    isSelected = pendingInlayUnderline
                    addItemListener { pendingInlayUnderline = isSelected; inlayPreview.repaint() }
                }
            }
            row("Color:") {
                colorPanel = ColorPanel().apply {
                    selectedColor = pendingCustomColor
                    isEnabled = pendingColorSource.id == "CUSTOM"
                    addActionListener { selectedColor?.let(::applyCustomColor) }
                }
                colorSourceCombo = comboBox(COLOR_SOURCE_OPTIONS).component.apply {
                    selectedItem = pendingColorSource
                    addItemListener { event ->
                        if (event.stateChange != ItemEvent.SELECTED) return@addItemListener
                        pendingColorSource = event.item as ColorSourceOption
                        colorPanel.isEnabled = pendingColorSource.id == "CUSTOM"
                        inlayPreview.repaint()
                    }
                }
                cell(colorPanel)
            }
            row("Preview:") {
                cell(inlayPreview)
            }
        }

        group("Redundant Comment Warning Style") {
            row("Style:") {
                highlightCombo = comboBox(HIGHLIGHT_OPTIONS).component.apply {
                    selectedItem = pendingHighlightOption
                    addItemListener { event ->
                        if (event.stateChange != ItemEvent.SELECTED) return@addItemListener
                        pendingHighlightOption = event.item as HighlightOption
                        redundantPreview.repaint()
                    }
                }
            }
            row("Preview:") {
                cell(redundantPreview)
            }
        }
    }

    private fun applyCustomColor(color: Color) {
        pendingCustomColor = color
        inlayPreview.repaint()
    }

    /** Test-only hook: [ColorPanel] opens a real (non-headless) color-chooser dialog on click, so
     * tests simulate "the user picked [color]" by driving the same callback [colorPanel]'s own
     * `addActionListener` above uses, instead of a real click. */
    internal fun pickCustomColorForTest(color: Color) {
        colorPanel.selectedColor = color
        applyCustomColor(color)
    }

    internal fun currentInlayPreviewStyle(): EndCommentInlayStyle = EndCommentInlayStyle(
        italic = pendingInlayItalic,
        bold = pendingInlayBold,
        strikethrough = pendingInlayStrikethrough,
        underline = pendingInlayUnderline,
        customColor = if (pendingColorSource.id == "CUSTOM") pendingCustomColor else null,
    )

    internal fun currentRedundantHighlightId(): String = pendingHighlightOption.id

    override fun isModified(): Boolean =
        pendingIsActive != state.isActive ||
            pendingInlayItalic != state.inlayItalic ||
            pendingInlayBold != state.inlayBold ||
            pendingInlayStrikethrough != state.inlayStrikethrough ||
            pendingInlayUnderline != state.inlayUnderline ||
            pendingColorSource.id != state.inlayColorSource ||
            pendingCustomColor.rgb != state.inlayCustomColorRgb ||
            pendingHighlightOption.id != state.redundantHighlightType

    override fun apply() {
        state.isActive = pendingIsActive
        state.inlayItalic = pendingInlayItalic
        state.inlayBold = pendingInlayBold
        state.inlayStrikethrough = pendingInlayStrikethrough
        state.inlayUnderline = pendingInlayUnderline
        state.inlayColorSource = pendingColorSource.id
        state.inlayCustomColorRgb = pendingCustomColor.rgb
        state.redundantHighlightType = pendingHighlightOption.id
    }

    override fun reset() {
        pendingIsActive = state.isActive
        pendingInlayItalic = state.inlayItalic
        pendingInlayBold = state.inlayBold
        pendingInlayStrikethrough = state.inlayStrikethrough
        pendingInlayUnderline = state.inlayUnderline
        pendingColorSource = colorSourceOptionFor(state.inlayColorSource)
        pendingCustomColor = Color(state.inlayCustomColorRgb)
        pendingHighlightOption = highlightOptionFor(state.redundantHighlightType)

        // Components only exist once createComponent() has run; the platform always calls it before
        // the first reset(), but push the pending values back into them here too (rather than relying
        // on component-construction-time initial values) so a *second* reset() — e.g. after Cancel and
        // reopening the same dialog instance — also discards any live-but-unapplied edits.
        if (::activeCheckBox.isInitialized) {
            activeCheckBox.isSelected = pendingIsActive
            italicCheckBox.isSelected = pendingInlayItalic
            boldCheckBox.isSelected = pendingInlayBold
            strikethroughCheckBox.isSelected = pendingInlayStrikethrough
            underlineCheckBox.isSelected = pendingInlayUnderline
            colorSourceCombo.selectedItem = pendingColorSource
            colorPanel.selectedColor = pendingCustomColor
            colorPanel.isEnabled = pendingColorSource.id == "CUSTOM"
            highlightCombo.selectedItem = pendingHighlightOption
        }

        inlayPreview.repaint()
        redundantPreview.repaint()
    }
}

private fun colorSourceOptionFor(id: String): ColorSourceOption =
    COLOR_SOURCE_OPTIONS.firstOrNull { it.id == id } ?: COLOR_SOURCE_OPTIONS[0]

private fun highlightOptionFor(id: String): HighlightOption =
    HIGHLIGHT_OPTIONS.firstOrNull { it.id == id } ?: HIGHLIGHT_OPTIONS[0]

/** Live preview of the virtual `# end <keyword>` inlay, reusing the exact same paint routine it uses. */
private class InlayStylePreviewPanel(private val styleSupplier: () -> EndCommentInlayStyle) : JPanel() {
    init {
        preferredSize = Dimension(200, 24)
        isOpaque = true
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val scheme = EditorColorsManager.getInstance().globalScheme
        g.color = scheme.defaultBackground
        g.fillRect(0, 0, width, height)
        val fallbackColor = scheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT).foregroundColor
            ?: scheme.defaultForeground
        val baseFont = scheme.getFont(EditorFontType.PLAIN)
        val metrics = g.getFontMetrics(baseFont)
        val y = (height + metrics.ascent) / 2 - metrics.descent / 2
        paintStyledText(g, "# end if", 4, y, baseFont, fallbackColor, styleSupplier())
    }
}

/**
 * Live preview approximating each [com.intellij.codeInspection.ProblemHighlightType] preset. This is a
 * Swing-only approximation for the settings panel — it doesn't need pixel-parity with the
 * daemon-driven inspection highlight, only to give the user a rough sense of "strikethrough" vs.
 * "grayed out" vs. "underline" styles.
 */
private class RedundantStylePreviewPanel(private val highlightIdSupplier: () -> String) : JPanel() {
    init {
        preferredSize = Dimension(200, 24)
        isOpaque = true
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val scheme = EditorColorsManager.getInstance().globalScheme
        g.color = scheme.defaultBackground
        g.fillRect(0, 0, width, height)

        val text = "# end class"
        val baseFont = scheme.getFont(EditorFontType.PLAIN)
        g.font = baseFont
        val metrics: FontMetrics = g.fontMetrics
        val x = 4
        val y = (height + metrics.ascent) / 2 - metrics.descent / 2
        val textColor = scheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT).foregroundColor
            ?: scheme.defaultForeground

        when (highlightIdSupplier()) {
            "LIKE_UNUSED_SYMBOL" -> g.color = Color(
                textColor.red, textColor.green, textColor.blue,
                (textColor.alpha * 0.5).toInt().coerceIn(0, 255),
            )
            else -> g.color = textColor
        }
        g.drawString(text, x, y)

        val width = metrics.stringWidth(text)
        when (highlightIdSupplier()) {
            "LIKE_DEPRECATED" -> {
                val strikeY = y - metrics.ascent / 3
                g.drawLine(x, strikeY, x + width, strikeY)
            }
            "GENERIC_ERROR_OR_WARNING" -> drawWavyLine(g, x, y + 2, width, Color.RED)
            "WEAK_WARNING" -> drawDottedLine(g, x, y + 2, width, Color(180, 140, 0))
        }
    }

    private fun drawWavyLine(g: Graphics, x: Int, y: Int, width: Int, color: Color) {
        g.color = color
        var currentX = x
        var up = true
        while (currentX < x + width) {
            val nextX = (currentX + 3).coerceAtMost(x + width)
            g.drawLine(currentX, if (up) y else y + 2, nextX, if (up) y + 2 else y)
            currentX = nextX
            up = !up
        }
    }

    private fun drawDottedLine(g: Graphics, x: Int, y: Int, width: Int, color: Color) {
        g.color = color
        var currentX = x
        while (currentX < x + width) {
            g.drawLine(currentX, y, (currentX + 1).coerceAtMost(x + width), y)
            currentX += 3
        }
    }
}
