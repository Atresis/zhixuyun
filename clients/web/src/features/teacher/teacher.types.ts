export type TeacherProfile = {
  id: number; loginName: string; displayName: string; department: string; title: string;
  email: string; phone: string; bio: string;
};

export type Resource = {
  id: number; kind: "QUESTION_BANK" | "COURSE" | "MATERIAL"; name: string; sourceLabel: string;
  shared: boolean; contentType: string; ownedByCurrentTeacher: boolean; createdAt: string;
};

export type QuestionType = "SINGLE" | "MULTIPLE" | "TRUE_FALSE" | "FILL" | "SHORT";
export type Question = { id: number; type: QuestionType; title: string; options: string[]; answer: string; score: number };
export type Submission = {
  id: number; taskId: number; studentName: string; studentNo: string; submitted: boolean; submittedAt: string | null;
  aiScore: number | null; teacherScore: number | null; answers: Array<{ questionId: number; answer: string }>;
  reportText: string | null; aiReview: string | null; teacherComment: string | null;
};
export type LearningTask = {
  id: number; courseId: number; taskType: "HOMEWORK" | "EXPERIMENT"; name: string; description: string;
  startAt: string; deadline: string; maxScore: number; questions: Question[]; createdAt: string;
  submissions: Submission[]; submittedCount: number; gradedCount: number; studentCount: number;
};
export type Course = {
  id: number; name: string; code: string; className: string; semester: string; scheduleText: string;
  studentCount: number; color: string; resources: Resource[]; tasks: LearningTask[];
};
export type TeachingAlert = {
  id: number; title: string; summary: string; targetName: string; level: "HIGH" | "MEDIUM";
  status: "UNREAD" | "READ" | "PROPOSED"; analysis: string; evidence: string; proposal: string | null; createdAt: string;
};
export type AssistantMessage = { id: number; role: "USER" | "ASSISTANT"; content: string; createdAt: string };
export type AssistantSession = { id: number; title: string; createdAt: string; updatedAt: string; messages: AssistantMessage[] };
export type ConversationMessage = { id: number; sender: "SYSTEM" | "CONTACT" | "TEACHER"; title: string | null; content: string; createdAt: string };
export type Conversation = {
  id: number; studentId?: number | null; contactName: string; contactType: "SYSTEM" | "STUDENT" | "TEACHER"; avatarText: string;
  unreadCount: number; updatedAt: string; messages: ConversationMessage[];
};
export type ContactCandidate = { id: number; name: string; studentNo: string; className: string };
export type ContactCandidatePage = { items: ContactCandidate[]; page: number; size: number; total: number; pages: number };
export type TeacherWorkspace = {
  profile: TeacherProfile;
  metrics: { courseCount: number; activeTaskCount: number; pendingReviewCount: number; weeklySubmissionRate: number };
  semesters: string[]; courses: Course[]; alerts: TeachingAlert[]; assistantSessions: AssistantSession[];
  conversations: Conversation[]; recommendations: string[];
};
