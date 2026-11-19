package com.hydrachat.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.domain.StoreCatalog
import com.hydrachat.app.reminder.ReminderNotifier

private enum class OnboardingStep { PickPersona, Weight, Age, WorkHours, Cup, Permissions }

private data class OnboardingMessage(val text: String, val isFromUser: Boolean)

private data class WorkHoursOption(val startMinute: Int, val endMinute: Int) {
    val label: String
        get() = "${HydrationMath.formatMinuteOfDay(startMinute)} – ${HydrationMath.formatMinuteOfDay(endMinute)}"
}

private val weightOptions = listOf(45, 50, 55, 60, 65, 70, 75, 80, 90)
private val ageOptions = listOf("Dưới 55 tuổi", "Từ 55 tuổi trở lên")
private val cupOptions = listOf(200, 250, 300, 350, 500)
private val workHoursOptions = listOf(
    WorkHoursOption(8 * 60, 17 * 60),
    WorkHoursOption(8 * 60 + 30, 17 * 60 + 30),
    WorkHoursOption(9 * 60, 18 * 60),
    WorkHoursOption(10 * 60, 19 * 60),
)

/** First-run setup presented as the first conversation with the chosen persona. */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by remember { mutableStateOf(OnboardingStep.PickPersona) }
    var persona by remember { mutableStateOf(Persona.GenZ) }
    var weightKg by remember { mutableIntStateOf(60) }
    var isAge55Plus by remember { mutableStateOf(false) }
    var workHours by remember { mutableStateOf(workHoursOptions[1]) }
    var cupMl by remember { mutableIntStateOf(250) }
    val messages = remember {
        mutableStateListOf(OnboardingMessage("Chào bạn! Chọn một người bạn đồng hành nhắc uống nước nhé 👇", false))
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { listState.animateScrollToItem(messages.lastIndex) }

    fun answer(userText: String, botText: String, next: OnboardingStep) {
        messages += OnboardingMessage(userText, true)
        messages += OnboardingMessage(botText, false)
        step = next
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("HydraChat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { ChatBubble(text = it.text, isFromUser = it.isFromUser) }
        }
        when (step) {
            OnboardingStep.PickPersona -> PersonaChoices { picked ->
                persona = picked
                answer(picked.displayName, "${picked.hello}\nBạn nặng bao nhiêu kg để mình tính lượng nước?", OnboardingStep.Weight)
            }
            OnboardingStep.Weight -> ChipChoices(weightOptions.map { "$it kg" }) { index ->
                weightKg = weightOptions[index]
                answer("${weightKg} kg", "Bạn thuộc nhóm tuổi nào? Từ 55 tuổi nhu cầu nước thấp hơn một chút.", OnboardingStep.Age)
            }
            OnboardingStep.Age -> ChipChoices(ageOptions) { index ->
                isAge55Plus = index == ageOptions.lastIndex
                answer(ageOptions[index], "Giờ làm việc của bạn thế nào? Ngoài giờ mình sẽ ngủ đông 😴", OnboardingStep.WorkHours)
            }
            OnboardingStep.WorkHours -> ChipChoices(workHoursOptions.map { it.label }) { index ->
                workHours = workHoursOptions[index]
                answer(workHours.label, "Cái cốc trên bàn bạn dung tích bao nhiêu?", OnboardingStep.Cup)
            }
            OnboardingStep.Cup -> ChipChoices(cupOptions.map { "$it ml" }) { index ->
                cupMl = cupOptions[index]
                val target = HydrationMath.dailyTargetMl(weightKg, isAge55Plus)
                val cups = (target + cupMl - 1) / cupMl
                answer(
                    "$cupMl ml",
                    "Chốt! Mục tiêu $target ml/ngày ≈ $cups cốc.\n" +
                        "Cho phép mình nổi bong bóng trên màn hình để nhắc bạn mà không cần mở app nhé!",
                    OnboardingStep.Permissions,
                )
            }
            OnboardingStep.Permissions -> PermissionStep(
                viewModel = viewModel,
                persona = persona,
                onFinish = {
                    viewModel.finishOnboarding(
                        OnboardingResult(persona, weightKg, isAge55Plus, workHours.startMinute, workHours.endMinute, cupMl),
                    )
                },
            )
        }
    }
}

@Composable
private fun PersonaChoices(onPick: (Persona) -> Unit) {
    val skin = StoreCatalog.skin(StoreCatalog.DEFAULT_SKIN_ID)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Persona.entries.forEach { persona ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { onPick(persona) },
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonaAvatar(persona = persona, skin = skin, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(persona.displayName, fontWeight = FontWeight.SemiBold)
                        Text(persona.tagline, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipChoices(labels: List<String>, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            SuggestionChip(onClick = { onPick(index) }, label = { Text(label) })
        }
    }
}

@Composable
private fun PermissionStep(viewModel: MainViewModel, persona: Persona, onFinish: () -> Unit) {
    val context = LocalContext.current
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var canNotify by remember { mutableStateOf(ReminderNotifier.canPost(context)) }
    var canExact by remember { mutableStateOf(viewModel.canScheduleExact()) }
    var isWelcomeShown by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canOverlay = Settings.canDrawOverlays(context)
        canNotify = ReminderNotifier.canPost(context)
        canExact = viewModel.canScheduleExact()
    }
    // Instant check: the first bubble pops up as soon as the overlay permission is granted.
    LaunchedEffect(canOverlay) {
        if (canOverlay && !isWelcomeShown) {
            isWelcomeShown = true
            viewModel.showWelcomeBubble(welcomeNudge(persona))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermissionRow("🫧 Nổi bong bóng trên màn hình", canOverlay) {
            context.openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }
        PermissionRow("🔔 Gửi thông báo", canNotify) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow("⏰ Nhắc đúng giờ", canExact) {
                context.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Bắt đầu thôi 🚀") }
    }
}

@Composable
fun PermissionRow(label: String, isGranted: Boolean, onRequest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (isGranted) {
            Text("✅ Đã bật", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
        } else {
            OutlinedButton(onClick = onRequest) { Text("Cho phép") }
        }
    }
}

fun Context.openSettings(action: String) {
    val intent = Intent(action, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun welcomeNudge(persona: Persona) = NudgeContent(
    chatText = "${persona.hello} Bong bóng của mình sẽ hiện như thế này mỗi lần tới giờ uống nước!",
    quickReplyText = "Uống ly đầu tiên 🥤",
)
