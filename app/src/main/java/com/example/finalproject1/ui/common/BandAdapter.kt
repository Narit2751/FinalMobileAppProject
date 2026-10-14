package com.example.finalproject1.ui.common

import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalproject1.R
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.databinding.ItemBandBinding
import com.example.finalproject1.util.setJapaneseAwareText
import com.google.android.material.chip.Chip
import coil3.load
import coil3.request.crossfade
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

class BandAdapter(
    private val onOpen: (Band) -> Unit,
    private val onFavorite: (Band) -> Unit,
) : ListAdapter<Band, BandAdapter.BandViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = BandViewHolder(
        ItemBandBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: BandViewHolder, position: Int) = holder.bind(getItem(position))

    inner class BandViewHolder(private val binding: ItemBandBinding) : RecyclerView.ViewHolder(binding.root) {
        private val defaultNameTypeface = binding.name.typeface

        fun bind(band: Band) = with(binding) {
            name.setJapaneseAwareText(band.name, defaultNameTypeface)
            area.text = band.area ?: band.country ?: root.context.getString(R.string.not_available)
            initials.text = initialsFor(band.name)
            initials.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, paletteFor(band.id)).apply {
                cornerRadius = 18f * root.resources.displayMetrics.density
            }
            bandImage.contentDescription = root.context.getString(R.string.band_logo_description, band.name)
            bandImage.setBackgroundResource(R.drawable.band_logo_plate)
            val logoPadding = (7f * root.resources.displayMetrics.density).toInt()
            bandImage.setPadding(logoPadding, logoPadding, logoPadding, logoPadding)
            bandImage.scaleType = ImageView.ScaleType.FIT_CENTER
            bandImage.isInvisible = true
            initials.isVisible = true
            generatedBadge.isVisible = false
            if (band.imageUrl == null) {
                showGeneratedPlaceholder(band)
            } else {
                bandImage.load(band.imageUrl) {
                    crossfade(true)
                    httpHeaders(
                        NetworkHeaders.Builder()
                            .set("User-Agent", "OtoNaviJapan/1.0 university-project")
                            .build()
                    )
                    listener(
                        onSuccess = { _, _ ->
                            bandImage.isVisible = true
                            initials.isVisible = false
                            generatedBadge.isVisible = false
                        },
                        onError = { _, _ ->
                            showGeneratedPlaceholder(band)
                        },
                    )
                }
            }
            genres.removeAllViews()
            band.genres.take(3).forEach { genre ->
                genres.addView(Chip(root.context).apply {
                    text = genre
                    isClickable = false
                    isCheckable = false
                    textSize = 11f
                })
            }
            favoriteButton.setImageResource(if (band.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            favoriteButton.contentDescription = root.context.getString(if (band.isFavorite) R.string.unfavorite else R.string.favorite)
            root.setOnClickListener { onOpen(band) }
            favoriteButton.setOnClickListener { onFavorite(band) }
        }

        private fun ItemBandBinding.showGeneratedPlaceholder(band: Band) {
            bandImage.background = null
            bandImage.setPadding(0, 0, 0, 0)
            bandImage.scaleType = ImageView.ScaleType.CENTER_CROP
            bandImage.setImageResource(R.drawable.ai_band_placeholder)
            bandImage.isVisible = true
            bandImage.contentDescription = root.context.getString(R.string.ai_band_image_description, band.name)
            initials.isVisible = false
            generatedBadge.isVisible = true
        }
    }

    private fun initialsFor(name: String): String = name.trim().split(Regex("\\s+"))
        .filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        .ifBlank { "音" }

    private fun paletteFor(id: String): IntArray = when (kotlin.math.abs(id.hashCode()) % 4) {
        0 -> intArrayOf(0xFFFF4D5A.toInt(), 0xFF6A3DE8.toInt())
        1 -> intArrayOf(0xFF4EDCEB.toInt(), 0xFF1769AA.toInt())
        2 -> intArrayOf(0xFFFF8A4C.toInt(), 0xFFFF4D5A.toInt())
        else -> intArrayOf(0xFF14B8A6.toInt(), 0xFF3349A8.toInt())
    }

    private object Diff : DiffUtil.ItemCallback<Band>() {
        override fun areItemsTheSame(oldItem: Band, newItem: Band) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Band, newItem: Band) = oldItem == newItem
    }
}
