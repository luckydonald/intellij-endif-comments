package de.luckydonald.endifcomments.model

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyForStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.PyTryExceptStatement
import com.jetbrains.python.psi.PyWhileStatement
import com.jetbrains.python.psi.PyWithStatement
import com.intellij.psi.PsiElement

/**
 * The block-opening statements from `ai/skills/code-style/references/py.md`'s
 * "Explicit block endings" section, and the keyword their `# end <keyword>` marker uses.
 *
 * `PyMatchStatement`'s last `PyCaseClause` child is handled separately (see [EndCommentScanner]) —
 * it is the one construct that gets two stacked markers (`# end case` then `# end match`).
 */
val ALL_END_KEYWORDS: List<String> = listOf("if", "with", "for", "while", "def", "class", "try", "match", "case")

fun endKeywordFor(element: PsiElement): String? = when (element) {
    is PyIfStatement -> "if"
    is PyWithStatement -> "with"
    is PyForStatement -> "for"
    is PyWhileStatement -> "while"
    is PyFunction -> "def"
    is PyClass -> "class"
    is PyTryExceptStatement -> "try"
    is PyMatchStatement -> "match"
    else -> null
}
