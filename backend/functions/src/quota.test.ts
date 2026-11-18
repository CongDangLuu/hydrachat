import assert from "node:assert/strict";
import { test } from "node:test";
import { nextQuotaState, quotaDay } from "./quota";

const LIMIT = 3;
const TODAY = "2026-11-18";

test("starts counting at one for a new user or a new day", () => {
  assert.deepEqual(nextQuotaState(undefined, TODAY, LIMIT), { day: TODAY, count: 1 });
  assert.deepEqual(nextQuotaState({ day: "2026-11-17", count: LIMIT }, TODAY, LIMIT), { day: TODAY, count: 1 });
});

test("counts up to the limit and then refuses", () => {
  assert.deepEqual(nextQuotaState({ day: TODAY, count: 1 }, TODAY, LIMIT), { day: TODAY, count: 2 });
  assert.deepEqual(nextQuotaState({ day: TODAY, count: LIMIT - 1 }, TODAY, LIMIT), { day: TODAY, count: LIMIT });
  assert.equal(nextQuotaState({ day: TODAY, count: LIMIT }, TODAY, LIMIT), null);
  assert.equal(nextQuotaState({ day: TODAY, count: LIMIT + 5 }, TODAY, LIMIT), null);
});

test("the quota day rolls over at midnight in Vietnam, not UTC", () => {
  assert.equal(quotaDay(new Date("2026-11-18T16:59:59Z")), "2026-11-18");
  assert.equal(quotaDay(new Date("2026-11-18T17:00:00Z")), "2026-11-19");
});
