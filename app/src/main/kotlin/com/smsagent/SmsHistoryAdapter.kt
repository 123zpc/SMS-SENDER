package com.smsagent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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

    private val codeRegex = Regex("""(?<!\d)\d{4,6}(?!\d)""")

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: LinearLayout = view.findViewById(R.id.smsCardView)
        val checkbox: CheckBox = view.findViewById(R.id.smsCheckbox)
        val senderText: TextView = view.findViewById(R.id.smsSenderText)
        val codeBadgeText: TextView? = view.findViewById(R.id.codeBadgeText)
        val timeText: TextView = view.findViewById(R.id.smsTimeText)
        val bodyText: TextView = view.findViewById(R.id.smsBodyText)
        val unreadDot: View = view.findViewById(R.id.unreadDot)
        val signalBar: View = view.findViewById(R.id.signalBar)
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

        // 智能提取 4-6 位数字验证码并高亮显示 Badge
        val codeMatch = codeRegex.find(item.body)?.value
        if (!codeMatch.isNullOrBlank() && holder.codeBadgeText != null) {
            holder.codeBadgeText.text = "验证码 $codeMatch"
            holder.codeBadgeText.visibility = View.VISIBLE
        } else {
            holder.codeBadgeText?.visibility = View.GONE
        }

        applySelectionVisual(holder, item.isSelected)

        val toggleSelection = {
            item.isSelected = !item.isSelected
            holder.checkbox.isChecked = item.isSelected
            applySelectionVisual(holder, item.isSelected)
            onSelectionChanged(getSelectedItems().size)
        }

        holder.checkbox.setOnClickListener { toggleSelection() }
        holder.itemView.setOnClickListener { toggleSelection() }
    }

    override fun getItemCount(): Int = items.size

    private fun applySelectionVisual(holder: ViewHolder, selected: Boolean) {
        if (selected) {
            holder.cardView.setBackgroundResource(R.drawable.history_item_background_selected)
        } else {
            holder.cardView.setBackgroundResource(R.drawable.history_item_background)
        }
    }

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
