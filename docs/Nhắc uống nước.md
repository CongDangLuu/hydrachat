# Tài liệu Thiết kế & Kiến trúc Ứng dụng Nhắc Uống Nước (HydraChat)

Tài liệu này tổng hợp toàn bộ ý tưởng sản phẩm, luồng trải nghiệm người dùng (User Flow), kiến trúc kỹ thuật (Technical Architecture) và hướng dẫn tích hợp **Gemini API** để xây dựng ứng dụng nhắc uống nước tối ưu cho dân văn phòng với giao diện bong bóng chat (Chat Heads) trực quan.

---

## 1. Tổng quan Ý tưởng & Giá trị Cốt lõi

* **Tên dự án:** HydraChat
* **Nhóm khách hàng mục tiêu:** Dân văn phòng, lập trình viên, nhân viên sáng tạo (người ngồi máy tính 8 tiếng/ngày).
* **Nỗi đau (Pain Points):**
  * Dễ bị cuốn vào công việc (*Deep Work*) nên quên uống nước cả buổi.
  * Lười mở khóa điện thoại và mở ứng dụng chỉ để bấm nhập liệu thủ công.
  * Nhờn với các thông báo hệ thống khô khan, máy móc.
  * Rất ghét bị thông báo làm phiền giữa lúc đang họp online hoặc tập trung cao độ.
* **Giải pháp đột phá:**
  * **Bong bóng chat nổi (Chat Heads):** Hiển thị đè lên màn hình giống hệt Messenger, cho phép đọc tin nhắn và bấm xác nhận uống nước ngay lập tức mà không cần mở ứng dụng chính.
  * **Nhân cách hóa bằng Gemini AI:** Sử dụng **Gemini API (Free Tier)** để sinh lời thoại động theo thời gian thực dựa trên tính cách nhân vật (*Sếp khó tính*, *Crush ngọt ngào*, *Gen Z bắt trend*, *Bé Mầm Cây*).
  * **Micro-Quests (Nhiệm vụ siêu nhỏ):** Kết hợp nhắc uống nước với các bài tập giãn cơ/nghỉ mắt 10–20 giây để chống đau vai gáy và tạo động lực cày cuốc.

---

## 2. Luồng Trải nghiệm Người dùng (User Flow)

### 2.1. Onboarding (Thiết lập lần đầu — Mục tiêu dưới 45 giây)
Biến màn hình thiết lập thành **cuộc hội thoại đầu tiên** thay vì biểu mẫu truyền thống:
1. **Chọn Persona (Người bạn đồng hành):** Lướt chọn nhân vật yêu thích (VD: *Sếp khó tính*, *Crush ngọt ngào*, *Gen Z bắt trend*, *Bé Mầm Cây*).
2. **Khai báo qua hội thoại:** Nhân vật nhắn tin hỏi cân nặng và khung giờ làm việc (VD: `08:30 – 17:30`). Người dùng bấm chọn các nút gợi ý nhanh (*Chips*) để hệ thống tự tính định mức nước mục tiêu (VD: `2000ml`) và dung tích cốc cố định trên bàn làm việc (`250ml`).
3. **Cấp quyền chiến lược:**
   * Giải thích bằng ngôn ngữ thân thiện: *"Cho phép mình nổi bong bóng trên màn hình để nhắc bạn mà không cần mở app nhé!"*
   * Điều hướng thẳng tới màn hình cấp quyền hệ thống (`Display over other apps` trên Android / `Allow Notifications` trên iOS).
   * **Kiểm tra tức thì:** Ngay khi cấp quyền xong, bắn thử một bong bóng chat chào mừng để người dùng trải nghiệm ngay.

### 2.2. Core Loop (Vòng lặp tương tác hằng ngày — Không cần mở app)
1. **Kích hoạt (Trigger):** Đến giờ nhắc, hệ thống kiểm tra điều kiện an toàn (không ở chế độ *Không làm phiền*, màn hình không xoay ngang để chơi game/xem phim).
2. **Hiển thị (Display):**
   * **Android:** Bong bóng chat (avatar nhân vật + badge số `1` đỏ) trượt ra cạnh màn hình kèm âm thanh nhẹ và bản xem trước tin nhắn.
   * **iOS:** Thông báo tương tác (*Rich Notification*) bật xuống kèm biểu cảm nhân vật và các nút hành động nhanh.
3. **Tương tác (Action):**
   * Người dùng chạm vào bong bóng $\rightarrow$ Bung ra **Khung Chat Mini (Overlay Window)** chiếm khoảng 40% màn hình.
   * Hiển thị lời thoại AI, nhiệm vụ vận động nhỏ và các nút trả lời nhanh (*Quick Reply*):
     * `[ 🥤 Đã uống 250ml ]`
     * `[ 💧 Nhấp 1 ngụm (100ml) ]`
     * `[ 🤫 Đang họp, nhắc lại sau 20p ]`
4. **Phản hồi & Đóng (Feedback & Dismiss):**
   * Bấm `[ 🥤 Đã uống 250ml ]` $\rightarrow$ Nhân vật gửi sticker khen ngợi, thanh tiến độ nước và điểm thưởng nhiệm vụ tăng lên.
   * Khung chat tự động thu gọn sau 1.5 giây hoặc người dùng vuốt bong bóng xuống dấu `X` để đóng. Toàn bộ quá trình diễn ra trong **dưới 3 giây**.

### 2.3. In-App Experience (Khi người dùng chủ động mở ứng dụng)
* **Tab 1 — Phòng Chat & Nhân vật (Home):** Giao diện lịch sử trò chuyện trong ngày, thanh năng lượng lượng nước đã uống và hình ảnh nhân vật/cây đang phát triển.
* **Tab 2 — Cửa hàng & Nhiệm vụ (Quests & Store):** Dùng điểm thưởng (*giọt sương/xu*) tích lũy từ việc uống nước để đổi giao diện bong bóng chat, mở khóa bộ câu thoại mới hoặc vật phẩm trang trí.
* **Tab 3 — Thống kê & Cài đặt (Stats & Settings):** Biểu đồ lượng nước theo tuần/tháng, thiết lập khung giờ ngủ đông (ngoài giờ hành chính) và danh sách ứng dụng tự động ẩn bong bóng (YouTube, Netflix, Game...).

---

## 3. Kiến trúc Kỹ thuật (Technical Architecture)

### 3.1. Lựa chọn Tech Stack
* **Nền tảng chính (Android):** **Native Kotlin** kết hợp **Jetpack Compose** cho giao diện trong app và **WindowManager** cho bong bóng chat nổi (đảm bảo độ mượt và tương thích sâu với quyền vẽ đè màn hình).
* **Cơ sở dữ liệu cục bộ:** **Room Database (SQLite)** lưu trữ lịch sử uống nước, tiến độ nhiệm vụ và kho câu thoại dự phòng offline.
* **Lập lịch chạy ngầm:** **`AlarmManager`** (`setExactAndAllowWhileIdle`) kết hợp **`WorkManager`** để đánh thức ứng dụng đúng giờ mà không bị hệ điều hành tắt ngầm và không gây hao pin.

### 3.2. Sơ đồ Phân tầng Hệ thống

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                       │
│  ┌──────────────────────┐      ┌─────────────────────────┐  │
│  │   Main App UI        │      │ Floating Bubble Service │  │
│  │  (Jetpack Compose)   │      │ (Foreground Service +   │  │
│  │  - Chat History      │      │  WindowManager View)    │  │
│  └──────────┬───────────┘      └────────────┬────────────┘  │
└─────────────┼───────────────────────────────┼───────────────┘
              │                               │
              ▼                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN & ENGINE                        │
│  ┌──────────────────────┐      ┌─────────────────────────┐  │
│  │ Smart Scheduler      │      │ Context & Anti-Annoy    │  │
│  │ (AlarmManager +      │      │ Detector                │  │
│  │  WorkManager)        │      │ (Orientation, DND,      │  │
│  └──────────┬───────────┘      │  Foreground App Check)  │  │
│             │                  └────────────┬────────────┘  │
│             ▼                               │               │
│  ┌──────────────────────────────────────────▼────────────┐  │
│  │          Gemini AI Service & Local Cache              │  │
│  │   - Structured Output (JSON Schema)                   │  │
│  └──────────────────────────┬────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        DATA LAYER                           │
│  ┌──────────────────────┐      ┌─────────────────────────┐  │
│  │ Local Room DB        │      │ Gemini 2.5 Flash API    │  │
│  │ - Water Logs         │◄────►│ (Dynamic Prompts)       │  │
│  │ - Quest Progress     │      └─────────────────────────┘  │
│  └──────────────────────┘                                   │
└─────────────────────────────────────────────────────────────┘
```

### 3.3. Giải pháp Kỹ thuật cho Các Bài toán Cốt lõi
1. **Bong bóng nổi & Vật lý kéo thả (`WindowManager`):**
   * Sử dụng flag `TYPE_APPLICATION_OVERLAY` (Android 8.0+).
   * Xử lý sự kiện chạm (`OnTouchListener`) kết hợp `ValueAnimator` để bong bóng tự động hít (*snap*) vào cạnh trái hoặc phải màn hình khi thả tay.
   * Tự động fallback về **Android Bubbles API** (Android 11+) hoặc thông báo nút bấm nhanh nếu người dùng từ chối cấp quyền vẽ đè màn hình.
2. **Tối ưu Pin & Chống Doze Mode:**
   * Tuyệt đối không chạy Service liên tục 24/7. Chỉ khởi chạy `ForegroundService` ngắn hạn khi đến giờ báo thức từ `AlarmManager`, sau đó gọi `stopSelf()` ngay khi người dùng tương tác xong.
3. **Chiến lược Hybrid AI (Chống trễ & Chống vượt hạn mức Free Tier):**
   * Dùng `WorkManager` tải trước (*Prefetch*) câu thoại từ Gemini trước giờ nhắc 5 phút và lưu vào Room Database để bong bóng bật lên với độ trễ **0ms**.
   * Lưu sẵn kho 200+ câu thoại offline làm phương án dự phòng khi mất mạng hoặc chạm giới hạn request miễn phí.

---

## 4. Tích hợp Gemini API (Cấu trúc Prompt & Code Mẫu)

### 4.1. Cấu trúc Prompt Tối ưu
* **System Instruction (Định hình vai diễn):**
  > Bạn là người bạn ảo nhắc uống nước qua bong bóng chat. Viết tin nhắn tiếng Việt tự nhiên, ngắn gọn dưới 25 từ, đúng vai diễn, bắt trend nhẹ nhàng. Không chào hỏi rườm rà. Luôn trả về định dạng JSON theo đúng schema quy định.
* **Dynamic User Prompt (Truyền ngữ cảnh):**
  > Vai diễn: `{persona}` | Thời điểm: `{timeContext}` | Tiến độ: `{currentMl}` / `{targetMl}` ml. Hãy nhắn 1 tin nhắc uống nước kèm nhiệm vụ vận động nhỏ.

### 4.2. Khai báo Dependency (`build.gradle.kts`)
```kotlin
dependencies {
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

### 4.3. Code Mẫu Tích hợp Kotlin (Sử dụng Structured Outputs)
Sử dụng model **`gemini-2.5-flash`** để đạt tốc độ phản hồi nhanh nhất và tận dụng hạn mức miễn phí từ Google AI Studio.

```kotlin
package com.example.hydrachat.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 1. Model dữ liệu hứng kết quả JSON từ Gemini
@Serializable
data class HydrationMessage(
    val chatText: String,       // Nội dung bong bóng chat
    val quickReplyText: String, // Chữ trên nút bấm (VD: "Uống liền 1 cốc 🥤")
    val miniQuest: String       // Nhiệm vụ giãn cơ ngắn cho dân văn phòng
)

class HydrationAiService(private val apiKey: String) {

    // 2. Ép Gemini trả về đúng cấu trúc JSON Schema
    private val responseSchema = Schema.obj(
        name = "hydration_nudge",
        description = "Tin nhắn nhắc uống nước dạng chat",
        properties = mapOf(
            "chatText" to Schema.str("Câu nhắn nhắc uống nước ngắn gọn, đúng tính cách, có emoji"),
            "quickReplyText" to Schema.str("Nội dung nút bấm xác nhận đã uống, tối đa 4 từ"),
            "miniQuest" to Schema.str("Một hành động giãn cơ/nghỉ mắt 10 giây cho dân văn phòng")
        ),
        required = listOf("chatText", "quickReplyText", "miniQuest")
    )

    // 3. Khởi tạo model Gemini 2.5 Flash
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.9f // Tăng độ sáng tạo để câu thoại đa dạng
            responseMimeType = "application/json"
            responseSchema = this@HydrationAiService.responseSchema
        },
        systemInstruction = content {
            text("Bạn là người bạn ảo nhắc uống nước qua bong bóng chat. Viết tin nhắn tiếng Việt tự nhiên, ngắn gọn dưới 25 từ, đúng vai diễn, bắt trend nhẹ nhàng.")
        }
    )

    // 4. Hàm gọi sinh câu thoại theo ngữ cảnh
    suspend fun generateNudge(
        persona: String,
        timeContext: String,
        currentMl: Int,
        targetMl: Int
    ): Result<HydrationMessage> {
        val prompt = """
            Vai diễn: $persona
            Thời điểm: $timeContext
            Tiến độ uống nước: $currentMl / $targetMl ml.
            Hãy nhắn 1 tin nhắc uống nước kèm nhiệm vụ nhỏ.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text ?: throw Exception("Phản hồi rỗng từ API")
            val message = Json.decodeFromString<HydrationMessage>(jsonText)
            Result.success(message)
        } catch (e: Exception) {
            // Fallback về kho câu thoại offline trong Room DB nếu lỗi mạng/quota
            Result.failure(e)
        }
    }
}
```

---

## 5. Ví dụ Minh họa Đầu ra Thực tế từ AI

### Ví dụ 1: Vai diễn "Sếp khó tính nhưng quan tâm"
* **Input:** `persona = "Sếp khó tính"`, `timeContext = "15:15 chiều thứ 6"`, `currentMl = 600`, `targetMl = 2000`
* **JSON Output:**
```json
{
  "chatText": "Chiều thứ 6 rồi mà mới uống có 600ml, định để thận đình công trước khi tan làm à? Nạp nước ngay! 🤨💧",
  "quickReplyText": "Dạ sếp, uống liền 🥤",
  "miniQuest": "Nhìn ra cửa sổ 15 giây cho đỡ mỏi mắt rồi quay lại chạy deadline."
}
```

### Ví dụ 2: Vai diễn "Gen Z bắt trend"
* **Input:** `persona = "Bạn thân Gen Z"`, `timeContext = "10:30 sáng"`, `currentMl = 250`, `targetMl = 2000`
* **JSON Output:**
```json
{
  "chatText": "Alo alo, chạy KPI vừa thôi bồ ơi, uống miếng nước cho đỡ khô héo tâm hồn đi nè! ✨💦",
  "quickReplyText": "Chốt đơn 1 ly 🥤",
  "miniQuest": "Đứng dậy vươn vai 10 giây cho cột sống đỡ kêu cứu nha."
}
```

### Ví dụ 3: Vai diễn "Bé Mầm Cây"
* **Input:** `persona = "Bé Mầm Cây đáng yêu"`, `timeContext = "16:00 chiều"`, `currentMl = 1200`, `targetMl = 2000`
* **JSON Output:**
```json
{
  "chatText": "Chủ nhân ơi, lá con hơi héo rồi, tiếp thêm 1 ly nước để tụi mình cùng về đích nha 🥺🌱",
  "quickReplyText": "Tưới nước cho bé 💧",
  "miniQuest": "Xoay cổ nhẹ nhàng 5 vòng trong lúc chờ nước trôi xuống bụng nhé!"
}
```

---

## 6. Lưu ý Bảo mật & Triển khai Production
* **Bảo vệ API Key:** Tuyệt đối không lưu cứng `apiKey` trong mã nguồn ứng dụng khi phát hành lên Google Play (tránh rủi ro bị dịch ngược file APK). Hãy đặt API Key trên một **Backend trung gian siêu nhẹ** (Firebase Cloud Functions hoặc Node.js/Python server) và bật **Firebase App Check** để xác thực request hợp lệ.
* **Trải nghiệm không làm phiền (Anti-Annoyance):** Tích hợp `UsageStatsManager` (nếu người dùng cấp quyền) hoặc kiểm tra trạng thái xoay màn hình (`Configuration.ORIENTATION_LANDSCAPE`) để tự động tạm hoãn hiển thị bong bóng khi người dùng đang xem video toàn màn hình hoặc chơi game.