import { describe, expect, it } from "vitest";
import { initialQuestionScore, normalizeAnswer, validateQuestionScores } from "./teacher.scoring";
import type { Question } from "./teacher.types";

const question = (id: number, type: Question["type"], score = 10): Question => ({ id, type, score, title: "题目", options: [], answer: "A,B" });

describe("teacher grading rules", () => {
  it("normalizes multiple choice answer order", () => expect(normalizeAnswer(" b，a ")).toBe("A,B"));
  it("requires binary question types to be zero or full score", () => {
    expect(validateQuestionScores([question(1, "SINGLE")], { 1: 5 })).toBe(false);
    expect(validateQuestionScores([question(1, "SINGLE")], { 1: 10 })).toBe(true);
  });
  it("allows integer partial credit for multiple and short questions", () => {
    expect(validateQuestionScores([question(1, "MULTIPLE"), question(2, "SHORT", 20)], { 1: 6, 2: 13 })).toBe(true);
  });
  it("uses the AI ratio as the initial short-answer score", () => expect(initialQuestionScore(question(1, "SHORT", 20), "", 75, 100)).toBe(15));
});
