import type { Question, QuestionType } from "./teacher.types";

const binaryTypes: QuestionType[] = ["SINGLE", "TRUE_FALSE", "FILL"];

export function normalizeAnswer(value: string): string {
  return value.split(/[,，]/).map((part) => part.trim().toUpperCase()).filter(Boolean).sort().join(",");
}

export function initialQuestionScore(question: Question, studentAnswer: string, aiScore: number, taskMaxScore: number): number {
  if (question.type === "SHORT") return Math.round(question.score * (aiScore / Math.max(taskMaxScore, 1)));
  return normalizeAnswer(studentAnswer) === normalizeAnswer(question.answer) ? question.score : 0;
}

export function validateQuestionScores(questions: Question[], scores: Record<number, number>): boolean {
  return questions.every((question) => {
    const score = Number(scores[question.id]);
    if (!Number.isInteger(score) || score < 0 || score > question.score) return false;
    return !binaryTypes.includes(question.type) || score === 0 || score === question.score;
  });
}
