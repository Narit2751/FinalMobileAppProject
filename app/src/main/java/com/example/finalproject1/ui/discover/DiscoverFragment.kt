package com.example.finalproject1.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.example.finalproject1.databinding.FragmentDiscoverBinding
import com.example.finalproject1.ui.common.BandAdapter
import com.example.finalproject1.ui.common.SimpleViewModelFactory
import com.example.finalproject1.ui.common.UiState
import kotlinx.coroutines.launch

class DiscoverFragment : Fragment() {
    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private val appContainer by lazy { (requireActivity().application as OtoNaviApplication).container }
    private val viewModel: DiscoverViewModel by viewModels {
        SimpleViewModelFactory { DiscoverViewModel(appContainer.musicRepository, appContainer.networkStatus::isOnline) }
    }
    private val adapter by lazy {
        BandAdapter(
            onOpen = { findNavController().navigate(DiscoverFragmentDirections.actionDiscoverFragmentToBandDetailFragment(it.id)) },
            onFavorite = viewModel::toggleFavorite,
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        bandList.layoutManager = LinearLayoutManager(requireContext())
        bandList.adapter = adapter
        swipeRefresh.setColorSchemeResources(R.color.vermilion, R.color.cyan)
        swipeRefresh.setOnRefreshListener(viewModel::refresh)
        retryButton.setOnClickListener { viewModel.refresh() }
        searchButton.setOnClickListener { viewModel.refresh() }
        searchInput.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { viewModel.refresh(); true } else false
        }
        genreGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val genre = when (checkedIds.firstOrNull()) {
                R.id.genre_pop -> "pop"; R.id.genre_metal -> "metal"; R.id.genre_punk -> "punk"
                R.id.genre_jazz -> "jazz"; R.id.genre_electronic -> "electronic"; else -> "rock"
            }
            viewModel.onGenreChanged(genre)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: UiState<List<Band>>) = with(binding) {
        progress.isVisible = state is UiState.Loading && !swipeRefresh.isRefreshing
        if (state !is UiState.Loading) swipeRefresh.isRefreshing = false
        bandList.isVisible = state is UiState.Content
        statePanel.isVisible = state is UiState.Empty || state is UiState.Error
        when (state) {
            is UiState.Content -> adapter.submitList(state.data)
            UiState.Empty -> { stateMessage.setText(R.string.no_bands); retryButton.isVisible = false }
            is UiState.Error -> { stateMessage.text = state.message; retryButton.isVisible = true }
            UiState.Loading -> Unit
        }
    }

    override fun onDestroyView() { binding.bandList.adapter = null; _binding = null; super.onDestroyView() }
}
