package com.aistudy.aistudy.answer

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aistudy.aistudy.R

/** Formats explicit code regions without changing the saved or displayed source text. */
internal object AnswerBodyRenderer {
    private val LATEX_N_COMMANDS = setOf(
        "nabla", "natural", "nearrow", "neg", "neq", "ne", "nexists",
        "ni", "noindent", "nonumber", "not", "notin", "nu", "nwarrow"
    )

    fun bind(stepView: View, source: String) {
        val prose = stepView.findViewById<TextView>(R.id.tv_answer_step_body)
        val container = stepView.findViewById<LinearLayout>(R.id.layout_answer_text_blocks)
        val blocks = AnswerTextBlocks.split(source)
        if (blocks.none { it.isCode }) {
            prose.text = StudyMathText.format(sanitizeProse(source))
            prose.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        prose.visibility = View.GONE
        container.removeAllViews()
        container.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(stepView.context)
        blocks.forEach { block ->
            val layout = if (block.isCode) R.layout.item_answer_code else R.layout.item_answer_prose
            val view = inflater.inflate(layout, container, false)
            view.findViewById<TextView>(R.id.tv_answer_text_block).text =
                if (block.isCode) block.text else StudyMathText.format(sanitizeProse(block.text))
            container.addView(view)
        }
    }

    /**
     * Converts literal escaped newlines (\r\n, \n) into actual newlines in prose,
     * while preserving:
     * - inline code in backticks (`...`)
     * - LaTeX math formulas ($...$ or $$...$$)
     * - LaTeX commands starting with \n (such as \neq, \nabla, \nu, \notin, etc.)
     * Code blocks are preserved verbatim by AnswerTextBlocks.split before reaching sanitizeProse.
     */
    fun sanitizeProse(text: String): String {
        if (!text.contains("\\n") && !text.contains("\\r")) return text
        val sb = StringBuilder(text.length)
        var i = 0
        var inInlineCode = false
        var inMath = false

        while (i < text.length) {
            val c = text[i]

            if (c == '`') {
                inInlineCode = !inInlineCode
                sb.append(c)
                i++
                continue
            }
            if (inInlineCode) {
                sb.append(c)
                i++
                continue
            }

            if (c == '$') {
                inMath = !inMath
                sb.append(c)
                i++
                continue
            }
            if (inMath) {
                sb.append(c)
                i++
                continue
            }

            if (c == '\\' && i + 1 < text.length) {
                val next = text[i + 1]
                if (next == 'r' && i + 3 < text.length && text[i + 2] == '\\' && text[i + 3] == 'n') {
                    sb.append('\n')
                    i += 4
                    continue
                }
                if (next == 'n') {
                    var j = i + 2
                    while (j < text.length && text[j].isLetter()) {
                        j++
                    }
                    val word = text.substring(i + 2, j)
                    if (word.isNotEmpty() && LATEX_N_COMMANDS.contains(word.lowercase())) {
                        sb.append(text.substring(i, j))
                        i = j
                        continue
                    } else {
                        sb.append('\n')
                        i += 2
                        continue
                    }
                }
            }

            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
