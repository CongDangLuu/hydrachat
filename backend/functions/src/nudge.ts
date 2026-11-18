/** Prompt building, input validation and the Gemini call for one reminder line. */

export interface NudgeInput {
  personaId: string;
  timeContext: string;
  currentMl: number;
  targetMl: number;
}

export interface NudgeContent {
  chatText: string;
  quickReplyText: string;
  miniQuest: string;
}

export class InvalidInputError extends Error {}

// Must stay in sync with Persona.promptRole in app/src/main/java/com/hydrachat/app/domain/Persona.kt.
const PERSONA_ROLES: Readonly<Record<string, string>> = {
  boss: "Sếp khó tính nhưng quan tâm",
  crush: "Crush ngọt ngào",
  genz: "Bạn thân Gen Z bắt trend",
  sprout: "Bé Mầm Cây đáng yêu",
};

// Same shape as HydrationMath.timeContext on the client, e.g. "15:15 chiều thứ 6".
const TIME_CONTEXT_PATTERN = /^([01]\d|2[0-3]):[0-5]\d (sáng|trưa|chiều|tối) (thứ [2-7]|chủ nhật)$/u;
const MAX_CURRENT_ML = 20_000;
const MIN_TARGET_ML = 100;
const MAX_TARGET_ML = 10_000;
const MAX_FIELD_LENGTH = 300;

const MODEL = "gemini-3.5-flash-lite";
const ENDPOINT = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`;
const TIMEOUT_MS = 15_000;
const TEMPERATURE = 0.9;
const SYSTEM_INSTRUCTION =
  "Bạn là người bạn ảo nhắc uống nước qua bong bóng chat. Viết tin nhắn tiếng Việt tự nhiên, " +
  "ngắn gọn dưới 25 từ, đúng vai diễn, bắt trend nhẹ nhàng. Không chào hỏi rườm rà. " +
  "Luôn trả về định dạng JSON theo đúng schema quy định.";

function isIntInRange(value: unknown, min: number, max: number): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= min && value <= max;
}

/** Rejects anything that is not exactly what the app sends, so the endpoint is not a free-form LLM proxy. */
export function parseNudgeInput(data: unknown): NudgeInput {
  if (typeof data !== "object" || data === null) throw new InvalidInputError("Body must be an object");
  const { personaId, timeContext, currentMl, targetMl } = data as Record<string, unknown>;
  // hasOwn, not `in`: inherited keys like "__proto__" or "toString" must not pass.
  if (typeof personaId !== "string" || !Object.hasOwn(PERSONA_ROLES, personaId)) {
    throw new InvalidInputError("Unknown personaId");
  }
  const normalizedTime = typeof timeContext === "string" ? timeContext.normalize("NFC") : "";
  if (!TIME_CONTEXT_PATTERN.test(normalizedTime)) throw new InvalidInputError("Invalid timeContext");
  if (!isIntInRange(currentMl, 0, MAX_CURRENT_ML)) throw new InvalidInputError("Invalid currentMl");
  if (!isIntInRange(targetMl, MIN_TARGET_ML, MAX_TARGET_ML)) throw new InvalidInputError("Invalid targetMl");
  return { personaId, timeContext: normalizedTime, currentMl, targetMl };
}

export function buildRequestBody(input: NudgeInput): object {
  const userPrompt =
    `Vai diễn: ${PERSONA_ROLES[input.personaId]} | Thời điểm: ${input.timeContext} | ` +
    `Tiến độ: ${input.currentMl} / ${input.targetMl} ml. ` +
    "Hãy nhắn 1 tin nhắc uống nước kèm nhiệm vụ vận động nhỏ.";
  const stringField = (description: string) => ({ type: "STRING", description });
  return {
    systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
    contents: [{ role: "user", parts: [{ text: userPrompt }] }],
    generationConfig: {
      temperature: TEMPERATURE,
      responseMimeType: "application/json",
      responseSchema: {
        type: "OBJECT",
        properties: {
          chatText: stringField("Câu nhắn nhắc uống nước ngắn gọn, đúng tính cách, có emoji"),
          quickReplyText: stringField("Nội dung nút bấm xác nhận đã uống, tối đa 4 từ"),
          miniQuest: stringField("Một hành động giãn cơ/nghỉ mắt 10 giây cho dân văn phòng"),
        },
        required: ["chatText", "quickReplyText", "miniQuest"],
      },
    },
  };
}

function cleanField(value: unknown): string {
  return typeof value === "string" ? value.trim().slice(0, MAX_FIELD_LENGTH) : "";
}

export function parseGeminiResponse(body: unknown): NudgeContent {
  const text = (body as { candidates?: { content?: { parts?: { text?: unknown }[] } }[] })
    ?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (typeof text !== "string") throw new Error("Gemini response has no text part");
  const parsed = JSON.parse(text) as Record<string, unknown>;
  const nudge = {
    chatText: cleanField(parsed.chatText),
    quickReplyText: cleanField(parsed.quickReplyText),
    miniQuest: cleanField(parsed.miniQuest),
  };
  if (nudge.chatText === "") throw new Error("Gemini returned an empty chat text");
  return nudge;
}

export async function requestNudge(apiKey: string, input: NudgeInput): Promise<NudgeContent> {
  const response = await fetch(ENDPOINT, {
    method: "POST",
    // Header instead of a query parameter keeps the key out of URLs and logs.
    headers: { "Content-Type": "application/json; charset=utf-8", "x-goog-api-key": apiKey },
    body: JSON.stringify(buildRequestBody(input)),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`Gemini request failed with HTTP ${response.status}`);
  return parseGeminiResponse(await response.json());
}
