import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { studentApi } from "./student.api";
import type { StudentChatMessage, StudentTask, StudentWorkspace } from "./student.types";

export const useStudentStore = defineStore("student", () => {
  const workspace = ref<StudentWorkspace | null>(null);
  const loading = ref(false);
  const error = ref("");
  const assistantBusy = ref(false);
  const messages = ref<StudentChatMessage[]>([]);

  const tasks = computed(() => workspace.value?.tasks || []);
  const courses = computed(() => workspace.value?.courses || []);
  const reports = computed(() => workspace.value?.reports || []);
  const notifications = computed(() => workspace.value?.notifications || []);
  const profile = computed(() => workspace.value?.profile || null);
  const metrics = computed(() => workspace.value?.metrics || { pendingTaskCount: 0, submittedCount: 0, aiReadyCount: 0, reviewedCount: 0 });
  const pendingTasks = computed(() => tasks.value.filter((task) => task.submissionStatus === "待提交"));
  const urgentTasks = computed(() => [...tasks.value].sort((left, right) => new Date(left.deadline).getTime() - new Date(right.deadline).getTime()).slice(0, 4));

  async function load(force = false) {
    if (workspace.value && !force) return workspace.value;
    loading.value = true;
    error.value = "";
    try {
      workspace.value = await studentApi.workspace();
      if (!messages.value.length) {
        messages.value = [{ role: "assistant", content: "我可以根据你当前的任务和提交状态给出建议，先试试右侧快捷提问。" }];
      }
      return workspace.value;
    } catch (cause) {
      error.value = (cause as Error).message;
      throw cause;
    } finally {
      loading.value = false;
    }
  }

  async function reload() {
    return load(true);
  }

  async function submitText(taskId: number, content: string) {
    workspace.value = await studentApi.submitText(taskId, content);
  }

  async function submitFile(taskId: number, file: File) {
    workspace.value = await studentApi.submitFile(taskId, file);
  }

  async function joinCourse(code: string) {
    await studentApi.joinCourse(code);
    await reload();
  }

  async function readNotification(id: number) {
    await studentApi.readNotification(id);
    await reload();
  }

  async function readAllNotifications() {
    await studentApi.readAllNotifications();
    await reload();
  }

  async function askAssistant(content: string) {
    const prompt = content.trim();
    if (!prompt || assistantBusy.value) return;
    messages.value.push({ role: "user", content: prompt });
    assistantBusy.value = true;
    try {
      const reply = await studentApi.askAssistant(prompt);
      messages.value.push({ role: "assistant", content: reply.answer });
    } catch (cause) {
      messages.value.push({ role: "assistant", content: `调用失败：${(cause as Error).message}` });
    } finally {
      assistantBusy.value = false;
    }
  }

  function taskCourse(task: StudentTask) {
    return courses.value.find((course) => course.id === task.courseId);
  }

  return {
    workspace,
    loading,
    error,
    assistantBusy,
    messages,
    tasks,
    courses,
    reports,
    notifications,
    profile,
    metrics,
    pendingTasks,
    urgentTasks,
    load,
    reload,
    submitText,
    submitFile,
    joinCourse,
    readNotification,
    readAllNotifications,
    askAssistant,
    taskCourse,
  };
});
