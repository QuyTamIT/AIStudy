package com.aistudy.aistudy.playground

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R

class MemoryCardAdapter(
    private var cards: List<ConceptCard>,
    private val onCardClick: (Int) -> Unit
) : RecyclerView.Adapter<MemoryCardAdapter.CardViewHolder>() {

    fun updateCards(newCards: List<ConceptCard>) {
        this.cards = newCards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position], position)
    }

    override fun getItemCount(): Int = cards.size

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutBack: View = itemView.findViewById(R.id.layout_card_back)
        private val layoutFront: View = itemView.findViewById(R.id.layout_card_front)
        private val tvSubject: TextView = itemView.findViewById(R.id.tv_card_subject)
        private val tvText: TextView = itemView.findViewById(R.id.tv_card_text)
        private val tvCheck: TextView = itemView.findViewById(R.id.tv_matched_check)

        fun bind(card: ConceptCard, position: Int) {
            tvSubject.text = card.subject
            tvText.text = card.text

            val ctx = itemView.context
            if (card.isMatched) {
                layoutBack.visibility = View.GONE
                layoutFront.visibility = View.VISIBLE
                layoutFront.setBackgroundResource(R.drawable.bg_memory_card_matched)
                tvCheck.visibility = View.VISIBLE
                itemView.isEnabled = false
            } else if (card.isFlipped) {
                layoutBack.visibility = View.GONE
                layoutFront.visibility = View.VISIBLE
                layoutFront.setBackgroundResource(R.drawable.bg_memory_card_front)
                tvCheck.visibility = View.GONE
                itemView.isEnabled = false
            } else {
                layoutBack.visibility = View.VISIBLE
                layoutFront.visibility = View.GONE
                tvCheck.visibility = View.GONE
                itemView.isEnabled = true
            }

            itemView.setOnClickListener {
                if (!card.isFlipped && !card.isMatched) {
                    onCardClick(position)
                }
            }
        }
    }
}

