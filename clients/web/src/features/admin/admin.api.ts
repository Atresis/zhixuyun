import { apiRequest, jsonBody } from "../../api/client";

export type AdminPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type AdminUser = {
  id: number;
  loginName: string;
  displayName: string;
  role: "STUDENT" | "TEACHER" | "ADMIN";
  enabled: boolean;
  email?: string;
  phone?: string;
  bio?: string;
  studentNo?: string;
  gradeYear?: string;
  administrativeClassId?: number | null;
  administrativeClassName?: string;
};
export type CourseTeacher = { teacherId: number; teacherName: string; roleCode: string; subjectOrDuty: string };
export type AdminClass = {
  id: number;
  courseId: number;
  name: string;
  term: string;
  enabled: boolean;
  teacherId?: number | null;
  teacherName?: string | null;
};
export type AdminCourse = {
  id: number;
  name: string;
  code: string;
  className: string;
  semester: string;
  scheduleText?: string;
  studentCount: number;
  color: string;
  teacherId: number;
  teacherName: string;
  classes: AdminClass[];
  teachers: CourseTeacher[];
};
export type AdministrativeClass = { id: number; name: string; gradeYear: string; majorName?: string; enabled: boolean };
export type TeacherDetail = {
  id: number;
  loginName: string;
  displayName: string;
  email?: string;
  phone?: string;
  bio?: string;
  department?: string;
  title?: string;
  courseAssignments: Array<{ courseId: number; courseName: string; courseCode: string; semester: string; roleCode: string; subjectOrDuty: string }>;
  teachingClassAssignments: Array<{ teachingClassId: number; teachingClassName: string; term: string; courseId: number; courseName: string }>;
};
export type StudentImportResult = { createdCount: number; skippedCount: number; items: AdminUser[] };

export const adminApi = {
  dashboard: () => apiRequest<{ metrics: Record<string, number>; users: AdminUser[]; health: Record<string, string> }>("/admin/dashboard"),
  users: (params: { page?: number; size?: number; keyword?: string; role?: string } = {}) =>
    apiRequest<AdminPage<AdminUser>>(`/admin/users?${new URLSearchParams(Object.entries(params).filter(([, value]) => value !== undefined) as [string, string][]).toString()}`),
  teachers: (keyword = "") => apiRequest<{ content: AdminUser[] }>(`/admin/teachers?keyword=${encodeURIComponent(keyword)}`),
  teacherDetail: (teacherId: number) => apiRequest<TeacherDetail>(`/admin/teachers/${teacherId}`),
  saveTeacherCourses: (teacherId: number, assignments: Array<{ courseId: number; roleCode: string; subjectOrDuty: string }>) =>
    apiRequest<TeacherDetail>(`/admin/teachers/${teacherId}/courses`, { method: "PUT", ...jsonBody({ assignments }) }),
  createUser: (body: Record<string, unknown>) => apiRequest<AdminUser>("/admin/users", { method: "POST", ...jsonBody(body) }),
  updateUser: (id: number, body: Record<string, unknown>) => apiRequest<AdminUser>(`/admin/users/${id}`, { method: "PATCH", ...jsonBody(body) }),
  setEnabled: (id: number, enabled: boolean) => apiRequest<AdminUser>(`/admin/users/${id}/status`, { method: "PATCH", ...jsonBody({ enabled }) }),
  resetPassword: (id: number, newPassword: string) => apiRequest<void>(`/admin/users/${id}/password`, { method: "PATCH", ...jsonBody({ newPassword }) }),
  archiveStudent: (id: number) => apiRequest<{ archived: boolean; purgeAfter: string }>(`/admin/users/${id}/archive`, { method: "POST" }),
  importStudents: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<StudentImportResult>("/admin/users/import", { method: "POST", body: form });
  },
  transferStudent: (id: number, administrativeClassId: number) =>
    apiRequest<AdminUser>(`/admin/users/${id}/transfer`, { method: "PUT", ...jsonBody({ administrativeClassId }) }),
  courses: () => apiRequest<{ items: AdminCourse[] }>("/admin/courses"),
  createCourse: (body: Record<string, unknown>) => apiRequest<AdminCourse>("/admin/courses", { method: "POST", ...jsonBody(body) }),
  updateCourse: (id: number, body: Record<string, unknown>) => apiRequest<AdminCourse>(`/admin/courses/${id}`, { method: "PUT", ...jsonBody(body) }),
  deleteCourse: (id: number) => apiRequest<void>(`/admin/courses/${id}`, { method: "DELETE" }),
  createClass: (courseId: number, body: Record<string, unknown>) => apiRequest<AdminClass>(`/admin/courses/${courseId}/classes`, { method: "POST", ...jsonBody(body) }),
  updateTeachingClass: (id: number, body: Record<string, unknown>) => apiRequest<AdminClass>(`/admin/teaching-classes/${id}`, { method: "PUT", ...jsonBody(body) }),
  deleteTeachingClass: (id: number) => apiRequest<void>(`/admin/teaching-classes/${id}`, { method: "DELETE" }),
  assignClassTeacher: (classId: number, teacherId: number) => apiRequest<AdminClass>(`/admin/classes/${classId}/teacher`, { method: "PUT", ...jsonBody({ teacherId }) }),
  administrativeClasses: () => apiRequest<{ items: AdministrativeClass[] }>("/admin/administrative-classes"),
  createAdministrativeClass: (body: Record<string, unknown>) => apiRequest<AdministrativeClass>("/admin/administrative-classes", { method: "POST", ...jsonBody(body) }),
  updateAdministrativeClass: (id: number, body: Record<string, unknown>) =>
    apiRequest<AdministrativeClass>(`/admin/administrative-classes/${id}`, { method: "PUT", ...jsonBody(body) }),
  deleteAdministrativeClass: (id: number) => apiRequest<void>(`/admin/administrative-classes/${id}`, { method: "DELETE" }),
  settings: () => apiRequest<Record<string, string | number>>("/admin/settings"),
  saveSettings: (body: Record<string, unknown>) => apiRequest<Record<string, string | number>>("/admin/settings", { method: "PUT", ...jsonBody(body) }),
};
