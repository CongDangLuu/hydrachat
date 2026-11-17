package com.hydrachat.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrachat.app.domain.DayTotal
import com.hydrachat.app.domain.HydrationMath
import java.time.LocalDate
import java.time.ZoneId

private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30
private const val BAR_WIDTH_RATIO = 0.62f
private const val CHART_HEADROOM = 1.15f
private const val FADED_ALPHA = 0.45f
private const val MONTH_LABEL_STEP = 5

/** Tab 3: weekly/monthly chart followed by the settings. */
@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val historyLogs by viewModel.historyLogs.collectAsStateWithLifecycle()
    var isMonthView by rememberSaveable { mutableStateOf(false) }
    val entries = historyLogs.map { it.timestamp to it.amountMl }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val totals = HydrationMath.dailyTotals(entries, if (isMonthView) MONTH_DAYS else WEEK_DAYS, today, zone)
    val daysReached = totals.count { it.totalMl >= settings.targetMl }
    val average = if (totals.isEmpty()) 0 else totals.sumOf { it.totalMl } / totals.size
    val streak = HydrationMath.streakDays(entries, settings.targetMl, today, zone)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Thống kê", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isMonthView, onClick = { isMonthView = false }, label = { Text("7 ngày") })
            FilterChip(selected = isMonthView, onClick = { isMonthView = true }, label = { Text("30 ngày") })
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                WaterBarChart(totals, settings.targetMl, isMonthView)
                Text(
                    "Đường nét đứt: mục tiêu ${settings.targetMl} ml",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Trung bình", "$average ml", Modifier.weight(1f))
            StatTile("Đạt mục tiêu", "$daysReached/${totals.size} ngày", Modifier.weight(1f))
            StatTile("Chuỗi", "$streak ngày 🔥", Modifier.weight(1f))
        }
        SettingsSection(viewModel, settings)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun WaterBarChart(totals: List<DayTotal>, targetMl: Int, isMonthView: Boolean) {
    val barColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.tertiary
    val maxValue = (maxOf(targetMl, totals.maxOfOrNull { it.totalMl } ?: 0) * CHART_HEADROOM).coerceAtLeast(1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val slotWidth = size.width / totals.size.coerceAtLeast(1)
        val barWidth = slotWidth * BAR_WIDTH_RATIO
        totals.forEachIndexed { index, day ->
            val barHeight = size.height * (day.totalMl / maxValue)
            drawRoundRect(
                color = if (day.totalMl >= targetMl) barColor else barColor.copy(alpha = FADED_ALPHA),
                topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 4),
            )
        }
        val targetY = size.height * (1 - targetMl / maxValue)
        drawLine(
            color = targetColor,
            start = Offset(0f, targetY),
            end = Offset(size.width, targetY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        totals.forEachIndexed { index, day ->
            val isLabelled = !isMonthView || index % MONTH_LABEL_STEP == 0
            Text(
                text = if (isLabelled) day.date.dayOfMonth.toString() else "",
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
