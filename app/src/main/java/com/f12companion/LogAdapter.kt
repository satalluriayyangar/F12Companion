package com.f12companion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.f12companion.databinding.ItemLogBinding
import com.f12companion.model.BleLogEntry
import com.f12companion.model.Direction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : ListAdapter<BleLogEntry, LogAdapter.LogViewHolder>(DiffCallback()) {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: BleLogEntry) {
            binding.tvDirection.text = if (entry.direction == Direction.TX) "TX" else "RX"
            binding.tvDirection.setTextColor(
                if (entry.direction == Direction.TX) {
                    binding.root.context.getColor(android.R.color.holo_green_dark)
                } else {
                    binding.root.context.getColor(android.R.color.holo_blue_dark)
                }
            )
            binding.tvHex.text = entry.hex
            binding.tvTime.text = timeFormat.format(Date(entry.timestamp))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<BleLogEntry>() {
        override fun areItemsTheSame(oldItem: BleLogEntry, newItem: BleLogEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.direction == newItem.direction
        }

        override fun areContentsTheSame(oldItem: BleLogEntry, newItem: BleLogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
