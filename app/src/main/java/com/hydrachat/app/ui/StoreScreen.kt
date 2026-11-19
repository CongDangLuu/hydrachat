package com.hydrachat.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrachat.app.data.UserSettings
import com.hydrachat.app.domain.DailyQuest
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.ItemKind
import com.hydrachat.app.domain.QuestProgress
import com.hydrachat.app.domain.StoreCatalog
import com.hydrachat.app.domain.StoreItem
import java.time.LocalDate
import java.time.ZoneId

/** Tab 2: daily quests and the dew-drop store. */
@Composable
fun StoreScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayLogs.collectAsStateWithLifecycle()
    val historyLogs by viewModel.historyLogs.collectAsStateWithLifecycle()
    val progress = QuestProgress(
        cupsToday = todayLogs.size,
        isGoalReached = todayLogs.sumOf { it.amountMl } >= settings.targetMl,
        streakDays = HydrationMath.streakDays(
            historyLogs.map { it.timestamp to it.amountMl },
            settings.targetMl,
            LocalDate.now(),
            ZoneId.systemDefault(),
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("💧 ${settings.points} giọt sương", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        item { SectionTitle("Nhiệm vụ hôm nay") }
        items(StoreCatalog.dailyQuests, key = { it.id }) { quest ->
            QuestCard(
                quest = quest,
                value = progress.valueFor(quest),
                isClaimed = viewModel.isQuestClaimed(quest, settings.claimedQuests),
                onClaim = { viewModel.claimQuest(quest) },
            )
        }
        storeSection("Giao diện bong bóng", ItemKind.Skin, settings, viewModel)
        storeSection("Bộ câu thoại", ItemKind.Pack, settings, viewModel)
        storeSection("Trang trí cho cây", ItemKind.Decor, settings, viewModel)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.storeSection(
    title: String,
    kind: ItemKind,
    settings: UserSettings,
    viewModel: MainViewModel,
) {
    item(key = "title_$kind") { SectionTitle(title) }
    items(StoreCatalog.items.filter { it.kind == kind }, key = { it.id }) { item ->
        StoreItemCard(item, settings, viewModel)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun QuestCard(quest: DailyQuest, value: Int, isClaimed: Boolean, onClaim: () -> Unit) {
    val isDone = value >= quest.goal
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(quest.title, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (value.toFloat() / quest.goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text("${value.coerceAtMost(quest.goal)} / ${quest.goal}", fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            when {
                isClaimed -> Text("✅ Đã nhận", fontSize = 13.sp)
                isDone -> Button(onClick = onClaim) { Text("Nhận +${quest.reward}") }
                else -> Text("+${quest.reward} 💧", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StoreItemCard(item: StoreItem, settings: UserSettings, viewModel: MainViewModel) {
    val isOwned = item.id in settings.ownedItems
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.kind == ItemKind.Skin) {
                PersonaAvatar(settings.persona, item, 44.dp)
            } else {
                Text(item.emoji, fontSize = 30.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Medium)
                Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (isOwned) {
                OwnedItemAction(item, settings, viewModel)
            } else {
                Button(onClick = { viewModel.buy(item) }, enabled = settings.points >= item.price) {
                    Text("${item.price} 💧")
                }
            }
        }
    }
}

@Composable
private fun OwnedItemAction(item: StoreItem, settings: UserSettings, viewModel: MainViewModel) {
    when (item.kind) {
        ItemKind.Skin ->
            if (settings.skinId == item.id) {
                Text("✅ Đang dùng", fontSize = 13.sp)
            } else {
                FilledTonalButton(onClick = { viewModel.equipSkin(item.id) }) { Text("Dùng") }
            }
        ItemKind.Pack -> {
            val isActive = item.id in settings.activePacks
            OutlinedButton(onClick = { viewModel.togglePack(item.id) }) { Text(if (isActive) "Đang bật" else "Đang tắt") }
        }
        ItemKind.Decor -> Text("✅ Đã có", fontSize = 13.sp)
    }
}
