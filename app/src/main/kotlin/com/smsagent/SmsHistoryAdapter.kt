package com.smsagent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.smsagent.util.TimeFormatter

data class SmsHistoryItem(
    val id: Long,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean,
    var isSelected: Boolean = false
)

class SmsHistoryAdapter(
    private var items: List<SmsHistoryItem>,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<SmsHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.smsCardView)
        val checkbox: CheckBox = view.findViewById(R.id.smsCheckbox)
        val senderText: TextView = view.findViewById(R.id.smsSenderText)
        val timeText: TextView = view.findViewById(R.id.smsTimeText)
        val bodyText: TextView = view.findViewById(R.id.smsBodyText)
        val unreadDot: View = view.findViewById(R.id.unreadDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.senderText.text = item.sender
        holder.timeText.text = TimeFormatter.format(item.timestamp)
        holder.bodyText.text = item.body
        holder.checkbox.isChecked = item.isSelected
        holder.unreadDot.visibility = if (item.isRead) View.GONE else View.VISIBLE

        val context = holder.itemView.context
        val seedColor = context.getColor(R.color.seed)
        val outlineColor = context.getColor(R.color.brand_outline)
        val containerColor = context.getColor(R.color.brand_surface_container)
        val selectedCardBg = context.getColor(R.color.header_panel_start)

        if (item.isSelected) {
            holder.cardView.strokeColor = seedColor
            holder.cardView.setCardBackgroundColor(selectedCardBg)
        } else {
            holder.cardView.strokeColor = outlineColor
            holder.cardView.setCardBackgroundColor(containerColor)
        }

        val toggleSelection = {
            item.isSelected = !item.isSelected
            holder.checkbox.isChecked = item.isSelected
            if (item.isSelected) {
                holder.cardView.strokeColor = seedColor
                holder.cardView.setCardBackgroundColor(selectedCardBg)
            } else {
                holder.cardView.strokeColor = outlineColor
                holder.cardView.setCardBackgroundColor(containerColor)
            }
            onSelectionChanged(getSelectedItems().size)
        }

        holder.checkbox.setOnClickListener { toggleSelection() }
        holder.itemView.setOnClickListener { toggleSelection() }
    }

    override fun getItemCount(): Int = items.size

    fun getSelectedItems(): List<SmsHistoryItem> {
        return items.filter { it.isSelected }
    }

    fun selectAll(select: Boolean) {
        items.forEach { it.isSelected = select }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedItems().size)
    }

    fun updateItems(newItems: List<SmsHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
        onSelectionChanged(getSelectedItems().size)
    }
}
