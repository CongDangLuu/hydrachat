import { initializeApp } from "firebase-admin/app";
import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { InvalidInputError, parseNudgeInput, requestNudge } from "./nudge";
import { consumeNudgeQuota } from "./quota";

initializeApp();

const geminiApiKey = defineSecret("GEMINI_API_KEY");

/** Generates one reminder line for the HydraChat app. Only App Check-verified app instances may call it. */
export const generateNudge = onCall(
  {
    region: "asia-southeast1",
    enforceAppCheck: true,
    secrets: [geminiApiKey],
    // Keeps a leaked or abused endpoint from scaling the Gemini bill.
    maxInstances: 5,
    timeoutSeconds: 30,
  },
  async (request) => {
    // The app signs in anonymously, so every install has a uid to count the daily quota against.
    const uid = request.auth?.uid;
    if (uid === undefined) throw new HttpsError("unauthenticated", "Sign-in is required");
    let input;
    try {
      input = parseNudgeInput(request.data);
    } catch (error) {
      if (error instanceof InvalidInputError) throw new HttpsError("invalid-argument", error.message);
      throw error;
    }
    // Invalid input is rejected above, so it never costs the user a nudge.
    if (!(await consumeNudgeQuota(uid))) {
      throw new HttpsError("resource-exhausted", "Daily nudge limit reached");
    }
    try {
      return await requestNudge(geminiApiKey.value(), input);
    } catch (error) {
      logger.warn("Gemini nudge failed", { reason: error instanceof Error ? error.message : "unknown" });
      throw new HttpsError("unavailable", "Nudge generation failed");
    }
  },
);
