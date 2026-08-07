<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Bell, BookOpen, ChevronRight, CircleHelp, House, LogOut, Menu, MessageCircleMore, Moon, Sun, X } from "@lucide/vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../auth/auth.store";
import { authApi } from "../../api/auth";
import type { UserProfile } from "../auth/auth.types";
import { useStudentStore } from "./student.store";
import brandLogo from "../../assets/zhixuyun-logo.svg";
import "./student.css";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const student = useStudentStore();
const mobileOpen = ref(false);
const profileOpen = ref(false);
const helpOpen = ref(false);
const focusMode = ref(false);
const profileModal = ref(false);
const profileBusy = ref(false);
const profileMessage = ref("");
const avatarBusy = ref(false);
const avatarUrl = ref("");
const hasAvatar = ref(false);
const profileForm = ref<Pick<UserProfile, "displayName" | "email" | "phone" | "bio">>({ displayName: "", email: "", phone: "", bio: "" });
const title = computed(() => String(route.meta.title || "课程首页"));
const unreadCount = computed(() => student.notifications.filter((item) => item.read === false).length + student.conversations.reduce((sum, item) => sum + item.unreadCount, 0));
const courseDetailPath = computed(() => student.courses[0] ? `/student/courses/${student.courses[0].id}` : "/student/courses/select");
const currentSemester = computed(() => student.courses[0]?.semester || "当前学期");

async function refreshWorkspace() {
  if (student.loading) return;
  try {
    await student.reload();
  } catch {
    // The store exposes the request error and keeps the last successful workspace visible.
  }
}

function refreshWhenVisible() {
  if (document.visibilityState === "visible") void refreshWorkspace();
}

watch(() => route.fullPath, () => void refreshWorkspace(), { immediate: true });

onMounted(() => {
  focusMode.value = localStorage.getItem("zhixuyun-student-appearance") === "focus";
  window.addEventListener("focus", refreshWorkspace);
  document.addEventListener("visibilitychange", refreshWhenVisible);
});

onBeforeUnmount(() => {
  window.removeEventListener("focus", refreshWorkspace);
  document.removeEventListener("visibilitychange", refreshWhenVisible);
});

async function logout() {
  profileOpen.value = false;
  await auth.logout();
  await router.replace("/login");
}

function closeMobile() {
  mobileOpen.value = false;
}

function toggleAppearance() {
  focusMode.value = !focusMode.value;
  localStorage.setItem("zhixuyun-student-appearance", focusMode.value ? "focus" : "default");
}

async function openProfile() {
  profileOpen.value = false;
  profileMessage.value = "";
  try {
    const profile = await authApi.profile();
    profileForm.value = { displayName: profile.displayName, email: profile.email, phone: profile.phone, bio: profile.bio }; hasAvatar.value = profile.hasAvatar;
    await refreshAvatar();
    profileModal.value = true;
  } catch (error) { profileMessage.value = (error as Error).message; }
}

async function saveProfile() {
  profileBusy.value = true;
  profileMessage.value = "";
  try {
    const profile = await authApi.updateProfile(profileForm.value);
    auth.applyProfile(profile);
    await student.reload();
    profileModal.value = false;
  } catch (error) { profileMessage.value = (error as Error).message; }
  finally { profileBusy.value = false; }
}
async function changeAvatar(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return; avatarBusy.value = true; profileMessage.value = ""; try { const profile = await authApi.uploadAvatar(file); auth.applyProfile(profile); hasAvatar.value = profile.hasAvatar; await refreshAvatar(); } catch (error) { profileMessage.value = (error as Error).message; } finally { avatarBusy.value = false; (event.target as HTMLInputElement).value = ""; } }
async function removeAvatar() { avatarBusy.value = true; try { const profile = await authApi.deleteAvatar(); auth.applyProfile(profile); hasAvatar.value = profile.hasAvatar; if (avatarUrl.value) URL.revokeObjectURL(avatarUrl.value); avatarUrl.value = ""; } catch (error) { profileMessage.value = (error as Error).message; } finally { avatarBusy.value = false; } }
async function refreshAvatar() { if (!hasAvatar.value) return; if (avatarUrl.value) URL.revokeObjectURL(avatarUrl.value); avatarUrl.value = await authApi.loadAvatar(); }

function openSecurity() { profileOpen.value = false; void router.push("/change-password"); }
</script>

<template>
  <div class="student-shell" :class="{ 'focus-mode': focusMode }">
    <div v-if="mobileOpen" class="student-mobile-scrim" @click="closeMobile"></div>
    <aside class="student-sidebar" :class="{ open: mobileOpen }">
      <div class="student-brand">
        <span class="student-brand-mark"><img :src="brandLogo" alt="" /></span>
        <div><strong>知序云</strong><small>实验教学平台</small></div>
        <button class="student-icon-button student-mobile-close" aria-label="关闭菜单" title="关闭菜单" @click="closeMobile"><X :size="18" /></button>
      </div>
      <div class="student-space-label">学习空间</div>
      <nav class="student-nav" aria-label="学生端主导航">
        <RouterLink class="student-nav-link" to="/student/courses" @click="closeMobile"><House :size="17" />课程首页</RouterLink>
        <RouterLink class="student-nav-link" :to="courseDetailPath" @click="closeMobile"><BookOpen :size="17" />课程详情</RouterLink>
        <RouterLink class="student-nav-link" to="/student/assistant" @click="closeMobile"><MessageCircleMore :size="17" />AI 问答</RouterLink>
        <RouterLink class="student-nav-link" to="/student/messages" @click="closeMobile"><Bell :size="17" />消息通知 <span v-if="unreadCount" class="student-nav-badge">{{ unreadCount > 99 ? "99+" : unreadCount }}</span></RouterLink>
      </nav>
      <div class="student-sidebar-foot">
        <span>{{ currentSemester }}</span>
        <strong>当前课程 {{ student.courses.length }} 门</strong>
      </div>
    </aside>

    <main class="student-main">
      <header class="student-topbar">
        <div class="student-topbar-left">
          <button class="student-icon-button student-mobile-menu" aria-label="打开菜单" title="打开菜单" @click="mobileOpen = true"><Menu :size="19" /></button>
          <div class="student-crumb"><span>泉州信息工程学院</span><ChevronRight :size="14" /><strong>{{ title }}</strong></div>
        </div>
        <div class="student-topbar-actions">
          <button class="student-topbar-tool" type="button" aria-label="帮助中心" title="帮助中心" @click="helpOpen = true"><CircleHelp :size="18" /></button>
          <button class="student-topbar-tool" type="button" :aria-label="focusMode ? '切换为默认外观' : '切换为护眼外观'" :title="focusMode ? '默认外观' : '护眼外观'" :aria-pressed="focusMode" @click="toggleAppearance"><Moon v-if="focusMode" :size="18" /><Sun v-else :size="18" /></button>
          <button class="student-avatar-button" type="button" aria-label="个人菜单" @click="profileOpen = !profileOpen"><img v-if="avatarUrl" :src="avatarUrl" alt="" /><span v-else>{{ (student.profile?.displayName || auth.user?.displayName || "学").slice(0, 1) }}</span></button>
        </div>
      </header>
      <div v-if="profileOpen" class="student-profile-pop"><strong>{{ student.profile?.displayName || auth.user?.displayName || "学生" }}</strong><span>{{ student.profile?.studentNo }}</span><button type="button" @click="openProfile">个人中心</button><button type="button" @click="openSecurity">账号与安全</button><button type="button" @click="logout"><LogOut :size="15" />退出登录</button></div>
      <div v-if="student.loading && !student.workspace" class="student-state-page"><span class="student-loader"></span>正在载入课程数据</div>
      <div v-else-if="student.error && !student.workspace" class="student-state-page student-error-state"><strong>课程数据加载失败</strong><p>{{ student.error }}</p><button class="student-button primary" @click="student.reload">重新加载</button></div>
      <RouterView v-else />
    </main>

    <div v-if="helpOpen" class="student-modal-layer" role="presentation" @click.self="helpOpen = false">
      <section class="student-modal student-modal-small" role="dialog" aria-modal="true" aria-labelledby="student-help-title">
        <header class="student-modal-head"><div><span class="student-eyebrow">帮助中心</span><h2 id="student-help-title">使用帮助</h2><p>课程学习与任务提交常见问题</p></div><button class="student-icon-button" type="button" aria-label="关闭帮助" title="关闭" @click="helpOpen = false"><X :size="18" /></button></header>
        <div class="student-modal-body student-help-list">
          <article><strong>课程与资料</strong><p>在“课程详情”中查看作业、实验和课程资料。课程未显示时，可先刷新页面或联系任课教师确认选课名单。</p></article>
          <article><strong>任务提交</strong><p>提交后如需修改，请联系教师执行“退回重交”。收到退回通知后，可在原任务中提交新版本，历史版本会继续保留。</p></article>
          <article><strong>消息与 AI 问答</strong><p>教师通知集中在“消息通知”；课程问题可在“AI 问答”中选择课程后发起咨询。</p></article>
          <article><strong>仍需协助</strong><p>请携带学号、课程名称和问题截图联系任课教师或平台管理员。</p></article>
        </div>
        <footer class="student-modal-foot"><button class="student-button primary" type="button" @click="helpOpen = false">知道了</button></footer>
      </section>
    </div>

    <div v-if="profileModal" class="student-modal-layer" role="presentation" @click.self="profileModal = false">
      <section class="student-modal student-modal-small" role="dialog" aria-modal="true" aria-labelledby="student-profile-title">
        <header class="student-modal-head"><div><span class="student-eyebrow">个人中心</span><h2 id="student-profile-title">个人资料</h2><p>维护可公开展示的联系资料</p></div><button class="student-icon-button" type="button" aria-label="关闭个人中心" title="关闭" @click="profileModal = false"><X :size="18" /></button></header>
        <div class="student-modal-body"><label class="student-field"><span>头像</span><div class="student-avatar-editor"><img v-if="avatarUrl" :src="avatarUrl" alt="当前头像" /><span v-else>{{ (profileForm.displayName || "学").slice(0, 1) }}</span><label class="student-button secondary">{{ avatarBusy ? "处理中" : "上传头像" }}<input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="avatarBusy" @change="changeAvatar" /></label><button class="student-button secondary" type="button" :disabled="avatarBusy || !hasAvatar" @click="removeAvatar">恢复默认</button></div></label><label class="student-field"><span>姓名</span><input v-model="profileForm.displayName" class="student-input" maxlength="80" /></label><label class="student-field"><span>邮箱</span><input v-model="profileForm.email" class="student-input" type="email" /></label><label class="student-field"><span>联系电话</span><input v-model="profileForm.phone" class="student-input" /></label><label class="student-field"><span>个人简介</span><textarea v-model="profileForm.bio" class="student-textarea" maxlength="200" /></label><div class="student-profile-facts"><span>学号<strong>{{ student.profile?.studentNo || auth.user?.loginName }}</strong></span><span>行政班<strong>{{ student.profile?.className || "未分班" }}</strong></span></div><p v-if="profileMessage" class="student-form-error">{{ profileMessage }}</p></div>
        <footer class="student-modal-foot"><button class="student-button secondary" type="button" :disabled="profileBusy" @click="profileModal = false">取消</button><button class="student-button primary" type="button" :disabled="profileBusy" @click="saveProfile">{{ profileBusy ? "保存中" : "保存资料" }}</button></footer>
      </section>
    </div>
  </div>
</template>
