package de.luckydonald.endifcomments.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Color

@Service
@State(name = "EndCommentSettings", storages = [Storage("endCommentSettings.xml")])
class EndCommentSettingsState : PersistentStateComponent<EndCommentSettingsState> {

    var isActive: Boolean = true

    var inlayItalic: Boolean = false
    var inlayBold: Boolean = false
    var inlayStrikethrough: Boolean = false
    var inlayUnderline: Boolean = false

    /** "THEME" uses the editor's line-comment color; "CUSTOM" uses [inlayCustomColorRgb]. */
    var inlayColorSource: String = "THEME"

    // `Color(rgb).rgb` always ORs in a fully-opaque alpha byte, so the default here must already carry
    // it too — otherwise a value round-tripped through `Color` (as `EndCommentConfigurable` does for its
    // pending/preview state) never equals this raw literal, and `isModified()` looks dirty on open.
    var inlayCustomColorRgb: Int = Color(0x808080).rgb

    /** Name of a [com.intellij.codeInspection.ProblemHighlightType] constant. */
    var redundantHighlightType: String = "LIKE_DEPRECATED"

    override fun getState(): EndCommentSettingsState = this

    override fun loadState(state: EndCommentSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): EndCommentSettingsState =
            ApplicationManager.getApplication().getService(EndCommentSettingsState::class.java)
    }
}
