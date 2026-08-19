package de.luckydonald.endifcomments.inlay

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import de.luckydonald.endifcomments.model.EndCommentScanner
import de.luckydonald.endifcomments.model.EndMarker
import de.luckydonald.endifcomments.settings.EndCommentSettingsState

private val INLAYS_KEY: Key<MutableList<Inlay<*>>> = Key.create("de.luckydonald.endifcomments.INLAYS")
private val MARKERS_KEY: Key<List<EndMarker>> = Key.create("de.luckydonald.endifcomments.MARKERS")

/**
 * Recomputes the plugin's virtual `# end <keyword>` block inlays for one editor, every time the
 * platform reruns highlighting passes (i.e. on every document/PSI change) — the same mechanism the
 * daemon uses to keep other inline annotations in sync, so no manual document/PSI listener is needed.
 */
class EndCommentPass(
    project: Project,
    private val editor: Editor,
    private val file: PsiFile,
) : TextEditorHighlightingPass(project, editor.document, false) {

    private var computedMarkers: List<EndMarker> = emptyList()

    override fun doCollectInformation(progress: ProgressIndicator) {
        computedMarkers = if (EndCommentSettingsState.getInstance().isActive) {
            EndCommentScanner.visibleMarkers(file, editor.document)
        } else {
            emptyList()
        }
    }

    override fun doApplyInformationToEditor() {
        // `EndMarker.statement` compares by PSI identity, so this only short-circuits passes that
        // rerun without a reparse (e.g. caret moves); any real edit reparses the file and falls
        // through to a full dispose-and-recreate below, which is fine — it just means the "skip
        // stale/add missing" fine-grained diff wasn't worth the extra complexity for v1.
        val previous = editor.getUserData(MARKERS_KEY)
        if (previous == computedMarkers) return

        editor.getUserData(INLAYS_KEY)?.forEach { it.dispose() }

        val style = EndCommentInlayStyle.fromSettings(EndCommentSettingsState.getInstance())
        val newInlays: List<Inlay<*>> = computedMarkers.mapNotNull { marker ->
            editor.inlayModel.addBlockElement(
                marker.anchorOffset,
                /* relatesToPrecedingText = */ true,
                /* showAbove = */ false,
                /* priority = */ 0,
                EndCommentInlayRenderer(marker.text, marker.indentColumn, style),
            )
        }

        editor.putUserData(INLAYS_KEY, newInlays.toMutableList())
        editor.putUserData(MARKERS_KEY, computedMarkers)
    }
}
