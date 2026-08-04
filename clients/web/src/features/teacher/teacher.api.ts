import { apiDownload, apiRequest, jsonBody } from "../../api/client";
import type { AssistantSession, ContactCandidatePage, Conversation, LearningTask, Resource, Submission, TeacherProfile, TeacherWorkspace, TeachingAlert } from "./teacher.types";

export const teacherApi = {
  workspace: () => apiRequest<TeacherWorkspace>("/teacher/workspace"),
  updateProfile: (body: Partial<TeacherProfile>) => apiRequest<TeacherProfile>("/teacher/profile", { method: "PATCH", ...jsonBody(body) }),
  uploadResource: (courseId: number, kind: string, shared: boolean, file: File) => {
    const body = new FormData(); body.append("file", file);
    return apiRequest<Resource>(`/teacher/courses/${courseId}/resources?kind=${encodeURIComponent(kind)}&shared=${shared}`, { method: "POST", body });
  },
  downloadResource: (id: number) => apiDownload(`/teacher/resources/${id}/download`),
  downloadQuestionTemplate: () => apiDownload("/teacher/resources/question-bank-template"),
  deleteResource: (id: number) => apiRequest<void>(`/teacher/resources/${id}`, { method: "DELETE" }),
  createTask: (courseId: number, body: Partial<LearningTask>) => apiRequest<LearningTask>(`/teacher/courses/${courseId}/tasks`, { method: "POST", ...jsonBody(body) }),
  updateTask: (id: number, body: Partial<LearningTask>) => apiRequest<LearningTask>(`/teacher/tasks/${id}`, { method: "PATCH", ...jsonBody(body) }),
  updateQuestions: (id: number, questions: LearningTask["questions"]) => apiRequest<LearningTask>(`/teacher/tasks/${id}/questions`, { method: "PUT", ...jsonBody(questions) }),
  grade: (id: number, body: { teacherScore: number; teacherComment: string }) => apiRequest<Submission>(`/teacher/submissions/${id}/grade`, { method: "PUT", ...jsonBody(body) }),
  readAlert: (id: number) => apiRequest<TeachingAlert>(`/teacher/alerts/${id}/read`, { method: "PATCH" }),
  saveProposal: (id: number, proposal: string) => apiRequest<TeachingAlert>(`/teacher/alerts/${id}/proposal`, { method: "PUT", ...jsonBody({ proposal }) }),
  newAssistantSession: () => apiRequest<AssistantSession>("/teacher/assistant/sessions", { method: "POST" }),
  sendAssistantMessage: (id: number, content: string) => apiRequest<AssistantSession>(`/teacher/assistant/sessions/${id}/messages`, { method: "POST", ...jsonBody({ content }) }),
  readConversation: (id: number) => apiRequest<Conversation>(`/teacher/conversations/${id}/read`, { method: "PATCH" }),
  contactCandidates: (courseId: number, q: string, page = 1, size = 10) => apiRequest<ContactCandidatePage>(`/teacher/contact-candidates?courseId=${courseId}&q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  createConversation: (studentId: number, content: string) => apiRequest<Conversation>("/teacher/conversations", { method: "POST", ...jsonBody({ studentId, content }) }),
  sendConversationMessage: (id: number, content: string) => apiRequest<Conversation>(`/teacher/conversations/${id}/messages`, { method: "POST", ...jsonBody({ content }) }),
};

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a"); anchor.href = url; anchor.download = filename; anchor.click();
  URL.revokeObjectURL(url);
}
