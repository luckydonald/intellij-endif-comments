package de.luckydonald.endifcomments.model

import com.intellij.psi.PsiElement

/**
 * One `# end <keyword>` marker that belongs right after [statement]'s last line.
 *
 * [anchorOffset] is the offset (end of that last line) markers for the same source line share —
 * when several markers share an [anchorOffset], sort by descending [indentColumn] to stack the
 * innermost block's marker directly under the code, with shallower ones below it.
 */
data class EndMarker(
    val statement: PsiElement,
    val text: String,
    val anchorOffset: Int,
    val indentColumn: Int,
)
