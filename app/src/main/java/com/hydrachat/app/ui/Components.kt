package com.hydrachat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrachat.app.ai.AI_SOURCE_LABEL
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.domain.StoreItem

private const val AVATAR_EMOJI_RATIO = 0.48f
private const val RING_RATIO = 0.06f

fun ringBrush(skin: StoreItem): Brush {
    val colors = skin.ringColors.map { Color(it) }
    return if (colors.size >= 2) Brush.linearGradient(colors) else Brush.linearGradient(listOf(Color(0xFF42A5F5), Color(0xFF1E88E5)))
}

@Composable
fun PersonaAvatar(persona: Persona, skin: StoreItem, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(size * RING_RATIO, ringBrush(skin), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = persona.emoji, fontSize = (size.value * AVATAR_EMOJI_RATIO).sp)
    }
}

@Composable
fun ChatBubble(
    text: String,
    isFromUser: Boolean,
    modifier: Modifier = Modifier,
    quest: String? = null,
    isFromAi: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isFromUser) colors.primary else colors.surfaceVariant,
            contentColor = if (isFromUser) colors.onPrimary else colors.onSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isFromUser) 18.dp else 4.dp,
                bottomEnd = if (isFromUser) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            val body = if (quest.isNullOrBlank()) text else "$text\n🎯 $quest"
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = body, fontSize = 15.sp)
                if (isFromAi) Text(AI_SOURCE_LABEL, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}
