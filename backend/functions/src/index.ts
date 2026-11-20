import { initializeApp } from "firebase-admin/app";
import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { parseChatInput, requestChatReply } from "./chat";
import { InvalidInputError, parseNudgeInput, requestNudge } from "./nudge";
import { consumeNudgeQuota } from "./quota";

initializeApp();

const geminiApiKey = defineSecret("GEMINI_API_KEY");

const CALLABLE_OPTIONS = {
  region: "asia-southeast1",
  enforceAppCheck: true,
  secrets: [geminiApiKey],
  // Keeps a leaked or abused endpoint from scaling the Gemini bill.
  maxInstances: 5,
  timeoutSeconds: 30,
};

/** Shared by both callables: sign-in check, strict input parsing, the per-user daily quota, then Gemini. */
async function runWithQuota<Input, Output>(
  request: CallableRequest,
  parse: (data: unknown) => Input,
  run: (apiKey: string, input: Input) => Promise<Output>,
): Promise<Output> {
  // The app signs in anonymously, so every install has a uid to count the daily quota against.
  const uid = request.auth?.uid;
  if (uid === undefined) throw new HttpsError("unauthenticated", "Sign-in is required");
  let input: Input;
  try {
    input = parse(request.data);
  } catch (error) {
    if (error instanceof InvalidInputError) throw new HttpsError("invalid-argument", error.message);
    throw error;
  }
  // Invalid input is rejected above, so it never costs the user a call.
  if (!(await consumeNudgeQuota(uid))) {
    throw new HttpsError("resource-exhausted", "Daily nudge limit reached");
  }
  try {
    return await run(geminiApiKey.value(), input);
  } catch (error) {
    logger.warn("Gemini call failed", { reason: error instanceof Error ? error.message : "unknown" });
    throw new HttpsError("unavailable", "Generation failed");
  }
}

/** Generates one reminder line for the HydraChat app. Only App Check-verified app instances may call it. */
export const generateNudge = onCall(CALLABLE_OPTIONS, (request) =>
  runWithQuota(request, parseNudgeInput, requestNudge),
);

/** Answers a message the user typed in the chat, in the persona's voice; counts against the same quota. */
export const chatReply = onCall(CALLABLE_OPTIONS, (request) =>
  runWithQuota(request, parseChatInput, requestChatReply),
);
