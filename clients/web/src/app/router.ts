import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { useAuthStore } from "../features/auth/auth.store";
import { homeForRole } from "../features/auth/auth.utils";
import type { Role } from "../features/auth/auth.types";

const routes: RouteRecordRaw[] = [
  { path: "/", redirect: "/login" },
  { path: "/login", name: "login", component: () => import("../features/auth/LoginPage.vue"), meta: { public: true } },
  { path: "/change-password", name: "change-password", component: () => import("../features/auth/ChangePasswordPage.vue"), meta: { title: "修改初始密码" } },
  {
    path: "/teacher",
    meta: { role: "TEACHER" as Role },
    component: () => import("../features/teacher/TeacherLayout.vue"),
    redirect: "/teacher/dashboard",
    children: [
      { path: "dashboard", name: "teacher-dashboard", component: () => import("../features/teacher/TeacherDashboardPage.vue"), meta: { title: "首页", role: "TEACHER" as Role } },
      { path: "courses", name: "teacher-courses", component: () => import("../features/teacher/TeacherCoursesPage.vue"), meta: { title: "课程管理", role: "TEACHER" as Role } },
      { path: "tasks", name: "teacher-tasks", component: () => import("../features/teacher/TeacherTasksPage.vue"), meta: { title: "实验任务", role: "TEACHER" as Role } },
      { path: "review", name: "teacher-review", component: () => import("../features/teacher/TeacherReviewPage.vue"), meta: { title: "报告批阅", role: "TEACHER" as Role } },
      { path: "analytics", name: "teacher-analytics", component: () => import("../features/teacher/TeacherAnalyticsPage.vue"), meta: { title: "班级分析", role: "TEACHER" as Role } },
      { path: "rubrics", name: "teacher-rubrics", component: () => import("../features/teacher/TeacherRubricsPage.vue"), meta: { title: "评价模板", role: "TEACHER" as Role } },
      { path: "assistant", name: "teacher-assistant", component: () => import("../features/teacher/TeacherAssistantPage.vue"), meta: { title: "AI 教学助手", role: "TEACHER" as Role } },
      { path: "alerts", name: "teacher-alerts", component: () => import("../features/teacher/TeacherAlertsPage.vue"), meta: { title: "AI 教学预警", role: "TEACHER" as Role } },
      { path: "messages", name: "teacher-messages", component: () => import("../features/teacher/TeacherMessagesPage.vue"), meta: { title: "消息通知", role: "TEACHER" as Role } },
    ],
  },
  {
    path: "/admin",
    meta: { role: "ADMIN" as Role },
    component: () => import("../features/admin/AdminLayout.vue"),
    redirect: "/admin/dashboard",
    children: [
      { path: "dashboard", name: "admin-dashboard", component: () => import("../features/admin/AdminDashboardPage.vue"), meta: { title: "管理员首页", role: "ADMIN" as Role } },
      { path: "students", name: "admin-students", component: () => import("../features/admin/AdminUsersPage.vue"), meta: { title: "学生管理", role: "ADMIN" as Role } },
      { path: "teachers", name: "admin-teachers", component: () => import("../features/admin/AdminUsersPage.vue"), meta: { title: "教师管理", role: "ADMIN" as Role } },
      { path: "courses", name: "admin-courses", component: () => import("../features/admin/AdminCoursesPage.vue"), meta: { title: "课程管理", role: "ADMIN" as Role } },
      { path: "courses/:courseId/schedule", name: "admin-course-schedule", component: () => import("../features/admin/AdminCourseSchedulePage.vue"), meta: { title: "开课安排", role: "ADMIN" as Role } },
      { path: "classes", name: "admin-classes", component: () => import("../features/admin/AdminClassesPage.vue"), meta: { title: "班级管理", role: "ADMIN" as Role } },
      { path: "logs", name: "admin-logs", component: () => import("../features/admin/AdminLogsPage.vue"), meta: { title: "操作日志", role: "ADMIN" as Role } },
      { path: "settings", name: "admin-settings", component: () => import("../features/admin/AdminSettingsPage.vue"), meta: { title: "系统设置", role: "ADMIN" as Role } },
    ],
  },
  {
    path: "/student",
    meta: { role: "STUDENT" as Role },
    component: () => import("../features/student/StudentLayout.vue"),
    redirect: "/student/dashboard",
    children: [
      { path: "dashboard", name: "student-dashboard", component: () => import("../features/student/StudentDashboardPage.vue"), meta: { title: "学生首页", role: "STUDENT" as Role } },
      { path: "tasks", name: "student-tasks", component: () => import("../features/student/StudentTasksPage.vue"), meta: { title: "实验任务", role: "STUDENT" as Role } },
      { path: "courses", name: "student-courses", component: () => import("../features/student/StudentCoursesPage.vue"), meta: { title: "我的课程", role: "STUDENT" as Role } },
      { path: "reports", name: "student-reports", component: () => import("../features/student/StudentReportsPage.vue"), meta: { title: "我的报告", role: "STUDENT" as Role } },
      { path: "assistant", name: "student-assistant", component: () => import("../features/student/StudentAssistantPage.vue"), meta: { title: "AI 问答", role: "STUDENT" as Role } },
      { path: "notifications", name: "student-notifications", component: () => import("../features/student/StudentNotificationsPage.vue"), meta: { title: "消息通知", role: "STUDENT" as Role } },
    ],
  },
  { path: "/:pathMatch(.*)*", redirect: "/login" },
];

export const router = createRouter({ history: createWebHistory(), routes });

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.restore();
  if (to.meta.public) return auth.user ? homeForRole(auth.user.role) : true;
  if (!auth.user) return { name: "login", query: { redirect: to.fullPath } };
  if (auth.user.mustChangePassword && to.name !== "change-password") return { name: "change-password" };
  if (!auth.user.mustChangePassword && to.name === "change-password") return homeForRole(auth.user.role);
  const requiredRole = to.meta.role as Role | undefined;
  if (requiredRole && requiredRole !== auth.user.role) return homeForRole(auth.user.role);
  return true;
});
