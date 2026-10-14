package com.example.finalproject1.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.example.finalproject1.R
import com.example.finalproject1.data.model.Album
import com.example.finalproject1.databinding.ItemAlbumBinding
import com.example.finalproject1.util.setJapaneseAwareText

class AlbumAdapter(private val onOpen: (Album) -> Unit) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AlbumViewHolder(ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) = holder.bind(getItem(position))
    inner class AlbumViewHolder(private val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
        private val defaultTitleTypeface = binding.title.typeface

        fun bind(album: Album) = with(binding) {
            title.setJapaneseAwareText(album.title, defaultTitleTypeface)
            meta.text = listOfNotNull(album.primaryType, album.firstReleaseDate).joinToString(" • ").ifBlank { root.context.getString(R.string.not_available) }
            cover.contentDescription = root.context.getString(R.string.album_art_description, album.title)
            cover.setImageResource(R.drawable.album_placeholder)
            album.coverUrl?.let { cover.load(it) }
            root.setOnClickListener { onOpen(album) }
        }
    }
    private object Diff : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Album, newItem: Album) = oldItem == newItem
    }
}
