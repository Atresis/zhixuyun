import { apiRequest, jsonBody } from "../../api/client";
import type { StudentAssistantReply, StudentCourse, StudentWorkspace, SubmissionVersion } from "./student.types";

export const studentApi = {
  workspace: () => apiRequest<StudentWorkspace>("/student/workspace"),
  submitText: (taskId: number, content: string) =>
    apiRequest<StudentWorkspace>(`/student/tasks/${taskId}/text-submission`, { method: "POST", ...jsonBody({ content }) }),
  submitFile: (taskId: number, file: File) => {
    const body = new FormData();
    body.append("file", file);
    return apiRequest<StudentWorkspace>(`/student/tasks/${taskId}/file-submission`, { method: "POST", body });
  },
  askAssistant: (content: string) =>
    apiRequest<StudentAssistantReply>("/student/assistant/ask", { method: "POST", ...jsonBody({ content }) }),
  joinCourse: (code: string) => apiRequest<StudentCourse>("/student/courses/join", { method: "POST", ...jsonBody({ code }) }),
  versions: (submissionId: number) => apiRequest<SubmissionVersion[]>(`/submissions/${submissionId}/versions`),
  readNotification: (id: number) => apiRequest<void>(`/notifications/${id}/read`, { method: "PATCH" }),
  readAllNotifications: () => apiRequest<{ updated: number }>("/notifications/read-all", { method: "POST" }),
};
