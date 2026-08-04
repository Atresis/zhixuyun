import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { teacherApi } from "./teacher.api";
import type { TeacherWorkspace } from "./teacher.types";

export const useTeacherStore = defineStore("teacher", () => {
  const workspace = ref<TeacherWorkspace | null>(null);
  const loading = ref(false);
  const error = ref("");
  const unreadAlerts = computed(() => workspace.value?.alerts.filter((item) => item.status === "UNREAD").length || 0);
  const unreadMessages = computed(() => workspace.value?.conversations.reduce((sum, item) => sum + item.unreadCount, 0) || 0);

  async function load(force = false) {
    if (workspace.value && !force) return workspace.value;
    loading.value = true; error.value = "";
    try { workspace.value = await teacherApi.workspace(); return workspace.value; }
    catch (cause) { error.value = (cause as Error).message; throw cause; }
    finally { loading.value = false; }
  }
  async function reload() { return load(true); }
  return { workspace, loading, error, unreadAlerts, unreadMessages, load, reload };
});
