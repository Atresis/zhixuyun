<script setup lang="ts">
import { computed, onMounted } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { Bell, BookOpen, ClipboardList, House, LogOut, MessageCircleMore, ScrollText } from "@lucide/vue";
import { useAuthStore } from "../auth/auth.store";
import { useStudentStore } from "./student.store";
import "./student.css";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const student = useStudentStore();
const title = computed(() => String(route.meta.title || "学生工作台"));

onMounted(async () => {
  await student.load();
});

async function logout() {
  await auth.logout();
  await router.replace("/login");
}
</script>

<template>
  <div class="student-shell">
    <aside class="student-sidebar">
      <div class="student-brand">
        <span class="student-brand-mark">知</span>
        <div><strong>知序云</strong><small>学生实验学习端</small></div>
      </div>
      <div class="student-user">
        <strong>{{ student.profile?.displayName || auth.user?.displayName }}</strong>
        <span>{{ student.profile?.className || "正在载入班级信息" }}</span>
      </div>
      <nav class="student-nav">
        <RouterLink class="student-nav-link" to="/student/dashboard"><House :size="18" />学生首页</RouterLink>
        <RouterLink class="student-nav-link" to="/student/tasks"><ClipboardList :size="18" />实验任务</RouterLink>
        <RouterLink class="student-nav-link" to="/student/courses"><BookOpen :size="18" />我的课程</RouterLink>
        <RouterLink class="student-nav-link" to="/student/reports"><ScrollText :size="18" />我的报告</RouterLink>
        <RouterLink class="student-nav-link" to="/student/assistant"><MessageCircleMore :size="18" />AI 问答</RouterLink>
        <RouterLink class="student-nav-link" to="/student/notifications"><Bell :size="18" />消息通知</RouterLink>
      </nav>
      <div class="student-sidebar-foot">当前数据范围<br /><strong>{{ student.profile?.studentNo || "学号载入中" }}</strong></div>
    </aside>
    <main class="student-main">
      <header class="student-topbar">
        <div class="student-crumb">
          <span>泉州信息工程学院</span>
          <strong>{{ title }}</strong>
        </div>
        <button class="student-logout" @click="logout"><LogOut :size="16" />退出登录</button>
      </header>
      <div v-if="student.loading && !student.workspace" class="student-empty">正在载入学生工作台...</div>
      <div v-else-if="student.error && !student.workspace" class="student-empty">{{ student.error }}</div>
      <RouterView v-else />
    </main>
  </div>
</template>
