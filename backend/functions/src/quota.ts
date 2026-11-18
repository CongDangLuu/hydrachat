/** Per-user daily cap on generated nudges, stored as one Firestore document per uid. */

import { getFirestore } from "firebase-admin/firestore";

export interface QuotaState {
  day: string;
  count: number;
}

// A workday of reminders is about 10-15 nudges; the cap leaves room for retries and the test button.
export const DAILY_NUDGE_LIMIT = 30;
const QUOTA_COLLECTION = "nudgeQuota";
// Users are Vietnamese office workers, so the quota day rolls over at local midnight.
const QUOTA_TIME_ZONE = "Asia/Ho_Chi_Minh";

/** Calendar day in the quota time zone, e.g. "2026-11-18". */
export function quotaDay(now: Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: QUOTA_TIME_ZONE }).format(now);
}

/** Returns the state after one more nudge, or null when today's limit is already used up. */
export function nextQuotaState(current: QuotaState | undefined, day: string, limit: number): QuotaState | null {
  if (current === undefined || current.day !== day) return { day, count: 1 };
  if (current.count >= limit) return null;
  return { day, count: current.count + 1 };
}

function toQuotaState(data: FirebaseFirestore.DocumentData | undefined): QuotaState | undefined {
  if (typeof data?.day !== "string" || typeof data?.count !== "number") return undefined;
  return { day: data.day, count: data.count };
}

/** Counts one nudge for the user. Returns false when the daily limit is reached. */
export async function consumeNudgeQuota(uid: string, now: Date = new Date()): Promise<boolean> {
  const firestore = getFirestore();
  const quotaRef = firestore.collection(QUOTA_COLLECTION).doc(uid);
  // A transaction keeps parallel calls from the same user from slipping past the limit.
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(quotaRef);
    const next = nextQuotaState(toQuotaState(snapshot.data()), quotaDay(now), DAILY_NUDGE_LIMIT);
    if (next === null) return false;
    transaction.set(quotaRef, next);
    return true;
  });
}
