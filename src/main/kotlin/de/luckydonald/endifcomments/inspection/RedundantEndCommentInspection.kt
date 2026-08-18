package de.luckydonald.endifcomments.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import de.luckydonald.endifcomments.model.END_COMMENT_LINE_REGEX
import de.luckydonald.endifcomments.settings.EndCommentSettingsState

/**
 * Flags a real `# end <keyword>` comment as redundant: this plugin already renders that marker as
 * a virtual overlay, so a hand-written one is stale friction, not documentation. The quick-fix below
 * is the only place this plugin ever mutates file text, and it only ever *removes* a marker — it
 * never inserts one.
 */
class RedundantEndCommentInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!EndCommentSettingsState.getInstance().isActive) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitComment(comment: PsiComment) {
                if (!END_COMMENT_LINE_REGEX.matches(comment.text.trim())) return

                holder.registerProblem(
                    comment,
                    "Redundant explicit block-ending comment — the Explicit Block Endings plugin already shows this virtually",
                    // LIKE_DEPRECATED strikes the line through, which reads as clearly distinct from
                    // the plugin's own virtual inlay (plain comment-colored text) — LIKE_UNUSED_SYMBOL
                    // looked almost identical to the inlay and made real vs. virtual hard to tell apart.
                    ProblemHighlightType.LIKE_DEPRECATED,
                    RemoveEndCommentQuickFix,
                )
            }
        }
    }
}

object RemoveEndCommentQuickFix : LocalQuickFix {

    override fun getFamilyName(): String = "Remove redundant end-comment"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val comment = descriptor.psiElement as? PsiComment ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(comment.containingFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, familyName, null, {
            val line = document.getLineNumber(comment.textRange.startOffset)
            val lineStart = document.getLineStartOffset(line)
            val deleteEnd = if (line + 1 < document.lineCount) {
                document.getLineStartOffset(line + 1)
            } else {
                document.getLineEndOffset(line)
            }
            document.deleteString(lineStart, deleteEnd)
        })
    }
}
