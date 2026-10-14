package com.example.finalproject1.ui.aifinder

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import com.example.finalproject1.R
import com.example.finalproject1.data.model.ChatMessage
import com.example.finalproject1.data.model.MusicEntityType
import com.example.finalproject1.data.model.ResolvedMusicEntity
import com.example.finalproject1.databinding.ItemChatAssistantBinding
import com.example.finalproject1.databinding.ItemChatResultsBinding
import com.example.finalproject1.databinding.ItemChatUserBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ChatAdapter(
    private val onRetry: () -> Unit,
    private val onClarification: (ResolvedMusicEntity) -> Unit,
    private val onOpenEntity: (ResolvedMusicEntity) -> Unit,
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ChatMessage.UserText -> USER
        is ChatMessage.Loading -> LOADING
        is ChatMessage.EntityResults, is ChatMessage.Recommendations, is ChatMessage.Clarification -> RESULTS
        else -> ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            USER -> TextHolder(ItemChatUserBinding.inflate(inflater, parent, false).messageText)
            ASSISTANT -> TextHolder(ItemChatAssistantBinding.inflate(inflater, parent, false).messageText)
            LOADING -> LoadingHolder(ProgressBar(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(8) }
                contentDescription = context.getString(R.string.assistant_typing)
            })
            else -> ResultsHolder(ItemChatResultsBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatMessage.UserText -> (holder as TextHolder).bind(item.text)
            is ChatMessage.AssistantText -> (holder as TextHolder).bind(item.text)
            is ChatMessage.Error -> (holder as TextHolder).bind(
                if (item.canRetry) "${item.message}\n\nTap here to retry." else item.message,
                if (item.canRetry) onRetry else null,
            )
            is ChatMessage.Loading -> Unit
            is ChatMessage.Clarification -> (holder as ResultsHolder).bindClarification(item)
            is ChatMessage.EntityResults -> (holder as ResultsHolder).bindEntities(item.introduction, item.entities, emptyMap())
            is ChatMessage.Recommendations -> (holder as ResultsHolder).bindEntities(
                item.introduction,
                item.recommendations.map { it.entity },
                item.recommendations.associate { it.entity.id to it.reason },
            )
        }
    }

    private class TextHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
        fun bind(value: String, click: (() -> Unit)? = null) {
            text.text = value
            text.setOnClickListener(if (click == null) null else View.OnClickListener { click() })
            text.isClickable = click != null
        }
    }

    private class LoadingHolder(view: View) : RecyclerView.ViewHolder(view)

    private inner class ResultsHolder(private val binding: ItemChatResultsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindClarification(message: ChatMessage.Clarification) {
            clear(message.question)
            message.options.forEach { option ->
                binding.resultContainer.addView(MaterialButton(binding.root.context).apply {
                    text = option.label
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setOnClickListener { onClarification(option.entity) }
                })
            }
        }

        fun bindEntities(
            introduction: String,
            entities: List<ResolvedMusicEntity>,
            reasons: Map<String, String>,
        ) {
            clear(introduction)
            entities.forEach { entity -> binding.resultContainer.addView(entityCard(entity, reasons[entity.id])) }
        }

        private fun clear(introduction: String) {
            binding.introduction.text = introduction
            binding.resultContainer.removeAllViews()
        }

        private fun entityCard(entity: ResolvedMusicEntity, reason: String?): View {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density
            return MaterialCardView(context).apply {
                radius = 16 * density
                strokeWidth = dp(1)
                setStrokeColor(context.getColor(R.color.cyan))
                setCardBackgroundColor(context.getColor(R.color.surface_card))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12))
                    val imageUrl = when (entity) {
                        is ResolvedMusicEntity.ResolvedBand -> entity.imageUrl
                        is ResolvedMusicEntity.ResolvedMember -> entity.imageUrl
                        is ResolvedMusicEntity.ResolvedAlbum -> entity.imageUrl
                        else -> null
                    }
                    addView(ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(12) }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription = "Artwork for ${entity.name}"
                        setImageResource(R.drawable.album_placeholder)
                        imageUrl?.let { load(it) { crossfade(true); error(R.drawable.album_placeholder) } }
                    })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        addView(TextView(context).apply {
                            text = entity.type.name
                            setTextColor(context.getColor(R.color.cyan))
                            textSize = 11f
                            setTypeface(typeface, Typeface.BOLD)
                        })
                        addView(TextView(context).apply {
                            text = entity.name
                            setTextColor(context.getColor(R.color.text_primary))
                            textSize = 18f
                            setTypeface(typeface, Typeface.BOLD)
                        })
                        val detail = details(entity)
                        if (detail.isNotBlank()) addView(TextView(context).apply {
                            text = detail
                            setTextColor(context.getColor(R.color.text_secondary))
                        })
                        reason?.takeIf(String::isNotBlank)?.let { value -> addView(TextView(context).apply {
                            text = value
                            setTextColor(context.getColor(R.color.text_primary))
                            setPadding(0, dp(6), 0, 0)
                        }) }
                    })
                })
                if (entity.type == MusicEntityType.BAND || entity.type == MusicEntityType.ALBUM) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onOpenEntity(entity) }
                }
            }
        }

        private fun details(entity: ResolvedMusicEntity): String = when (entity) {
            is ResolvedMusicEntity.ResolvedBand -> listOfNotNull(entity.countryOrArea, entity.genres.take(3).joinToString().takeIf(String::isNotBlank)).joinToString(" · ")
            is ResolvedMusicEntity.ResolvedMember -> entity.bands.joinToString { link -> "${link.bandName}${link.roles.takeIf { it.isNotEmpty() }?.joinToString(prefix = " — ").orEmpty()}${if (link.isCurrent) "" else " (former)"}" }.ifBlank { "Verified membership details unavailable" }
            is ResolvedMusicEntity.ResolvedAlbum -> listOfNotNull(entity.artistName, entity.firstReleaseDate, entity.primaryType).joinToString(" · ")
            is ResolvedMusicEntity.ResolvedSong -> listOfNotNull(entity.artistName, entity.albumName, entity.durationMillis?.let { "%d:%02d".format(it / 60_000, (it / 1_000) % 60) }).joinToString(" · ")
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }

    private fun View.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object { const val ASSISTANT = 0; const val USER = 1; const val LOADING = 2; const val RESULTS = 3 }
}
