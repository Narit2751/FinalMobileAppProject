package com.example.finalproject1.ui.banddetail

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject1.OtoNaviApplication
import com.example.finalproject1.R
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.databinding.FragmentBandDetailBinding
import com.example.finalproject1.ui.common.AlbumAdapter
import com.example.finalproject1.ui.common.BandMemberAdapter
import com.example.finalproject1.ui.common.SimpleViewModelFactory
import com.example.finalproject1.util.setJapaneseAwareText
import com.google.android.material.chip.Chip
import coil3.load
import coil3.request.crossfade
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import kotlinx.coroutines.launch

class BandDetailFragment : Fragment() {
    private var _binding: FragmentBandDetailBinding? = null
    private val binding get() = _binding!!
    private var renderedImageUrl: String? = null
    private var defaultBandNameTypeface: Typeface? = null
    private val args: BandDetailFragmentArgs by navArgs()
    private val appContainer by lazy { (requireActivity().application as OtoNaviApplication).container }
    private val viewModel: BandDetailViewModel by viewModels {
        SimpleViewModelFactory {
            BandDetailViewModel(
                args.artistId,
                appContainer.musicRepository,
                appContainer.aiService,
                appContainer.bandInfoService,
                appContainer.networkStatus::isOnline,
            )
        }
    }
    private val albumAdapter by lazy {
        AlbumAdapter { album ->
            findNavController().navigate(
                BandDetailFragmentDirections.actionBandDetailFragmentToAlbumDetailFragment(
                    album.id,
                    album.title,
                    album.primaryType,
                    album.firstReleaseDate,
                    album.imageUrl,
                    album.sourceUrl,
                )
            )
        }
    }
    private val memberAdapter by lazy { BandMemberAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentBandDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        defaultBandNameTypeface = bandName.typeface
        toolbar.navigationContentDescription = getString(R.string.navigate_back)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        albumList.layoutManager = LinearLayoutManager(requireContext())
        albumList.adapter = albumAdapter
        memberList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        memberList.adapter = memberAdapter
        retryButton.setOnClickListener { viewModel.retry() }
        albumRetryButton.setOnClickListener { viewModel.retry() }
        favoriteButton.setOnClickListener { viewModel.toggleFavorite() }
        explainButton.setOnClickListener { viewModel.summarizeBand() }
        originsButton.setOnClickListener { viewModel.summarizeOrigins() }
        summarySourceButton.setOnClickListener {
            viewModel.state.value.summarySourceUrl?.let { source ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)))
            }
        }
        askAiFinderButton.setOnClickListener {
            val name = viewModel.state.value.band?.name.orEmpty()
            findNavController().navigate(BandDetailFragmentDirections.actionBandDetailFragmentToAiFinderFragment(name))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: BandDetailUiState) = with(binding) {
        progress.isVisible = state.loading
        contentScroll.isVisible = !state.loading && state.errorMessage == null
        errorPanel.isVisible = state.errorMessage != null
        errorMessage.text = state.errorMessage
        state.band?.let(::renderBand)
        albumProgress.isVisible = state.albumLoading
        albumMessage.isVisible = state.albumError != null || (!state.albumLoading && state.albums.isEmpty())
        albumMessage.text = state.albumError ?: getString(R.string.no_albums)
        albumRetryButton.isVisible = state.albumError != null
        albumAdapter.submitList(state.albums)
        aiProgress.isVisible = state.aiLoading
        aiExplanation.isVisible = state.aiExplanation != null
        aiExplanation.text = state.aiExplanation?.let { getString(R.string.ai_explanation_format, getString(R.string.ai_disclaimer), it) }
        summarySourceButton.isVisible = state.summarySourceUrl != null
    }

    private fun renderBand(band: Band) = with(binding) {
        bandName.setJapaneseAwareText(
            band.name,
            defaultBandNameTypeface ?: bandName.typeface,
        )
        bandInitials.text = initialsFor(band.name)
        bandInitials.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            paletteFor(band.id),
        ).apply { cornerRadius = 20f * resources.displayMetrics.density }
        val imageKey = band.profileImageUrl ?: AI_PLACEHOLDER_KEY
        if (renderedImageUrl != imageKey) {
            renderedImageUrl = imageKey
            bandImage.isInvisible = true
            bandImage.background = null
            bandImage.setPadding(0, 0, 0, 0)
            bandImage.scaleType = ImageView.ScaleType.CENTER_CROP
            bandInitials.isVisible = true
            generatedImageBadge.isVisible = false
            if (band.profileImageUrl == null) {
                showGeneratedPlaceholder(band)
            } else {
                bandImage.contentDescription = getString(R.string.band_image_description, band.name)
                bandImage.load(band.profileImageUrl) {
                    crossfade(true)
                    httpHeaders(
                        NetworkHeaders.Builder()
                            .set("User-Agent", "OtoNaviJapan/1.0 university-project")
                            .build()
                    )
                    listener(
                        onSuccess = { _, _ ->
                            bandImage.isVisible = true
                            bandInitials.isVisible = false
                            generatedImageBadge.isVisible = false
                        },
                        onError = { _, _ -> showGeneratedPlaceholder(band) },
                    )
                }
            }
        }
        imageSourceButton.isVisible = band.profileImageSourceUrl != null
        imageSourceButton.setOnClickListener {
            band.profileImageSourceUrl?.let { source ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)))
            }
        }
        area.isVisible = band.area != null || band.country != null
        area.text = band.area ?: band.country
        lifespan.isVisible = band.beginDate != null
        lifespan.text = band.beginDate?.let { getString(R.string.began, it) }
        status.text = when (band.ended) { true -> getString(R.string.ended); false -> getString(R.string.active); null -> getString(R.string.status_unknown) }
        genres.removeAllViews()
        band.genres.take(8).forEach { genre -> genres.addView(Chip(requireContext()).apply { text = genre; isClickable = false }) }
        favoriteButton.text = getString(if (band.isFavorite) R.string.unfavorite else R.string.favorite)
        favoriteButton.setIconResource(if (band.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        memberAdapter.submitList(band.members)
        memberList.isVisible = band.members.isNotEmpty()
        memberMessage.isVisible = band.members.isEmpty()
    }

    private fun showGeneratedPlaceholder(band: Band) = with(binding) {
        bandImage.background = null
        bandImage.setPadding(0, 0, 0, 0)
        bandImage.scaleType = ImageView.ScaleType.CENTER_CROP
        bandImage.setImageResource(R.drawable.ai_band_placeholder)
        bandImage.isVisible = true
        bandImage.contentDescription = getString(R.string.ai_band_image_description, band.name)
        bandInitials.isVisible = false
        generatedImageBadge.isVisible = true
    }

    private fun initialsFor(name: String): String = name.trim().split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { "音" }

    private fun paletteFor(id: String): IntArray = when (kotlin.math.abs(id.hashCode()) % 4) {
        0 -> intArrayOf(0xFFFF4D5A.toInt(), 0xFF6A3DE8.toInt())
        1 -> intArrayOf(0xFF4EDCEB.toInt(), 0xFF1769AA.toInt())
        2 -> intArrayOf(0xFFFF8A4C.toInt(), 0xFFFF4D5A.toInt())
        else -> intArrayOf(0xFF14B8A6.toInt(), 0xFF3349A8.toInt())
    }

    override fun onDestroyView() {
        binding.albumList.adapter = null
        binding.memberList.adapter = null
        renderedImageUrl = null
        defaultBandNameTypeface = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val AI_PLACEHOLDER_KEY = "local:ai-band-placeholder"
    }
}
