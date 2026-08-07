<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { BookOpen, CircleHelp, LayoutDashboard, Menu, MessageSquare, Moon, Sparkles, Sun, TriangleAlert, X } from "@lucide/vue";
import { useAuthStore } from "../auth/auth.store";
import { useTeacherStore } from "./teacher.store";
import brandLogo from "../../assets/zhixuyun-logo.svg";
import TeacherModal from "./TeacherModal.vue";
import "./teacher-design.css";
import "./teacher-app.css";

const route = useRoute(); const router = useRouter(); const auth = useAuthStore(); const teacher = useTeacherStore();
const profileOpen = ref(false); const helpOpen = ref(false); const profileModal = ref(false); const dark = ref(false); const mobileOpen = ref(false);
const title = computed(() => String(route.meta.title || "教师工作台"));
const profile = computed(() => teacher.workspace?.profile);
const initials = computed(() => profile.value?.displayName?.slice(0, 1) || "师");
const form = ref({ displayName: "", department: "", title: "", email: "", phone: "", bio: "" });

onMounted(async () => {
  dark.value = localStorage.getItem("zhixuyun-teacher-appearance") === "focus";
  document.body.classList.toggle("teacher-focus", dark.value);
  await teacher.load();
  syncForm();
});
onBeforeUnmount(() => document.body.classList.remove("teacher-focus"));
function syncForm() { if (profile.value) form.value = { ...profile.value }; }
function openProfile() { syncForm(); profileOpen.value = false; profileModal.value = true; }
async function saveProfile() { const { teacherApi } = await import("./teacher.api"); await teacherApi.updateProfile(form.value); await teacher.reload(); profileModal.value = false; }
function toggleTheme() { dark.value = !dark.value; document.body.classList.toggle("teacher-focus", dark.value); localStorage.setItem("zhixuyun-teacher-appearance", dark.value ? "focus" : "default"); }
async function logout() { await auth.logout(); await router.replace("/login"); }
</script>

<template>
  <div class="app-shell">
    <div v-if="mobileOpen" class="teacher-mobile-scrim" @click="mobileOpen = false"></div>
    <aside class="sidebar" :class="{ open: mobileOpen }">
      <div class="brand"><span class="brand-mark"><img :src="brandLogo" alt="" /></span><div><strong>知序云</strong><small>实验教学平台</small></div></div>
      <button class="teacher-mobile-close" type="button" aria-label="关闭导航" @click="mobileOpen = false"><X :size="19" /></button>
      <p class="sidebar-label">教师工作台</p>
      <nav>
        <RouterLink class="nav-link" to="/teacher/dashboard" @click="mobileOpen = false"><LayoutDashboard :size="18" /><span>首页</span></RouterLink>
        <RouterLink class="nav-link" to="/teacher/courses" @click="mobileOpen = false"><BookOpen :size="18" /><span>课程管理</span></RouterLink>
        <RouterLink class="nav-link" to="/teacher/assistant" @click="mobileOpen = false"><Sparkles :size="18" /><span>AI 教学助手</span></RouterLink>
        <RouterLink class="nav-link" to="/teacher/alerts" @click="mobileOpen = false"><TriangleAlert :size="18" /><span>AI 教学预警</span><em v-if="teacher.unreadAlerts" class="badge">{{ teacher.unreadAlerts }}</em></RouterLink>
        <RouterLink class="nav-link" to="/teacher/messages" @click="mobileOpen = false"><MessageSquare :size="18" /><span>消息通知</span><em v-if="teacher.unreadMessages" class="badge">{{ teacher.unreadMessages }}</em></RouterLink>
      </nav>
      <div class="sidebar-foot">数据更新时间<br /><strong>{{ new Date().toLocaleDateString('zh-CN') }}</strong></div>
    </aside>
    <main class="main">
      <header class="topbar"><div class="teacher-topbar-left"><button class="teacher-mobile-menu" type="button" aria-label="打开导航" @click="mobileOpen = true"><Menu :size="19" /></button><div class="crumb"><span>泉州信息工程学院</span><span>›</span><strong>{{ title }}</strong></div></div><div class="top-actions">
        <button class="icon-btn" title="帮助" @click="helpOpen = true"><CircleHelp :size="18" /></button>
        <button class="icon-btn" title="切换界面风格" @click="toggleTheme"><Sun v-if="!dark" :size="18" /><Moon v-else :size="18" /></button>
        <button class="avatar-btn" aria-label="个人菜单" @click="profileOpen = !profileOpen">{{ initials }}</button>
      </div></header>
      <div class="profile-pop" :class="{ open: profileOpen }"><div class="profile-head"><span class="avatar-btn">{{ initials }}</span><div><strong>{{ profile?.displayName }}</strong><span>{{ profile?.department }} · {{ profile?.title }}</span></div></div><button @click="openProfile">个人中心</button><button @click="openProfile">账号与安全</button><button @click="logout">退出登录</button></div>
      <div v-if="!teacher.workspace && !teacher.error" class="teacher-loading">正在载入教师工作台...</div>
      <div v-else-if="teacher.error && !teacher.workspace" class="teacher-error"><strong>数据加载失败</strong><span>{{ teacher.error }}</span><button class="btn primary" @click="teacher.load(true)">重新加载</button></div>
      <RouterView v-else />
    </main>
  </div>

  <TeacherModal v-if="helpOpen" title="教师端帮助" subtitle="当前工作台说明" size="small" @close="helpOpen = false"><div class="modal-body"><div class="resource-list"><div class="resource-row"><div class="resource-main"><strong>课程工作区</strong><span>双击课程卡片进入课程、作业、实验和资料模块。</span></div></div><div class="resource-row"><div class="resource-main"><strong>教学数据</strong><span>当前页面只读取本人授课课程和当前账号历史。</span></div></div></div></div></TeacherModal>
  <TeacherModal v-if="profileModal" title="个人中心" subtitle="维护教师资料与账号偏好" size="small" @close="profileModal = false"><div class="modal-body"><div class="form-grid"><label class="field-label">姓名<input v-model="form.displayName" /></label><label class="field-label">邮箱<input v-model="form.email" /></label><label class="field-label">联系电话<input v-model="form.phone" /></label><label class="field-label">所属院系<input v-model="form.department" /></label><label class="field-label">职称<input v-model="form.title" /></label><label class="field-label full">个人简介<textarea v-model="form.bio" /></label></div></div><footer class="modal-footer"><button class="btn" @click="profileModal = false">取消</button><button class="btn primary" @click="saveProfile">保存个人资料</button></footer></TeacherModal>
</template>
