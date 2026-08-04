<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { BookOpen, Building2, ChevronRight, CircleHelp, GraduationCap, LayoutDashboard, LogOut, Menu, Settings2, SlidersHorizontal, Sun, UsersRound, X } from "@lucide/vue";
import { useAuthStore } from "../auth/auth.store";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const profileOpen = ref(false);
const sidebarOpen = ref(false);
const title = computed(() => String(route.meta.title || "管理后台"));
const rootCrumb = computed(() => route.name === "admin-dashboard" ? "泉州信息工程学院" : "平台管理");
const initials = computed(() => auth.user?.displayName?.slice(0, 1) || "管");
const sideSummary = computed(() => {
  if (route.path.includes("students")) return ["在用学生账号", "8,426 个"];
  if (route.path.includes("teachers")) return ["在用教师账号", "536 个"];
  if (route.path.includes("courses")) return ["课程基础库", "684 门课程"];
  if (route.path.includes("classes")) return ["当前教学班", "186 个"];
  if (route.path.includes("settings")) return ["配置更新需审计", "操作日志保留 180 天"];
  return ["系统状态：运行正常", "最后巡检 08-03 09:12"];
});
onMounted(() => auth.restore());
watch(() => route.fullPath, () => { sidebarOpen.value = false; profileOpen.value = false; });
async function logout() { await auth.logout(); await router.replace("/login"); }
</script>

<template>
  <div class="admin-shell" :class="{ 'admin-shell--menu-open': sidebarOpen }">
    <div class="admin-mobile-mask" @click="sidebarOpen = false" />
    <aside class="admin-sidebar">
      <div class="admin-brand"><span class="admin-brand-mark"><BookOpen :size="20" /></span><div><strong>知序云</strong><small>实验教学平台</small></div></div>
      <button class="admin-mobile-close" type="button" aria-label="关闭导航" @click="sidebarOpen = false"><X :size="20" /></button>
      <div class="admin-role">平台管理</div>
      <nav class="admin-nav">
        <RouterLink to="/admin/dashboard"><LayoutDashboard :size="18" /><span>首页</span></RouterLink>
        <RouterLink to="/admin/students"><UsersRound :size="18" /><span>学生管理</span></RouterLink>
        <RouterLink to="/admin/teachers"><GraduationCap :size="18" /><span>教师管理</span></RouterLink>
        <RouterLink to="/admin/courses"><BookOpen :size="18" /><span>课程管理</span></RouterLink>
        <RouterLink to="/admin/classes"><Building2 :size="18" /><span>班级管理</span></RouterLink>
        <RouterLink to="/admin/settings"><SlidersHorizontal :size="18" /><span>系统设置</span></RouterLink>
      </nav>
      <div class="admin-sidebar-foot"><span>{{ sideSummary[0] }}</span><strong>{{ sideSummary[1] }}</strong></div>
    </aside>
    <main class="admin-main">
      <header class="admin-topbar">
        <button class="admin-menu-button" type="button" aria-label="打开导航" @click="sidebarOpen = true"><Menu :size="20" /></button>
        <div class="admin-crumb"><span>{{ rootCrumb }}</span><ChevronRight :size="15" /><strong>{{ title }}</strong></div>
        <div class="admin-top-actions">
          <button class="admin-icon-button" type="button" title="帮助" aria-label="帮助"><CircleHelp :size="18" /></button>
          <button class="admin-icon-button" type="button" title="显示模式" aria-label="显示模式"><Sun :size="18" /></button>
          <button class="admin-avatar" type="button" aria-label="管理员菜单" @click="profileOpen = !profileOpen">{{ initials }}</button>
        </div>
        <div v-if="profileOpen" class="admin-profile-pop">
          <strong>{{ auth.user?.displayName }}</strong><span>{{ auth.user?.loginName }} · 管理员</span>
          <button @click="logout"><LogOut :size="16" />退出登录</button>
        </div>
      </header>
      <RouterView />
    </main>
  </div>
</template>
