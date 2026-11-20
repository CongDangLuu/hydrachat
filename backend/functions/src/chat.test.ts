import assert from "node:assert/strict";
import { test } from "node:test";
import { MAX_DRANK_ML, parseChatInput, parseChatResponse } from "./chat";
import { InvalidInputError } from "./nudge";

const validInput = {
  personaId: "genz",
  timeContext: "15:15 chiều thứ 6",
  currentMl: 600,
  targetMl: 2000,
  cupMl: 350,
  history: [{ fromUser: false, text: "Uống nước đi bồ!" }],
  message: " Mình vừa uống 1 ly ",
};

const geminiBody = (output: object) => ({ candidates: [{ content: { parts: [{ text: JSON.stringify(output) }] } }] });

test("accepts a message with bounded history and trims it", () => {
  const input = parseChatInput(validInput);
  assert.equal(input.message, "Mình vừa uống 1 ly");
  assert.deepEqual(input.history, validInput.history);
});

test("rejects empty, oversized or malformed chat input", () => {
  assert.throws(() => parseChatInput({ ...validInput, message: "   " }), InvalidInputError);
  assert.throws(() => parseChatInput({ ...validInput, message: "a".repeat(301) }), InvalidInputError);
  assert.throws(() => parseChatInput({ ...validInput, history: Array(9).fill(validInput.history[0]) }), InvalidInputError);
  assert.throws(() => parseChatInput({ ...validInput, history: [{ fromUser: "yes", text: "hi" }] }), InvalidInputError);
  assert.throws(() => parseChatInput({ ...validInput, cupMl: 5000 }), InvalidInputError);
});

test("keeps a plausible drink amount and drops anything out of range", () => {
  assert.deepEqual(parseChatResponse(geminiBody({ replyText: " Đỉnh! ", drankMl: 350 })), { replyText: "Đỉnh!", drankMl: 350 });
  assert.equal(parseChatResponse(geminiBody({ replyText: "Ok", drankMl: MAX_DRANK_ML + 1 })).drankMl, 0);
  assert.equal(parseChatResponse(geminiBody({ replyText: "Ok", drankMl: -50 })).drankMl, 0);
  assert.throws(() => parseChatResponse(geminiBody({ replyText: "", drankMl: 0 })));
});
