import { apiDownload, apiRequest, jsonBody } from "../../api/client";
import type { StudentAssistantReply, StudentAssistantSession, StudentConversation, StudentCourse, StudentWorkspace, SubmissionVersion, StudentTeacherContact } from "./student.types";

export const studentApi = {
  workspace: () => apiRequest<StudentWorkspace>("/student/workspace"),
  submitText: (taskId: number, content: string) =>
    apiRequest<StudentWorkspace>(`/student/tasks/${taskId}/text-submission`, { method: "POST", ...jsonBody({ content }) }),
  submitFile: (taskId: number, file: File) => {
    const body = new FormData();
    body.append("file", file);
    return apiRequest<StudentWorkspace>(`/student/tasks/${taskId}/file-submission`, { method: "POST", body });
  },
  submitAnswers: (taskId: number, answers: Record<string, string | string[]>) =>
    apiRequest<StudentWorkspace>(`/student/tasks/${taskId}/answer-submission`, { method: "POST", ...jsonBody({ answers }) }),
  askAssistant: (content: string) =>
    apiRequest<StudentAssistantReply>("/student/assistant/ask", { method: "POST", ...jsonBody({ content }) }),
  joinCourse: (code: string) => apiRequest<StudentCourse>("/student/courses/join", { method: "POST", ...jsonBody({ code }) }),
  downloadResource: (id: number) => apiDownload(`/student/resources/${id}/download`),
  versions: (submissionId: number) => apiRequest<SubmissionVersion[]>(`/submissions/${submissionId}/versions`),
  readNotification: (id: number) => apiRequest<void>(`/notifications/${id}/read`, { method: "PATCH" }),
  readAllNotifications: () => apiRequest<{ updated: number }>("/notifications/read-all", { method: "POST" }),
  createAssistantSession: () => apiRequest<StudentAssistantSession>("/student/assistant/sessions", { method: "POST" }),
  sendAssistantMessage: (id: number, content: string) => apiRequest<StudentAssistantSession>(`/student/assistant/sessions/${id}/messages`, { method: "POST", ...jsonBody({ content }) }),
  teacherContacts: () => apiRequest<{ items: StudentTeacherContact[] }>("/student/teacher-contacts"),
  createConversation: (teacherId: number, content: string) => apiRequest<StudentConversation>("/student/conversations", { method: "POST", ...jsonBody({ teacherId, content }) }),
  sendConversationMessage: (id: number, content: string) => apiRequest<StudentConversation>(`/student/conversations/${id}/messages`, { method: "POST", ...jsonBody({ content }) }),
  readConversation: (id: number) => apiRequest<StudentConversation>(`/student/conversations/${id}/read`, { method: "POST" }),
};
