export type StudentProfile = {
  id: number;
  loginName: string;
  displayName: string;
  studentNo: string;
  gradeYear: string;
  className: string;
};

export type StudentMetrics = {
  pendingTaskCount: number;
  submittedCount: number;
  aiReadyCount: number;
  reviewedCount: number;
};

export type StudentCourse = {
  id: number;
  name: string;
  code: string;
  className: string;
  semester: string;
  scheduleText: string;
  teacherName: string;
  color: string;
  resources?: StudentResource[];
};

export type StudentResource = {
  id: number;
  kind: "MATERIAL" | "QUESTION_BANK" | string;
  name: string;
  sourceLabel?: string;
  contentType?: string;
  fileSize?: number | null;
  createdAt?: string;
};

export type StudentQuestion = {
  id: number;
  type: "SINGLE" | "MULTIPLE" | "TRUE_FALSE" | "FILL" | "SHORT" | string;
  title: string;
  options?: string[];
  score?: number;
};

export type StudentTaskAttachment = {
  mode?: string;
  fileName?: string;
  contentType?: string;
  size?: number;
};

export type StudentTask = {
  id: number;
  courseId: number;
  taskType: "HOMEWORK" | "EXPERIMENT";
  name: string;
  description: string;
  startAt: string;
  deadline: string;
  maxScore: number;
  submissionId?: number | null;
  submittedAt?: string | null;
  aiScore?: number | null;
  teacherScore?: number | null;
  aiReview?: string | null;
  teacherComment?: string | null;
  reportText?: string | null;
  attachment: StudentTaskAttachment;
  reviewStatus?: "SUBMITTED" | "RETURNED" | "PUBLISHED";
  currentVersionNo?: number;
  submissionStatus: "待提交" | "已提交" | "AI 初评完成" | "教师已复核" | "已退回";
  questions?: StudentQuestion[];
  answers?: Record<string, string | string[]>;
};

export type StudentReport = {
  submissionId: number;
  taskId: number;
  courseId: number;
  taskName: string;
  submittedAt?: string | null;
  submissionStatus: StudentTask["submissionStatus"];
  aiScore?: number | null;
  teacherScore?: number | null;
  aiReview?: string | null;
  teacherComment?: string | null;
  reportText?: string | null;
  attachment: StudentTaskAttachment;
  reviewStatus?: "SUBMITTED" | "RETURNED" | "PUBLISHED";
  currentVersionNo?: number;
};

export type StudentNotification = {
  id: number;
  type: "TASK" | "AI" | "REVIEW" | "COURSE";
  title: string;
  content: string;
  createdAt: string;
  status?: "TODO" | "INFO" | "DONE";
  read?: boolean;
};

export type SubmissionVersion = {
  id: number; versionNo: number; reportText: string | null; attachment: StudentTaskAttachment;
  aiScore: number | null; aiReview: string | null; createdAt: string;
};

export type StudentWorkspace = {
  profile: StudentProfile;
  metrics: StudentMetrics;
  courses: StudentCourse[];
  tasks: StudentTask[];
  reports: StudentReport[];
  notifications: StudentNotification[];
  assistantPrompts: string[];
  assistantSessions?: StudentAssistantSession[];
  conversations?: StudentConversation[];
  teacherContacts?: StudentTeacherContact[];
};

export type StudentAssistantReply = { answer: string };
export type StudentChatMessage = { role: "user" | "assistant"; content: string };
export type StudentAssistantMessage = { id: number; role: "USER" | "ASSISTANT"; content: string; createdAt: string };
export type StudentAssistantSession = { id: number; title: string; createdAt: string; updatedAt: string; messages: StudentAssistantMessage[] };
export type StudentTeacherContact = { id: number; name: string; courseName: string };
export type StudentConversationMessage = { id: number; sender: "STUDENT" | "TEACHER" | string; title?: string | null; content: string; createdAt: string };
export type StudentConversation = { id: number; teacherId: number; contactName: string; avatarText: string; unreadCount: number; updatedAt: string; messages: StudentConversationMessage[] };
