<script setup lang="ts">
import { computed, ref } from "vue";
import { Eye, EyeOff, KeyRound, RefreshCw, ShieldCheck, UserRound, X } from "@lucide/vue";
import { useRoute, useRouter } from "vue-router";
import brandLogo from "../../assets/zhixuyun-logo.svg";
import { useAuthStore } from "./auth.store";
import { homeForRole } from "./auth.utils";
import { normalizeAuthError } from "./auth.utils";

type PolicyTab = "agreement" | "privacy";
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const account = ref("");
const password = ref("");
const captcha = ref("");
const captchaCode = ref(makeCaptcha());
const remember = ref(false);
const showPassword = ref(false);
const policyTab = ref<PolicyTab | null>(null);
const message = ref("");

const policySections = computed(() => policyTab.value === "privacy" ? [
  ["一、信息收集", "为完成身份认证与教学服务，平台会处理账号标识、姓名、角色、课程关系、任务提交、批改记录及必要的操作日志。"],
  ["二、信息用途", "验证身份和执行角色权限。\n支持课程管理、任务发布、报告提交、批改和教学分析。\n保障系统安全，定位异常访问和服务故障。"],
  ["三、权限与隔离", "平台按照角色、课程和班级关系限制数据访问。AI 助手不会跨账号读取历史对话，也不会访问当前用户权限以外的学生或课程数据。"],
  ["四、存储与保护", "平台采用访问控制、日志审计和必要的安全措施保护数据。教学数据的保留期限遵循学校管理要求。"],
] : [
  ["一、协议范围", "本协议适用于你访问和使用知序云实验教学平台。首次登录或继续使用平台，表示你理解并接受本协议约定。"],
  ["二、账号使用", "账号仅限本人使用，不得转借、共享或交由他人操作。应妥善保管登录密码，发现异常登录或账号泄露时应及时联系平台管理员。"],
  ["三、内容规范", "用户上传的报告、资料、评价与消息应与教学活动相关，不得上传违法内容、恶意程序或侵犯他人权益的文件。"],
  ["四、AI 辅助功能", "AI 生成内容仅作为教学建议，成绩、通知和教学安排由教师确认后生效。用户应对最终采用的内容承担审核责任。"],
]);

function makeCaptcha() {
  const chars = "23456789";
  return Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
}
function refreshCaptcha() { captchaCode.value = makeCaptcha(); captcha.value = ""; }
function errorText(error: unknown) {
  const normalized = normalizeAuthError(error);
  if (normalized.code === "ACCOUNT_DISABLED") return "账号已被禁用，请联系管理员";
  if (normalized.code === "INVALID_CREDENTIALS") return "账号或密码不正确";
  if (normalized.code === "NETWORK_ERROR") return "暂时无法连接服务，请稍后再试";
  return normalized.message || "登录失败，请稍后再试";
}
async function submit() {
  message.value = "";
  if (!account.value.trim() || !password.value) { message.value = "请输入账号和密码"; return; }
  if (captcha.value.trim().toUpperCase() !== captchaCode.value) { message.value = "验证码不正确，请重新输入"; refreshCaptcha(); return; }
  try {
    const user = await auth.login(account.value, password.value, remember.value);
    const redirect = user.mustChangePassword ? "/change-password" : typeof route.query.redirect === "string" ? route.query.redirect : homeForRole(user.role);
    await router.replace(redirect);
  } catch (error) { message.value = errorText(error); refreshCaptcha(); }
}
</script>

<template>
  <main class="login-page">
    <section class="brand-panel">
      <div class="brand-lockup"><img class="brand-symbol" :src="brandLogo" alt="" /><span><strong>知序云</strong><small>实验教学平台</small></span></div>
      <div class="circuit-line line-one"><i /></div><div class="circuit-line line-two"><i /></div><div class="circuit-line line-three"><i /></div>
      <div class="brand-content"><h1>让教学任务、学习过程<br />与评价反馈保持有序</h1><p>知序云连接课程管理、实验任务、报告批改与教学分析，为教师和学生提供清晰一致的实验教学空间。</p></div>
      <div class="book-mark" aria-hidden="true"><div class="cloud" /><div class="book-left" /><div class="book-right" /></div>
      <small class="school-mark">泉州信息工程学院</small><small class="copyright">ZHIXUYUN · 2026</small>
    </section>
    <section class="signin-panel"><form class="signin-form" @submit.prevent="submit">
      <p class="entry-note"><span />泉州信息工程学院统一登录入口</p><h2>欢迎登录</h2><p class="intro">使用你的平台账号继续访问知序云。</p>
      <label>账号<div class="field-control"><UserRound :size="18" /><input v-model="account" autocomplete="username" placeholder="请输入学号、邮箱或管理员账号" /></div></label>
      <label>密码<div class="field-control"><KeyRound :size="18" /><input v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" /><button type="button" class="icon-button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></div></label>
      <label>验证码<div class="captcha-row"><div class="field-control"><ShieldCheck :size="18" /><input v-model="captcha" inputmode="numeric" maxlength="4" placeholder="输入四位数字" /></div><button type="button" class="captcha-code" aria-label="刷新验证码" @click="refreshCaptcha"><b v-for="(char, index) in captchaCode" :key="`${char}-${index}`">{{ char }}</b></button><button type="button" class="refresh-button" aria-label="刷新验证码" @click="refreshCaptcha"><RefreshCw :size="19" /></button></div></label>
      <p v-if="message" class="form-message" role="alert">{{ message }}</p>
      <label class="remember"><input v-model="remember" type="checkbox" />记住本次登录</label>
      <button class="login-button" type="submit" :disabled="auth.loading">{{ auth.loading ? "正在登录" : "登录" }}</button>
      <p class="consent">登录即表示你已阅读并同意 <button type="button" @click="policyTab = 'agreement'">《用户协议》</button> 和 <button type="button" @click="policyTab = 'privacy'">《隐私政策》</button></p>
      <div class="form-footer">请妥善保管账号信息，不要在公共设备上保存密码。</div>
    </form></section>
    <div v-if="policyTab" class="modal-backdrop" @click.self="policyTab = null"><section class="policy-modal" role="dialog" aria-modal="true"><header><div><h2>平台服务协议</h2><p>更新日期：2026年8月2日</p></div><button type="button" class="close-button" aria-label="关闭" @click="policyTab = null"><X :size="21" /></button></header><nav class="policy-tabs"><button type="button" :class="{ active: policyTab === 'agreement' }" @click="policyTab = 'agreement'">用户协议</button><button type="button" :class="{ active: policyTab === 'privacy' }" @click="policyTab = 'privacy'">隐私政策</button></nav><div class="policy-content"><article v-for="([heading, body], index) in policySections" :key="index"><h3>{{ heading }}</h3><p v-for="part in body.split('\n')" :key="part">{{ part }}</p></article></div><footer><span>完整协议以学校正式发布版本为准</span><button type="button" @click="policyTab = null">我知道了</button></footer></section></div>
  </main>
</template>
