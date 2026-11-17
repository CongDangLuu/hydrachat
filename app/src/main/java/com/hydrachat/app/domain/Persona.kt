package com.hydrachat.app.domain

enum class Persona(
    val id: String,
    val displayName: String,
    val emoji: String,
    val tagline: String,
    val hello: String,
    val promptRole: String,
) {
    Boss(
        id = "boss",
        displayName = "Sếp khó tính",
        emoji = "🧑‍💼",
        tagline = "Nghiêm khắc nhưng quan tâm",
        hello = "Tôi sẽ giám sát KPI uống nước của cậu từ hôm nay. 🤨",
        promptRole = "Sếp khó tính nhưng quan tâm",
    ),
    Crush(
        id = "crush",
        displayName = "Crush ngọt ngào",
        emoji = "🥰",
        tagline = "Dịu dàng, hay hỏi han",
        hello = "Từ giờ để mình nhắc cậu uống nước mỗi ngày nha. 💕",
        promptRole = "Crush ngọt ngào",
    ),
    GenZ(
        id = "genz",
        displayName = "Gen Z bắt trend",
        emoji = "😎",
        tagline = "Bạn thân lầy lội",
        hello = "Ố dè, từ nay tui là trợ lý chống khô héo của bồ nha! ✨",
        promptRole = "Bạn thân Gen Z bắt trend",
    ),
    Sprout(
        id = "sprout",
        displayName = "Bé Mầm Cây",
        emoji = "🌱",
        tagline = "Lớn lên cùng từng ly nước",
        hello = "Chủ nhân ơi, con sẽ lớn lên cùng mỗi ly nước của người đó! 🌱",
        promptRole = "Bé Mầm Cây đáng yêu",
    );

    companion object {
        fun fromId(id: String): Persona = entries.firstOrNull { it.id == id } ?: GenZ
    }
}
