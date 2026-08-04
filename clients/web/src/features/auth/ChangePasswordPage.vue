<script setup lang="ts">
import { ref } from "vue";
import { KeyRound, ShieldCheck } from "@lucide/vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "./auth.store";

const auth = useAuthStore();
const router = useRouter();
const oldPassword = ref("");
const newPassword = ref("");
const confirmation = ref("");
const message = ref("");
const busy = ref(false);

async function submit() {
  message.value = "";
  if (newPassword.value.length < 6) { message.value = "新密码至少需要 6 个字符"; return; }
  if (newPassword.value !== confirmation.value) { message.value = "两次输入的新密码不一致"; return; }
  busy.value = true;
  try {
    await auth.changePassword(oldPassword.value, newPassword.value);
    await router.replace({ path: "/login", query: { passwordChanged: "1" } });
  } catch (error) { message.value = (error as Error).message; }
  finally { busy.value = false; }
}
</script>

<template>
  <main class="login-page password-page">
    <section class="brand-panel">
      <div class="brand-lockup"><span class="brand-symbol"><ShieldCheck :size="28" /></span><span><strong>知序云</strong><small>账号安全</small></span></div>
      <div class="brand-content"><h1>首次登录需要<br />设置新的个人密码</h1><p>完成修改后，当前会话会安全退出。请使用新密码重新登录。</p></div>
      <small class="school-mark">泉州信息工程学院</small>
    </section>
    <section class="signin-panel"><form class="signin-form" @submit.prevent="submit">
      <p class="entry-note"><span />首次登录安全检查</p><h2>修改初始密码</h2><p class="intro">新密码至少 6 个字符，且不要与初始密码相同。</p>
      <label>当前密码<div class="field-control"><KeyRound :size="18" /><input v-model="oldPassword" type="password" autocomplete="current-password" /></div></label>
      <label>新密码<div class="field-control"><KeyRound :size="18" /><input v-model="newPassword" type="password" autocomplete="new-password" /></div></label>
      <label>确认新密码<div class="field-control"><KeyRound :size="18" /><input v-model="confirmation" type="password" autocomplete="new-password" /></div></label>
      <p v-if="message" class="form-message" role="alert">{{ message }}</p>
      <button class="login-button" type="submit" :disabled="busy">{{ busy ? "正在修改" : "修改并重新登录" }}</button>
    </form></section>
  </main>
</template>
