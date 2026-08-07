<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { BookOpen, Building2, ChevronRight, CircleHelp, FileClock, GraduationCap, LayoutDashboard, LogOut, Menu, Moon, SlidersHorizontal, Sun, UsersRound, X } from "@lucide/vue";
import { useAuthStore } from "../auth/auth.store";
import { authApi } from "../../api/auth";
import type { UserProfile } from "../auth/auth.types";
import brandLogo from "../../assets/zhixuyun-logo.svg";
import AdminModal from "./AdminModal.vue";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const profileOpen = ref(false);
const sidebarOpen = ref(false);
const helpOpen = ref(false);
const focusMode = ref(false);
const profileModal = ref(false);
const profileBusy = ref(false);
const profileMessage = ref("");
const avatarBusy = ref(false);
const avatarUrl = ref("");
const hasAvatar = ref(false);
const profileForm = ref<Pick<UserProfile, "displayName" | "email" | "phone" | "bio">>({ displayName: "", email: "", phone: "", bio: "" });
const title = computed(() => String(route.meta.title || "管理后台"));
const rootCrumb = computed(() => route.name === "admin-dashboard" ? "泉州信息工程学院" : "平台管理");
const adminName = computed(() => {
  const value = auth.user?.displayName?.trim() || "";
  return value && !/^\?+$/.test(value) ? value : "管理员";
});
const initials = computed(() => adminName.value.slice(0, 1));
const sideSummary = computed(() => {
  if (route.path.includes("students")) return ["在用学生账号", "8,426 个"];
  if (route.path.includes("teachers")) return ["在用教师账号", "536 个"];
  if (route.path.includes("courses")) return ["课程基础库", "684 门课程"];
  if (route.path.includes("classes")) return ["当前教学班", "186 个"];
  if (route.path.includes("settings")) return ["配置更新需审计", "操作日志保留 180 天"];
  return ["系统状态：运行正常", "最后巡检 08-03 09:12"];
});
onMounted(() => {
  auth.restore();
  focusMode.value = localStorage.getItem("zhixuyun-admin-appearance") === "focus";
});
watch(() => route.fullPath, () => { sidebarOpen.value = false; profileOpen.value = false; });
async function logout() { await auth.logout(); await router.replace("/login"); }
function toggleAppearance() {
  focusMode.value = !focusMode.value;
  localStorage.setItem("zhixuyun-admin-appearance", focusMode.value ? "focus" : "default");
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
    profileModal.value = false;
  } catch (error) { profileMessage.value = (error as Error).message; }
  finally { profileBusy.value = false; }
}
async function changeAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  avatarBusy.value = true; profileMessage.value = "";
  try { const profile = await authApi.uploadAvatar(file); auth.applyProfile(profile); hasAvatar.value = profile.hasAvatar; await refreshAvatar(); }
  catch (error) { profileMessage.value = (error as Error).message; }
  finally { avatarBusy.value = false; (event.target as HTMLInputElement).value = ""; }
}
async function removeAvatar() { avatarBusy.value = true; try { const profile = await authApi.deleteAvatar(); auth.applyProfile(profile); hasAvatar.value = profile.hasAvatar; if (avatarUrl.value) URL.revokeObjectURL(avatarUrl.value); avatarUrl.value = ""; } catch (error) { profileMessage.value = (error as Error).message; } finally { avatarBusy.value = false; } }
async function refreshAvatar() { if (!hasAvatar.value) return; if (avatarUrl.value) URL.revokeObjectURL(avatarUrl.value); avatarUrl.value = await authApi.loadAvatar(); }
function openSecurity() { profileOpen.value = false; void router.push("/change-password"); }
</script>

<template>
  <div class="admin-shell" :class="{ 'admin-shell--menu-open': sidebarOpen, 'focus-mode': focusMode }">
    <div class="admin-mobile-mask" @click="sidebarOpen = false" />
    <aside class="admin-sidebar">
      <div class="admin-brand"><span class="admin-brand-mark"><img :src="brandLogo" alt="" /></span><div><strong>知序云</strong><small>实验教学平台</small></div></div>
      <button class="admin-mobile-close" type="button" aria-label="关闭导航" @click="sidebarOpen = false"><X :size="20" /></button>
      <div class="admin-role">平台管理</div>
      <nav class="admin-nav">
        <RouterLink to="/admin/dashboard"><LayoutDashboard :size="18" /><span>首页</span></RouterLink>
        <RouterLink to="/admin/students"><UsersRound :size="18" /><span>学生管理</span></RouterLink>
        <RouterLink to="/admin/teachers"><GraduationCap :size="18" /><span>教师管理</span></RouterLink>
        <RouterLink to="/admin/courses"><BookOpen :size="18" /><span>课程管理</span></RouterLink>
        <RouterLink to="/admin/classes"><Building2 :size="18" /><span>班级管理</span></RouterLink>
        <RouterLink to="/admin/logs"><FileClock :size="18" /><span>操作日志</span></RouterLink>
        <RouterLink to="/admin/settings"><SlidersHorizontal :size="18" /><span>系统设置</span></RouterLink>
      </nav>
      <div class="admin-sidebar-foot"><span>{{ sideSummary[0] }}</span><strong>{{ sideSummary[1] }}</strong></div>
    </aside>
    <main class="admin-main">
      <header class="admin-topbar">
        <button class="admin-menu-button" type="button" aria-label="打开导航" @click="sidebarOpen = true"><Menu :size="20" /></button>
        <div class="admin-crumb"><span>{{ rootCrumb }}</span><ChevronRight :size="15" /><strong>{{ title }}</strong></div>
        <div class="admin-top-actions">
          <button class="admin-icon-button" type="button" title="帮助" aria-label="帮助" @click="helpOpen = true"><CircleHelp :size="18" /></button>
          <button class="admin-icon-button" type="button" :title="focusMode ? '默认外观' : '专注外观'" :aria-label="focusMode ? '切换为默认外观' : '切换为专注外观'" :aria-pressed="focusMode" @click="toggleAppearance"><Moon v-if="focusMode" :size="18" /><Sun v-else :size="18" /></button>
          <button class="admin-avatar" type="button" aria-label="管理员菜单" @click="profileOpen = !profileOpen"><img v-if="avatarUrl" :src="avatarUrl" alt="" /><span v-else>{{ initials }}</span></button>
        </div>
        <div v-if="profileOpen" class="admin-profile-pop">
          <strong>{{ adminName }}</strong><span>{{ auth.user?.loginName }} · 管理员</span>
          <button class="admin-profile-pop__action" type="button" @click="openProfile">个人中心</button>
          <button class="admin-profile-pop__action" type="button" @click="openSecurity">账号与安全</button>
          <button @click="logout"><LogOut :size="16" />退出登录</button>
        </div>
      </header>
      <RouterView />
    </main>

    <AdminModal v-if="helpOpen" title="管理端帮助" subtitle="平台账号、教学数据与系统配置说明" @close="helpOpen = false">
      <div class="admin-help-list">
        <article><strong>账号与班级</strong><p>学生、教师与班级资料在对应管理页维护。停用、删除、重置密码等敏感操作会要求二次确认。</p></article>
        <article><strong>课程与开课安排</strong><p>课程管理维护基础课程，开课安排用于按学期、班级指定任课教师，两类数据彼此独立。</p></article>
        <article><strong>系统设置</strong><p>连接配置和系统消息在保存或发布前请再次核对。API Key 只显示掩码，不会回显完整内容。</p></article>
        <article><strong>操作日志</strong><p>管理端关键操作会进入操作日志，可按操作者、动作与时间范围追溯。</p></article>
      </div>
      <template #footer><button class="admin-primary-button" type="button" @click="helpOpen = false">知道了</button></template>
    </AdminModal>
    <AdminModal v-if="profileModal" title="个人中心" subtitle="维护管理员联系资料与个人简介" @close="profileModal = false">
      <div class="admin-form-grid"><label class="admin-field admin-field--wide"><span>头像</span><div class="admin-avatar-editor"><img v-if="avatarUrl" :src="avatarUrl" alt="当前头像" /><span v-else>{{ initials }}</span><label class="admin-secondary-button">{{ avatarBusy ? "处理中" : "上传头像" }}<input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="avatarBusy" @change="changeAvatar" /></label><button class="admin-secondary-button" type="button" :disabled="avatarBusy || !hasAvatar" @click="removeAvatar">恢复默认</button></div></label><label class="admin-field"><span>姓名</span><input v-model="profileForm.displayName" maxlength="80" /></label><label class="admin-field"><span>管理员账号</span><input :value="auth.user?.loginName" readonly /></label><label class="admin-field"><span>邮箱</span><input v-model="profileForm.email" type="email" /></label><label class="admin-field"><span>联系电话</span><input v-model="profileForm.phone" /></label><label class="admin-field admin-field--wide"><span>个人简介</span><textarea v-model="profileForm.bio" maxlength="200" /></label></div><p v-if="profileMessage" class="admin-form-error">{{ profileMessage }}</p><template #footer><button class="admin-secondary-button" type="button" :disabled="profileBusy || avatarBusy" @click="profileModal = false">取消</button><button class="admin-primary-button" type="button" :disabled="profileBusy || avatarBusy" @click="saveProfile">{{ profileBusy ? "保存中" : "保存资料" }}</button></template>
    </AdminModal>
  </div>
</template>
