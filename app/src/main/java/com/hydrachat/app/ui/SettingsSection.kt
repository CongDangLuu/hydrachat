package com.hydrachat.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hydrachat.app.data.UserSettings
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.reminder.ContextDetector
import com.hydrachat.app.reminder.ReminderNotifier

private const val WEIGHT_STEP_KG = 5
private const val MIN_WEIGHT_KG = 35
private const val MAX_WEIGHT_KG = 150
private const val TARGET_STEP_ML = 100
private const val MIN_TARGET_ML = 800
private const val MAX_TARGET_ML = 5000
private const val TIME_STEP_MINUTES = 30
private const val MIN_WORK_SPAN_MINUTES = 60
private const val LAST_MINUTE_OF_DAY = 23 * 60 + 30
private val cupSizes = listOf(200, 250, 300, 350, 500)
private val intervalChoices = listOf(0, 30, 45, 60, 90)

private data class InstalledApp(val packageName: String, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSection(viewModel: MainViewModel, settings: UserSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cài đặt", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

        SettingsCard("Người bạn đồng hành") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Persona.entries.forEach { persona ->
                    FilterChip(
                        selected = settings.personaId == persona.id,
                        onClick = { viewModel.updateSettings { it.copy(personaId = persona.id) } },
                        label = { Text("${persona.emoji} ${persona.displayName}") },
                    )
                }
            }
        }

        SettingsCard("Mục tiêu nước") {
            StepperRow("Cân nặng", "${settings.weightKg} kg") { direction ->
                val weight = (settings.weightKg + direction * WEIGHT_STEP_KG).coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
                viewModel.updateSchedule { it.copy(weightKg = weight, targetMl = HydrationMath.dailyTargetMl(weight)) }
            }
            StepperRow("Mục tiêu/ngày", "${settings.targetMl} ml") { direction ->
                val target = (settings.targetMl + direction * TARGET_STEP_ML).coerceIn(MIN_TARGET_ML, MAX_TARGET_ML)
                viewModel.updateSchedule { it.copy(targetMl = target) }
            }
            Text("Dung tích cốc", fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cupSizes.forEach { cup ->
                    FilterChip(
                        selected = settings.cupMl == cup,
                        onClick = { viewModel.updateSchedule { it.copy(cupMl = cup) } },
                        label = { Text("$cup ml") },
                    )
                }
            }
        }

        SettingsCard("Giờ nhắc (ngoài giờ sẽ ngủ đông)") {
            StepperRow("Bắt đầu", HydrationMath.formatMinuteOfDay(settings.workStartMinute)) { direction ->
                val start = (settings.workStartMinute + direction * TIME_STEP_MINUTES)
                    .coerceIn(0, settings.workEndMinute - MIN_WORK_SPAN_MINUTES)
                viewModel.updateSchedule { it.copy(workStartMinute = start) }
            }
            StepperRow("Kết thúc", HydrationMath.formatMinuteOfDay(settings.workEndMinute)) { direction ->
                val end = (settings.workEndMinute + direction * TIME_STEP_MINUTES)
                    .coerceIn(settings.workStartMinute + MIN_WORK_SPAN_MINUTES, LAST_MINUTE_OF_DAY)
                viewModel.updateSchedule { it.copy(workEndMinute = end) }
            }
            SwitchRow("Nghỉ nhắc cuối tuần", settings.skipWeekends) { isOn ->
                viewModel.updateSchedule { it.copy(skipWeekends = isOn) }
            }
            Text("Khoảng cách giữa các lần nhắc", fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                intervalChoices.forEach { minutes ->
                    val label = if (minutes == 0) "Tự động (${settings.copy(intervalMinutes = 0).effectiveIntervalMinutes}p)" else "${minutes}p"
                    FilterChip(
                        selected = settings.intervalMinutes == minutes,
                        onClick = { viewModel.updateSchedule { it.copy(intervalMinutes = minutes) } },
                        label = { Text(label) },
                    )
                }
            }
            SwitchRow("Âm thanh khi bong bóng hiện", settings.isSoundEnabled) { isOn ->
                viewModel.updateSettings { it.copy(isSoundEnabled = isOn) }
            }
        }

        GeminiCard(viewModel, settings)
        HiddenAppsCard(viewModel, settings)
        PermissionsCard(viewModel)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onStep(-1) }) { Text("−", fontSize = 20.sp) }
        Text(value, fontWeight = FontWeight.Medium)
        TextButton(onClick = { onStep(1) }) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun SwitchRow(label: String, isChecked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = isChecked, onCheckedChange = onChange)
    }
}

@Composable
private fun GeminiCard(viewModel: MainViewModel, settings: UserSettings) {
    var draftKey by rememberSaveable(settings.apiKey) { mutableStateOf(settings.apiKey) }
    SettingsCard("Gemini AI (lời thoại động)") {
        Text(
            if (settings.apiKey.isBlank()) {
                "Chưa có API key: đang dùng kho câu thoại offline. Lấy key miễn phí ở Google AI Studio."
            } else {
                "Đã bật: câu thoại được tải trước 5 phút mỗi lần nhắc, lỗi mạng sẽ dùng kho offline."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draftKey,
            onValueChange = { draftKey = it.trim() },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { viewModel.updateSchedule { it.copy(apiKey = draftKey) } },
            enabled = draftKey != settings.apiKey,
        ) { Text("Lưu key") }
    }
}

@Composable
private fun HiddenAppsCard(viewModel: MainViewModel, settings: UserSettings) {
    val context = LocalContext.current
    var isDialogOpen by remember { mutableStateOf(false) }
    var hasUsageAccess by remember { mutableStateOf(ContextDetector.hasUsageAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasUsageAccess = ContextDetector.hasUsageAccess(context) }

    SettingsCard("Không làm phiền") {
        Text(
            "Bong bóng tự hoãn 10 phút khi máy bật Không làm phiền, đang xoay ngang, " +
                "hoặc khi bạn đang mở một trong ${settings.hiddenApps.size} ứng dụng đã chọn.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionRow("Quyền xem ứng dụng đang mở", hasUsageAccess) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        OutlinedButton(onClick = { isDialogOpen = true }) { Text("Chọn ứng dụng tự ẩn bong bóng") }
    }
    if (isDialogOpen) {
        HiddenAppsDialog(
            selected = settings.hiddenApps,
            onToggle = { packageName ->
                viewModel.updateSettings {
                    val apps = if (packageName in it.hiddenApps) it.hiddenApps - packageName else it.hiddenApps + packageName
                    it.copy(hiddenApps = apps)
                }
            },
            onDismiss = { isDialogOpen = false },
        )
    }
}

@Composable
private fun HiddenAppsDialog(selected: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { loadLaunchableApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } },
        title = { Text("Ẩn bong bóng khi đang mở") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = app.packageName in selected, onCheckedChange = { onToggle(app.packageName) })
                        Text(app.label, fontSize = 14.sp)
                    }
                }
            }
        },
    )
}

@Composable
private fun PermissionsCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var canNotify by remember { mutableStateOf(ReminderNotifier.canPost(context)) }
    var canExact by remember { mutableStateOf(viewModel.canScheduleExact()) }
    var canBubble by remember { mutableStateOf(ReminderNotifier.canBubble(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canOverlay = Settings.canDrawOverlays(context)
        canNotify = ReminderNotifier.canPost(context)
        canExact = viewModel.canScheduleExact()
        canBubble = ReminderNotifier.canBubble(context)
    }
    SettingsCard("Quyền") {
        PermissionRow("🫧 Nổi bong bóng", canOverlay) { context.openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
        PermissionRow("🔔 Thông báo", canNotify) { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }
        PermissionRow("⏰ Nhắc đúng giờ", canExact) { context.openSettings(EXACT_ALARM_SETTINGS_ACTION) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PermissionRow("💬 Bong bóng hệ thống", canBubble) { context.openBubbleSettings() }
        }
        if (!canOverlay) {
            Text(
                "Chưa có quyền nổi bong bóng: mình sẽ nhắc bằng bong bóng hệ thống (nếu được bật) " +
                    "hoặc thông báo có nút trả lời nhanh.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// This screen takes the package as an extra, not as a "package:" URI like openSettings uses.
@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.openBubbleSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }
}

// Same value as Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, which only exists from API 31.
private const val EXACT_ALARM_SETTINGS_ACTION = "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"

private fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(launcherIntent, 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
