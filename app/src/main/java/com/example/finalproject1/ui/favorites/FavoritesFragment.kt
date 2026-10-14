package com.example.finalproject1.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject1.OtoNaviApplication
import com.example.finalproject1.R
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.databinding.FragmentFavoritesBinding
import com.example.finalproject1.ui.common.BandAdapter
import com.example.finalproject1.ui.common.SimpleViewModelFactory
import com.example.finalproject1.ui.common.UiState
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val appContainer by lazy { (requireActivity().application as OtoNaviApplication).container }
    private val viewModel: FavoritesViewModel by viewModels { SimpleViewModelFactory { FavoritesViewModel(appContainer.musicRepository) } }
    private val adapter by lazy {
        BandAdapter(
            onOpen = { findNavController().navigate(FavoritesFragmentDirections.actionFavoritesFragmentToBandDetailFragment(it.id)) },
            onFavorite = ::removeWithUndo,
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        favoriteList.layoutManager = LinearLayoutManager(requireContext())
        favoriteList.adapter = adapter
        exploreButton.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.discoverFragment
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: UiState<List<Band>>) = with(binding) {
        progress.isVisible = state is UiState.Loading
        favoriteList.isVisible = state is UiState.Content
        emptyPanel.isVisible = state is UiState.Empty
        if (state is UiState.Content) adapter.submitList(state.data)
    }

    private fun removeWithUndo(band: Band) {
        viewModel.remove(band)
        Snackbar.make(binding.root, getString(R.string.removed_favorite, band.name), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { viewModel.restore(band) }.show()
    }

    override fun onDestroyView() { binding.favoriteList.adapter = null; _binding = null; super.onDestroyView() }
}
