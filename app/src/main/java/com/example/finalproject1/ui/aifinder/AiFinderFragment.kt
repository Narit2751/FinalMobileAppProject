package com.example.finalproject1.ui.aifinder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
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
import com.example.finalproject1.data.model.ChatUiState
import com.example.finalproject1.data.model.MusicEntityType
import com.example.finalproject1.data.model.ResolvedMusicEntity
import com.example.finalproject1.databinding.FragmentAiFinderBinding
import com.example.finalproject1.ui.common.SimpleViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class AiFinderFragment : Fragment() {
    private var _binding: FragmentAiFinderBinding? = null
    private val binding get() = _binding!!
    private val args: AiFinderFragmentArgs by navArgs()
    private val appContainer by lazy { (requireActivity().application as OtoNaviApplication).container }
    private val viewModel: AiFinderViewModel by viewModels {
        SimpleViewModelFactory { AiFinderViewModel(appContainer.entityResolver, appContainer.aiService, appContainer.networkStatus::isOnline) }
    }
    private val adapter by lazy {
        ChatAdapter(viewModel::retry, viewModel::chooseClarification, ::openEntity)
    }
    private var renderingInput = false
    private var renderingRecommendations = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentAiFinderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        chatList.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        chatList.adapter = adapter
        messageInput.doAfterTextChanged { if (!renderingInput) viewModel.updateInput(it?.toString().orEmpty()) }
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND && sendButton.isEnabled) { viewModel.submit(); true } else false
        }
        sendButton.setOnClickListener { viewModel.submit() }
        recommendationCategoryGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (!renderingRecommendations) {
                viewModel.selectRecommendationCategory(
                    when (checkedIds.firstOrNull()) {
                        R.id.category_band -> MusicEntityType.BAND
                        R.id.category_song -> MusicEntityType.SONG
                        else -> null
                    }
                )
            }
        }
        vibeChips().forEach { (chip, vibe) ->
            chip.setOnCheckedChangeListener { _, checked ->
                if (!renderingRecommendations) viewModel.setRecommendationVibe(vibe, checked)
            }
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_new_chat) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.new_chat)
                    .setMessage(R.string.new_chat_confirmation)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.new_chat) { _, _ -> viewModel.resetConversation() }
                    .show()
                true
            } else false
        }
        if (savedInstanceState == null && args.prefillBandName.isNotBlank()) {
            viewModel.updateInput(getString(R.string.related_band_request, args.prefillBandName))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: ChatUiState) = with(binding) {
        adapter.submitList(state.messages) {
            if (state.messages.isNotEmpty()) chatList.scrollToPosition(state.messages.lastIndex)
        }
        if (messageInput.text?.toString() != state.inputText) {
            renderingInput = true
            messageInput.setText(state.inputText)
            messageInput.setSelection(state.inputText.length)
            renderingInput = false
        }
        messageInputLayout.hint = getString(R.string.chat_hint)
        sendButton.isEnabled = state.canSend
        messageInput.isEnabled = !state.isLoading
        renderingRecommendations = true
        when (state.selectedEntityType) {
            MusicEntityType.BAND -> recommendationCategoryGroup.check(R.id.category_band)
            MusicEntityType.SONG -> recommendationCategoryGroup.check(R.id.category_song)
            else -> recommendationCategoryGroup.clearCheck()
        }
        vibeChips().forEach { (chip, vibe) -> chip.isChecked = vibe in state.selectedKeywords }
        recommendationCategoryGroup.isEnabled = !state.isLoading
        categoryBand.isEnabled = !state.isLoading
        categorySong.isEnabled = !state.isLoading
        vibeChips().forEach { (chip, _) -> chip.isEnabled = !state.isLoading }
        renderingRecommendations = false
    }

    private fun vibeChips(): List<Pair<Chip, String>> = with(binding) {
        listOf(
            vibeEnergetic to getString(R.string.mood_energetic),
            vibeRelaxing to getString(R.string.mood_relaxing),
            vibeEmotional to getString(R.string.mood_emotional),
            vibeDark to getString(R.string.mood_dark),
            vibeHappy to getString(R.string.mood_happy),
            vibeFocused to getString(R.string.mood_focused),
        )
    }

    private fun openEntity(entity: ResolvedMusicEntity) {
        when (entity) {
            is ResolvedMusicEntity.ResolvedBand -> findNavController().navigate(
                AiFinderFragmentDirections.actionAiFinderFragmentToBandDetailFragment(entity.musicBrainzId ?: entity.id)
            )
            is ResolvedMusicEntity.ResolvedAlbum -> findNavController().navigate(
                AiFinderFragmentDirections.actionAiFinderFragmentToAlbumDetailFragment(
                    entity.id, entity.name, entity.primaryType, entity.firstReleaseDate, entity.imageUrl,
                    "https://musicbrainz.org/release-group/${entity.id}",
                )
            )
            else -> Unit
        }
    }

    override fun onDestroyView() {
        binding.chatList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
