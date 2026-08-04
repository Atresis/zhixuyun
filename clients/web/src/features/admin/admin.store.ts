import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { adminApi, type AdminClass, type AdminCourse, type AdminUser, type AdministrativeClass, type StudentImportResult, type TeacherDetail } from "./admin.api";

export const useAdminStore = defineStore("admin", () => {
  const users = ref<AdminUser[]>([]);
  const courses = ref<AdminCourse[]>([]);
  const classes = ref<AdministrativeClass[]>([]);
  const settings = ref<Record<string, string | number>>({});
  const metrics = ref<Record<string, number>>({});
  const health = ref<Record<string, string>>({});
  const teacherDetail = ref<TeacherDetail | null>(null);
  const loading = ref(false);
  const error = ref("");

  const studentCount = computed(() => metrics.value.studentCount ?? users.value.filter((item) => item.role === "STUDENT").length);
  const teacherCount = computed(() => metrics.value.teacherCount ?? users.value.filter((item) => item.role === "TEACHER").length);

  async function loadDashboard() {
    loading.value = true;
    error.value = "";
    try {
      const data = await adminApi.dashboard();
      metrics.value = data.metrics;
      health.value = data.health;
      users.value = data.users;
    } catch (cause) {
      error.value = (cause as Error).message;
      throw cause;
    } finally {
      loading.value = false;
    }
  }

  async function loadUsers(params: { keyword?: string; role?: string } = {}) {
    const data = await adminApi.users({ ...params, page: 0, size: 200 });
    users.value = data.content;
    return data;
  }

  async function loadTeachers(keyword = "") {
    return (await adminApi.teachers(keyword)).content;
  }

  async function loadCourses() {
    courses.value = (await adminApi.courses()).items;
  }

  async function loadClasses() {
    classes.value = (await adminApi.administrativeClasses()).items;
  }

  async function loadSettings() {
    settings.value = await adminApi.settings();
  }

  async function saveSettings() {
    settings.value = await adminApi.saveSettings(settings.value);
  }

  async function createUser(body: Record<string, unknown>) {
    const item = await adminApi.createUser(body);
    await loadUsers();
    return item;
  }

  async function updateUser(id: number, body: Record<string, unknown>) {
    const item = await adminApi.updateUser(id, body);
    await loadUsers();
    return item;
  }

  async function toggleUser(item: AdminUser) {
    await adminApi.setEnabled(item.id, !item.enabled);
    await loadUsers();
  }

  async function resetPassword(id: number, password: string) {
    await adminApi.resetPassword(id, password);
  }

  async function archiveStudent(id: number) {
    const item = await adminApi.archiveStudent(id);
    await loadUsers();
    return item;
  }

  async function importStudents(file: File): Promise<StudentImportResult> {
    const result = await adminApi.importStudents(file);
    await loadUsers({ role: "STUDENT" });
    return result;
  }

  async function transferStudent(id: number, administrativeClassId: number) {
    const item = await adminApi.transferStudent(id, administrativeClassId);
    await loadUsers({ role: "STUDENT" });
    return item;
  }

  async function createCourse(body: Record<string, unknown>) {
    const item = await adminApi.createCourse(body);
    await loadCourses();
    return item;
  }

  async function updateCourse(id: number, body: Record<string, unknown>) {
    const item = await adminApi.updateCourse(id, body);
    await loadCourses();
    return item;
  }

  async function deleteCourse(id: number) {
    await adminApi.deleteCourse(id);
    await loadCourses();
  }

  async function createCourseClass(courseId: number, body: Record<string, unknown>) {
    const item = await adminApi.createClass(courseId, body);
    await loadCourses();
    return item;
  }

  async function updateTeachingClass(id: number, body: Record<string, unknown>) {
    const item = await adminApi.updateTeachingClass(id, body);
    await loadCourses();
    return item;
  }

  async function deleteTeachingClass(id: number) {
    await adminApi.deleteTeachingClass(id);
    await loadCourses();
  }

  async function assignClassTeacher(classId: number, teacherId: number) {
    const item = await adminApi.assignClassTeacher(classId, teacherId);
    await loadCourses();
    return item;
  }

  async function createAdministrativeClass(body: Record<string, unknown>) {
    const item = await adminApi.createAdministrativeClass(body);
    await loadClasses();
    return item;
  }

  async function updateAdministrativeClass(id: number, body: Record<string, unknown>) {
    const item = await adminApi.updateAdministrativeClass(id, body);
    await loadClasses();
    return item;
  }

  async function deleteAdministrativeClass(id: number) {
    await adminApi.deleteAdministrativeClass(id);
    await loadClasses();
  }

  async function loadTeacherDetail(teacherId: number) {
    teacherDetail.value = await adminApi.teacherDetail(teacherId);
    return teacherDetail.value;
  }

  async function saveTeacherCourses(teacherId: number, assignments: Array<{ courseId: number; roleCode: string; subjectOrDuty: string }>) {
    teacherDetail.value = await adminApi.saveTeacherCourses(teacherId, assignments);
    await loadCourses();
    return teacherDetail.value;
  }

  return {
    users,
    courses,
    classes,
    settings,
    metrics,
    health,
    teacherDetail,
    loading,
    error,
    studentCount,
    teacherCount,
    loadDashboard,
    loadUsers,
    loadTeachers,
    loadCourses,
    loadClasses,
    loadSettings,
    saveSettings,
    createUser,
    updateUser,
    toggleUser,
    resetPassword,
    archiveStudent,
    importStudents,
    transferStudent,
    createCourse,
    updateCourse,
    deleteCourse,
    createCourseClass,
    updateTeachingClass,
    deleteTeachingClass,
    assignClassTeacher,
    createAdministrativeClass,
    updateAdministrativeClass,
    deleteAdministrativeClass,
    loadTeacherDetail,
    saveTeacherCourses,
  };
});
