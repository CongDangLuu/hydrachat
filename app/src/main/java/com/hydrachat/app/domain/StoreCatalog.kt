package com.hydrachat.app.domain

enum class ItemKind { Skin, Pack, Decor }

data class StoreItem(
    val id: String,
    val kind: ItemKind,
    val name: String,
    val emoji: String,
    val price: Int,
    val description: String,
    val ringColors: List<Long> = emptyList(),
)

enum class QuestMetric { CupsToday, GoalReached, MiniQuests, Streak }

data class DailyQuest(
    val id: String,
    val title: String,
    val metric: QuestMetric,
    val goal: Int,
    val reward: Int,
)

data class QuestProgress(
    val cupsToday: Int,
    val isGoalReached: Boolean,
    val miniQuestsToday: Int,
    val streakDays: Int,
) {
    fun valueFor(quest: DailyQuest): Int = when (quest.metric) {
        QuestMetric.CupsToday -> cupsToday
        QuestMetric.GoalReached -> if (isGoalReached) 1 else 0
        QuestMetric.MiniQuests -> miniQuestsToday
        QuestMetric.Streak -> streakDays
    }
}

object StoreCatalog {
    const val DEFAULT_SKIN_ID = "skin_default"

    val items: List<StoreItem> = listOf(
        StoreItem(DEFAULT_SKIN_ID, ItemKind.Skin, "Nước trong", "💧", 0, "Viền xanh mặc định", listOf(0xFF42A5F5, 0xFF1E88E5)),
        StoreItem("skin_ocean", ItemKind.Skin, "Đại dương", "🌊", 80, "Viền xanh ngọc sâu thẳm", listOf(0xFF26C6DA, 0xFF00838F, 0xFF1A237E)),
        StoreItem("skin_sunset", ItemKind.Skin, "Hoàng hôn", "🌇", 120, "Viền cam hồng ấm áp", listOf(0xFFFFB300, 0xFFFF7043, 0xFFD81B60)),
        StoreItem("skin_neon", ItemKind.Skin, "Neon", "⚡", 160, "Viền neon cho dân cú đêm", listOf(0xFF00E676, 0xFF00B0FF, 0xFFD500F9)),
        StoreItem("skin_sakura", ItemKind.Skin, "Sakura", "🌸", 200, "Viền hồng anh đào", listOf(0xFFF8BBD0, 0xFFF06292, 0xFFBA68C8)),
        StoreItem("pack_meme", ItemKind.Pack, "Bộ thoại Meme", "🤣", 150, "Thêm 20 câu nhắc kiểu meme công sở"),
        StoreItem("pack_poetry", ItemKind.Pack, "Bộ thoại Thơ", "📜", 150, "Thêm 20 câu nhắc bằng thơ con cóc"),
        StoreItem("decor_pot", ItemKind.Decor, "Chậu gốm", "🏺", 60, "Chậu xinh cho bé cây"),
        StoreItem("decor_sun", ItemKind.Decor, "Nắng mai", "☀️", 90, "Thêm chút nắng cho góc bàn"),
        StoreItem("decor_butterfly", ItemKind.Decor, "Bướm nhỏ", "🦋", 120, "Bạn bướm ghé chơi"),
        StoreItem("decor_rainbow", ItemKind.Decor, "Cầu vồng", "🌈", 200, "Cầu vồng sau cơn mưa"),
    )

    val dailyQuests: List<DailyQuest> = listOf(
        DailyQuest("cups_4", "Uống 4 lần trong ngày", QuestMetric.CupsToday, 4, 20),
        DailyQuest("mini_3", "Hoàn thành 3 nhiệm vụ giãn cơ", QuestMetric.MiniQuests, 3, 20),
        DailyQuest("goal", "Đạt mục tiêu nước hôm nay", QuestMetric.GoalReached, 1, 30),
        DailyQuest("streak_3", "Giữ chuỗi 3 ngày đạt mục tiêu", QuestMetric.Streak, 3, 50),
    )

    fun find(id: String): StoreItem? = items.firstOrNull { it.id == id }

    fun skin(id: String): StoreItem = find(id)?.takeIf { it.kind == ItemKind.Skin } ?: items.first()
}
