package com.ultra.autodetector.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.databinding.ItemUserRowBinding

class UserAdapter(
    private val onGrant: (User, Int?) -> Unit,
    private val onReject: (User) -> Unit,
) : RecyclerView.Adapter<UserAdapter.Holder>() {
    private var items = emptyList<User>()

    fun submit(value: List<User>) { items = value; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemUserRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class Holder(private val binding: ItemUserRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.userEmail.text = user.email
            binding.userStatus.text = user.licenseStatus.wireValue.uppercase()
            binding.userRemaining.text = user.remainingLabel()
            binding.btnOneDay.setOnClickListener { onGrant(user, 1) }
            binding.btnTwoDays.setOnClickListener { onGrant(user, 2) }
            binding.btnThreeDays.setOnClickListener { onGrant(user, 3) }
            binding.btnLifetime.setOnClickListener { onGrant(user, null) }
            binding.btnReject.setOnClickListener { onReject(user) }
        }
    }
}