package com.hydrachat.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrachat.app.data.UserSettings
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.ItemKind
import com.hydrachat.app.domain.StoreCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val reminderTimeFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM")

private const val STAGE_SPROUT = 0.25f
private const val STAGE_LEAFY = 0.5f
private const val STAGE_POTTED = 0.75f

/** Tab 1: today's conversation, the water "energy bar" and the growing plant. */
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logs by viewModel.todayLogs.collectAsStateWithLifecycle()
    val chat by viewModel.todayChat.collectAsStateWithLifecycle()
    val totalMl = logs.sumOf { it.amountMl }
    val listState = rememberLazyListState()
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ProgressCard(settings, totalMl)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (chat.isEmpty()) {
                item { ChatBubble("Hôm nay chưa có tin nhắn nào. Tới giờ mình sẽ nhắc bạn nha!", isFromUser = false) }
            }
            items(chat, key = { it.id }) { 
                ChatBubble(text = it.text, isFromUser = it.isFromUser, isFromAi = it.isFromAi)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Button(onClick = { viewModel.logDrink(settings.cupMl) }, modifier = Modifier.weight(1f)) {
                Text("🥤 ${settings.cupMl}ml", maxLines = 1)
            }
            FilledTonalButton(onClick = { viewModel.logDrink(HydrationMath.SIP_ML) }, modifier = Modifier.weight(1f)) {
                Text("💧 ${HydrationMath.SIP_ML}ml", maxLines = 1)
            }
            OutlinedButton(onClick = { viewModel.remindInChat() }, modifier = Modifier.weight(1f)) {
                Text("💬 Thử", maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProgressCard(settings: UserSettings, totalMl: Int) {
    val progress = (totalMl.toFloat() / settings.targetMl.coerceAtLeast(1)).coerceIn(0f, 1f)
    val decorations = StoreCatalog.items
        .filter { it.kind == ItemKind.Decor && it.id in settings.ownedItems }
        .joinToString("") { it.emoji }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(settings.persona, StoreCatalog.skin(settings.skinId), 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(settings.persona.displayName, fontWeight = FontWeight.SemiBold)
                    Text("$totalMl / ${settings.targetMl} ml", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text(plantEmoji(progress) + decorations, fontSize = 30.sp)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text("💧 ${settings.points} giọt sương   ·   ${nextReminderLabel(settings.nextReminderAt)}", fontSize = 13.sp)
        }
    }
}

private fun plantEmoji(progress: Float): String = when {
    progress < STAGE_SPROUT -> "🌱"
    progress < STAGE_LEAFY -> "🌿"
    progress < STAGE_POTTED -> "🪴"
    progress < 1f -> "🌳"
    else -> "🌸🌳"
}

private fun nextReminderLabel(nextReminderAt: Long): String {
    if (nextReminderAt <= System.currentTimeMillis()) return "Chưa có lịch nhắc"
    val time = Instant.ofEpochMilli(nextReminderAt).atZone(ZoneId.systemDefault())
    return "Nhắc tiếp: ${reminderTimeFormat.format(time)}"
}
