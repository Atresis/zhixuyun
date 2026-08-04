<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { AlertTriangle, Eye, FileClock, PlugZap, Save, Send, ShieldCheck } from "@lucide/vue";
import { useAdminStore } from "./admin.store";

const store = useAdminStore(); const saving = ref(false); const message = ref("");
const form = reactive({ aiApiUrl: "https://api.example.edu.cn/v1", aiModel: "teaching-assistant-v2", aiTimeoutSeconds: "60", aiFeatures: "AI 助手、AI 初评、AI 预警", aiDailyLimit: "20000", aiRetryCount: "2", aiAttribution: "隐藏 AI 操作并显示配置提示", announcementScope: "全部用户", announcementLevel: "普通通知", announcementTitle: "知序云系统维护通知", announcementContent: "平台将于 2026年8月8日 00:00 至 02:00 进行例行维护。维护期间登录、作业提交和批改功能可能短暂不可用，请提前保存正在编辑的内容。", announcementTime: "立即发布", announcementExpiresAt: "2026-08-15 23:59" });
onMounted(async () => { await store.loadSettings(); Object.assign(form, Object.fromEntries(Object.entries(store.settings).filter(([key]) => key in form))); });
async function persist(success: string) { saving.value = true; message.value = ""; try { store.settings = { ...store.settings, ...form }; await store.saveSettings(); message.value = success; } catch (error) { message.value = (error as Error).message; } finally { saving.value = false; } }
function testConnection() { try { new URL(form.aiApiUrl); message.value = "接口地址格式有效。连接凭据由服务端环境变量管理。"; } catch { message.value = "请输入有效的 API URL。"; } }
</script>

<template>
  <section class="admin-page admin-settings-page">
    <div class="admin-page-head"><div><h1>系统设置</h1><p>配置平台 AI 服务与系统通知。密钥更新和消息发布会写入管理员审计日志。</p></div><button class="admin-secondary-button"><FileClock :size="16" />查看操作日志</button></div>
    <div class="admin-settings-top">
      <section class="admin-panel admin-settings-card"><header class="admin-panel-head"><div><h2>AI 服务接口</h2><p>用于 AI 教学助手、AI 初评和教学预警</p></div><span class="admin-status">连接正常</span></header><div class="admin-settings-body">
        <label class="admin-field"><span>API URL</span><input v-model="form.aiApiUrl" /><small>仅允许使用 HTTPS 地址，保存前会执行连通性检查。</small></label>
        <label class="admin-field"><span>API Key</span><span class="admin-secret-field"><input value="••••••••••••••••••••••••" readonly /><Eye :size="16" /></span><small>密钥仅显示掩码，完整内容不会出现在日志中。</small></label>
        <div class="admin-form-grid"><label class="admin-field"><span>默认模型</span><input v-model="form.aiModel" /></label><label class="admin-field"><span>请求超时（秒）</span><input v-model="form.aiTimeoutSeconds" type="number" min="10" /></label></div>
        <div class="admin-connection-state"><span>● 最近连接成功</span><small>2026-08-03 09:12 · 平均响应 1.8 秒</small></div><div class="admin-card-footer"><button class="admin-secondary-button" @click="testConnection"><PlugZap :size="16" />测试连接</button><button class="admin-primary-button" :disabled="saving" @click="persist('接口配置已保存')"><Save :size="16" />保存接口配置</button></div>
      </div></section>
      <section class="admin-panel admin-settings-card"><header class="admin-panel-head"><div><h2>服务安全策略</h2><p>控制调用范围与异常处理方式</p></div><ShieldCheck :size="18" /></header><div class="admin-settings-body">
        <label class="admin-field"><span>允许调用的功能</span><select v-model="form.aiFeatures"><option>AI 助手、AI 初评、AI 预警</option><option>仅 AI 助手</option><option>暂停全部 AI 功能</option></select></label><div class="admin-form-grid"><label class="admin-field"><span>每日调用上限</span><input v-model="form.aiDailyLimit" type="number" /></label><label class="admin-field"><span>失败重试次数</span><input v-model="form.aiRetryCount" type="number" min="0" max="5" /></label></div><label class="admin-field"><span>AI 未配置时</span><select v-model="form.aiAttribution"><option>隐藏 AI 操作并显示配置提示</option><option>保留入口并显示停用提示</option></select></label><div class="admin-warning-strip"><AlertTriangle :size="16" />暂停 AI 功能不会删除历史生成记录，但教师端与学生端将暂时无法生成新内容。</div><div class="admin-card-footer"><button class="admin-primary-button" :disabled="saving" @click="persist('安全策略已保存')">保存安全策略</button></div>
      </div></section>
    </div>
    <section class="admin-panel admin-announcement-card"><header class="admin-panel-head"><div><h2>发布系统消息</h2><p>消息将进入教师或学生端的“系统通知”会话</p></div><span class="admin-status info">发布前需确认</span></header><div class="admin-announcement-grid"><form class="admin-announcement-form" @submit.prevent="persist('系统消息已保存并进入发布队列')"><div class="admin-form-grid"><label class="admin-field"><span>接收范围</span><select v-model="form.announcementScope"><option>全部用户</option><option>仅教师</option><option>仅学生</option></select></label><label class="admin-field"><span>消息级别</span><select v-model="form.announcementLevel"><option>普通通知</option><option>系统维护</option><option>紧急通知</option></select></label><label class="admin-field admin-field--wide"><span>消息标题</span><input v-model="form.announcementTitle" /></label><label class="admin-field admin-field--wide"><span>消息内容</span><textarea v-model="form.announcementContent" /></label><label class="admin-field"><span>发布时间</span><select v-model="form.announcementTime"><option>立即发布</option><option>定时发布</option></select></label><label class="admin-field"><span>消息有效期</span><input v-model="form.announcementExpiresAt" /></label></div><div class="admin-card-footer"><button class="admin-secondary-button" type="button" @click="persist('通知草稿已保存')">保存草稿</button><button class="admin-primary-button" :disabled="saving"><Send :size="16" />确认并发布消息</button></div></form><aside class="admin-announcement-preview"><strong>系统通知</strong><small>知序云平台 · 刚刚</small><h3>{{ form.announcementTitle || "消息标题" }}</h3><p>{{ form.announcementContent || "消息内容" }}</p><span class="admin-status info">{{ form.announcementLevel }}</span></aside></div></section>
    <p v-if="message" class="admin-settings-message">{{ message }}</p>
  </section>
</template>
