package de.luckydonald.endifcomments.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * No `groupId`/`parentId` is declared for this configurable's `<applicationConfigurable>` entry in
 * `plugin.xml`, which is what makes the IDE place it under the synthetic **Settings > Other Settings**
 * bucket rather than nesting it under an existing top-level group.
 */
class EndCommentConfigurable : Configurable {

    private val state = EndCommentSettingsState.getInstance()
    private var pendingIsActive = state.isActive

    override fun getDisplayName(): String = "Explicit Block Endings"

    override fun createComponent(): JComponent = panel {
        row {
            checkBox("Active")
                .bindSelected({ pendingIsActive }, { pendingIsActive = it })
        }
    }

    override fun isModified(): Boolean = pendingIsActive != state.isActive

    override fun apply() {
        state.isActive = pendingIsActive
    }

    override fun reset() {
        pendingIsActive = state.isActive
    }
}
