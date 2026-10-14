package com.example.finalproject1.ui.albumdetail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import com.example.finalproject1.R
import com.example.finalproject1.databinding.FragmentAlbumDetailBinding
import com.example.finalproject1.util.setJapaneseAwareText

class AlbumDetailFragment : Fragment() {
    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!
    private val args: AlbumDetailFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        toolbar.navigationContentDescription = getString(R.string.navigate_back)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        title.setJapaneseAwareText(args.title, title.typeface)
        type.isVisible = !args.albumType.isNullOrBlank()
        type.text = args.albumType?.let { getString(R.string.album_type, it) }
        date.isVisible = !args.firstReleaseDate.isNullOrBlank()
        date.text = args.firstReleaseDate?.let { getString(R.string.first_released, it) }
        cover.contentDescription = getString(R.string.album_art_description, args.title)
        cover.setImageResource(R.drawable.album_placeholder)
        artworkStatus.isVisible = args.imageUrl.isNullOrBlank()
        args.imageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
            cover.load(imageUrl) {
                listener(
                    onSuccess = { _, _ -> artworkStatus.isVisible = false },
                    onError = { _, _ -> artworkStatus.isVisible = true },
                )
            }
        }
        musicbrainzButton.isVisible = !args.sourceUrl.isNullOrBlank()
        musicbrainzButton.setOnClickListener {
            args.sourceUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
