package com.example.finalproject1.ui.common

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.crossfade
import com.example.finalproject1.R
import com.example.finalproject1.data.model.BandMember
import com.example.finalproject1.databinding.ItemBandMemberBinding
import com.example.finalproject1.util.setJapaneseAwareText

class BandMemberAdapter : ListAdapter<BandMember, BandMemberAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemBandMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemBandMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        private val defaultNameTypeface = binding.name.typeface

        fun bind(member: BandMember) = with(binding) {
            name.setJapaneseAwareText(member.name, defaultNameTypeface)
            role.text = when {
                member.roles.isNotEmpty() && member.isCurrent -> member.roles.take(2).joinToString()
                member.roles.isNotEmpty() -> root.context.getString(
                    R.string.former_member_role,
                    member.roles.take(2).joinToString(),
                )
                member.isCurrent -> root.context.getString(R.string.band_member_role)
                else -> root.context.getString(R.string.former_member)
            }
            initials.text = member.name.trim().take(2).uppercase()
            initials.isVisible = true
            photo.isInvisible = true
            photo.contentDescription = root.context.getString(R.string.member_photo_description, member.name)
            member.imageUrl?.let { url ->
                photo.load(url) {
                    crossfade(true)
                    httpHeaders(
                        NetworkHeaders.Builder()
                            .set("User-Agent", "OtoNaviJapan/1.0 university-project")
                            .build()
                    )
                    listener(
                        onSuccess = { _, _ ->
                            photo.isVisible = true
                            initials.isVisible = false
                        },
                        onError = { _, _ ->
                            photo.isInvisible = true
                            initials.isVisible = true
                        },
                    )
                }
            }
            root.setOnClickListener {
                member.imageSourceUrl?.let { source ->
                    root.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)))
                }
            }
            root.isClickable = member.imageSourceUrl != null
        }
    }

    private object Diff : DiffUtil.ItemCallback<BandMember>() {
        override fun areItemsTheSame(oldItem: BandMember, newItem: BandMember) =
            oldItem.artistId == newItem.artistId

        override fun areContentsTheSame(oldItem: BandMember, newItem: BandMember) = oldItem == newItem
    }
}
