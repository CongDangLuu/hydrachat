package com.hydrachat.app.bubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.domain.StoreCatalog
import com.hydrachat.app.domain.StoreItem
import com.hydrachat.app.ui.ChatBubble
import com.hydrachat.app.ui.PersonaAvatar

const val BUBBLE_BOX_DP = 68
const val DISMISS_TARGET_DP = 64
/** How long the praise stays visible before the overlay or system bubble closes itself. */
const val AUTO_COLLAPSE_MS = 1_500L
private const val AVATAR_DP = 60
private const val DISMISS_ACTIVE_SCALE = 1.25f

enum class PanelPhase { Asking, Answered }

/** Observable state shared by the overlay windows of one reminder. */
class BubbleUiState {
    var nudge by mutableStateOf(NudgeContent("", ""))
    var persona by mutableStateOf(Persona.GenZ)
    var skin by mutableStateOf<StoreItem>(StoreCatalog.skin(StoreCatalog.DEFAULT_SKIN_ID))
    var totalMl by mutableIntStateOf(0)
    var targetMl by mutableIntStateOf(1)
    var cupMl by mutableIntStateOf(0)
    var phase by mutableStateOf(PanelPhase.Asking)
    var answerText by mutableStateOf("")
    var isAnswerFromAi by mutableStateOf(false)
    var hasUnread by mutableStateOf(true)
    var isOverDismiss by mutableStateOf(false)
}

@Composable
fun BubbleHead(state: BubbleUiState) {
    Box(modifier = Modifier.size(BUBBLE_BOX_DP.dp), contentAlignment = Alignment.Center) {
        PersonaAvatar(persona = state.persona, skin = state.skin, size = AVATAR_DP.dp)
        if (state.hasUnread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)),
                contentAlignment = Alignment.Center,
            ) {
                Text("1", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BubblePreview(state: BubbleUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(6.dp).widthIn(max = 240.dp),
    ) {
        Text(
            text = state.nudge.chatText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun DismissTarget(state: BubbleUiState) {
    val scale by animateFloatAsState(if (state.isOverDismiss) DISMISS_ACTIVE_SCALE else 1f, label = "dismissScale")
    Box(modifier = Modifier.size((DISMISS_TARGET_DP * DISMISS_ACTIVE_SCALE).dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(DISMISS_TARGET_DP.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(if (state.isOverDismiss) Color(0xFFE53935) else Color(0xB3000000)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = Color.White, fontSize = 24.sp)
        }
    }
}

@Composable
fun ChatPanel(
    state: BubbleUiState,
    onDrink: (Int) -> Unit,
    onSnooze: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanelHeader(state, onClose)
            if (state.phase == PanelPhase.Asking) {
                ChatBubble(text = state.nudge.chatText, isFromUser = false, isFromAi = state.nudge.isFromAi)
                QuickReplies(state, onDrink, onSnooze)
            } else {
                ChatBubble(text = state.answerText, isFromUser = false, isFromAi = state.isAnswerFromAi)
                Text("👏💧", fontSize = 44.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun PanelHeader(state: BubbleUiState, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PersonaAvatar(persona = state.persona, skin = state.skin, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.persona.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                "${state.totalMl} / ${state.targetMl} ml",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (state.totalMl.toFloat() / state.targetMl).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
        TextButton(onClick = onClose) { Text("Ẩn") }
    }
}

@Composable
private fun QuickReplies(state: BubbleUiState, onDrink: (Int) -> Unit, onSnooze: () -> Unit) {
    val cupLabel = state.nudge.quickReplyText.ifBlank { "🥤 Đã uống" }
    Button(onClick = { onDrink(state.cupMl) }, modifier = Modifier.fillMaxWidth()) {
        Text("$cupLabel · ${state.cupMl}ml", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onDrink(HydrationMath.SIP_ML) }, modifier = Modifier.weight(1f)) {
            Text("💧 1 ngụm (${HydrationMath.SIP_ML}ml)", maxLines = 1, fontSize = 13.sp)
        }
        OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
            Text("🤫 Họp, ${HydrationMath.SNOOZE_MINUTES}p nữa", maxLines = 1, fontSize = 13.sp)
        }
    }
}
