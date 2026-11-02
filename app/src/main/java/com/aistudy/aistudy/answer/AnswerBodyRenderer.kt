package com.aistudy.aistudy.answer

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aistudy.aistudy.R

/** Formats explicit code regions without changing the saved or displayed source text. */
internal object AnswerBodyRenderer {
    fun bind(stepView: View, source: String) {
        val prose = stepView.findViewById<TextView>(R.id.tv_answer_step_body)
        val container = stepView.findViewById<LinearLayout>(R.id.layout_answer_text_blocks)
        val blocks = AnswerTextBlocks.split(source)
        if (blocks.none { it.isCode }) {
            prose.text = source
            return
        }

        prose.visibility = View.GONE
        container.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(stepView.context)
        blocks.forEach { block ->
            val layout = if (block.isCode) R.layout.item_answer_code else R.layout.item_answer_prose
            val view = inflater.inflate(layout, container, false)
            view.findViewById<TextView>(R.id.tv_answer_text_block).text = block.text
            container.addView(view)
        }
    }
}
