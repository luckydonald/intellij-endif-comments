package de.luckydonald.endifcomments.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(name = "EndCommentSettings", storages = [Storage("endCommentSettings.xml")])
class EndCommentSettingsState : PersistentStateComponent<EndCommentSettingsState> {

    var isActive: Boolean = true

    override fun getState(): EndCommentSettingsState = this

    override fun loadState(state: EndCommentSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): EndCommentSettingsState =
            ApplicationManager.getApplication().getService(EndCommentSettingsState::class.java)
    }
}
