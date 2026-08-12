package com.ultra.autodetector.ui.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ultra.autodetector.data.model.Template
import com.ultra.autodetector.databinding.ItemTemplateRowBinding

class TemplateAdapter(private val onDelete: (String) -> Unit) : RecyclerView.Adapter<TemplateAdapter.Holder>() {
    private var items = emptyList<Template>()
    fun submit(value: List<Template>) { items = value; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTemplateRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class Holder(private val binding: ItemTemplateRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(template: Template) {
            binding.templateName.text = template.name
            binding.templateDescription.text = template.description
            binding.templateImage.setImageBitmap(BitmapFactory.decodeFile(template.filePath))
            binding.btnDelete.setOnClickListener { onDelete(template.templateId) }
        }
    }
}