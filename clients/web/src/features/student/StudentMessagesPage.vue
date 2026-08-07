<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Bell, MessageCircle, Plus, Send, UserRound, X } from "@lucide/vue";
import { useStudentStore } from "./student.store";

const student = useStudentStore();
const selectedId = ref<number | null>(null);
const selectedKind = ref<"SYSTEM" | "TEACHER">("SYSTEM");
const input = ref("");
const busy = ref(false);
const feedback = ref("");
const newOpen = ref(false);
const teacherId = ref<number | null>(null);
const firstMessage = ref("");
const selected = computed(() => selectedKind.value === "TEACHER" ? student.conversations.find((item) => item.id === selectedId.value) || null : null);
const systemNotices = computed(() => student.notifications);
const systemUnreadCount = computed(() => systemNotices.value.filter((item) => item.read === false).length);

watch(() => student.conversations, (items) => {
  if (selectedKind.value === "TEACHER" && !items.some((item) => item.id === selectedId.value)) selectedKind.value = "SYSTEM";
}, { immediate: true });

async function selectConversation(id: number) {
  selectedKind.value = "TEACHER";
  selectedId.value = id;
  await student.readConversation(id).catch(() => undefined);
}

async function selectSystem() {
  selectedKind.value = "SYSTEM";
  if (!systemUnreadCount.value) return;
  await student.readAllNotifications().catch(() => undefined);
}

async function send() {
  if (!selected.value || !input.value.trim() || busy.value) return;
  busy.value = true; feedback.value = "";
  try { await student.sendConversationMessage(selected.value.id, input.value.trim()); input.value = ""; }
  catch (error) { feedback.value = (error as Error).message; }
  finally { busy.value = false; }
}

async function createConversation() {
  if (!teacherId.value || !firstMessage.value.trim() || busy.value) return;
  busy.value = true; feedback.value = "";
  try { const conversation = await student.createConversation(teacherId.value, firstMessage.value.trim()); selectedKind.value = "TEACHER"; selectedId.value = conversation.id; firstMessage.value = ""; teacherId.value = null; newOpen.value = false; }
  catch (error) { feedback.value = (error as Error).message; }
  finally { busy.value = false; }
}
</script>

<template>
  <section class="student-page student-messages-page">
    <div class="student-page-heading"><div><h1>消息通知</h1><p>查看系统通知，与任课教师保持联系。</p></div><button class="student-button primary" @click="newOpen = true"><Plus :size="16" />发起新会话</button></div>
    <div class="student-message-layout">
      <aside class="student-conversation-list">
        <div class="student-section-label">消息 <span>{{ student.conversations.length + 1 }}</span></div>
        <button class="student-conversation-item student-system-conversation" :class="{ active: selectedKind === 'SYSTEM' }" @click="selectSystem"><span class="student-system-icon"><Bell :size="14" /></span><span><strong>系统通知</strong><small>{{ systemNotices[0]?.content || '知序云平台服务消息' }}</small></span><em v-if="systemUnreadCount">{{ systemUnreadCount > 99 ? '99+' : systemUnreadCount }}</em></button>
        <button v-for="conversation in student.conversations" :key="conversation.id" class="student-conversation-item" :class="{ active: selectedKind === 'TEACHER' && conversation.id === selectedId }" @click="selectConversation(conversation.id)"><span class="student-avatar conversation">{{ conversation.avatarText }}</span><span><strong>{{ conversation.contactName }}</strong><small>{{ conversation.messages[conversation.messages.length - 1]?.content || '暂无消息' }}</small></span><em v-if="conversation.unreadCount">{{ conversation.unreadCount }}</em></button>
        <div v-if="!student.conversations.length" class="student-list-empty"><MessageCircle :size="22" /><span>还没有教师会话</span></div>
      </aside>
      <section class="student-message-chat">
        <template v-if="selectedKind === 'SYSTEM'">
          <header class="student-message-chat-head"><span class="student-system-icon"><Bell :size="16" /></span><div><strong>系统通知</strong><small>知序云平台 · 只读会话</small></div></header>
          <div class="student-message-history student-system-history"><div v-for="notice in systemNotices" :key="notice.id" class="student-system-message"><time>{{ new Date(notice.createdAt).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }}</time><article><strong>{{ notice.title }}</strong><p>{{ notice.content }}</p></article></div><div v-if="!systemNotices.length" class="student-table-empty"><Bell :size="24" /><strong>暂无系统通知</strong><span>平台消息会在此集中展示。</span></div></div>
          <div class="student-message-compose student-system-compose"><textarea disabled placeholder="系统通知不可回复"></textarea><button class="student-send-button" disabled aria-label="系统通知不可回复" title="系统通知不可回复"><Send :size="17" /></button><p>系统通知由平台统一发送，不接收回复。</p></div>
        </template>
        <template v-else-if="selected">
          <header class="student-message-chat-head"><span class="student-avatar conversation">{{ selected.avatarText }}</span><div><strong>{{ selected.contactName }}</strong><small>任课教师</small></div></header>
          <div class="student-message-history"><div v-for="message in selected.messages" :key="message.id" class="student-message-bubble" :class="message.sender === 'STUDENT' ? 'mine' : 'theirs'"><span>{{ message.content }}</span><small>{{ new Date(message.createdAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }}</small></div><div v-if="!selected.messages.length" class="student-table-empty"><MessageCircle :size="24" /><strong>开始和老师交流</strong><span>发送你的第一个问题。</span></div></div>
          <div class="student-message-compose"><textarea v-model="input" :disabled="busy" placeholder="输入消息" @keydown.enter.exact.prevent="send"></textarea><button class="student-send-button" :disabled="busy || !input.trim()" aria-label="发送消息" title="发送消息" @click="send"><Send :size="17" /></button><p v-if="feedback" class="student-form-error">{{ feedback }}</p></div>
        </template>
        <div v-else class="student-message-placeholder"><MessageCircle :size="28" /><strong>选择一个会话</strong><span>或发起新的教师会话。</span></div>
      </section>
    </div>

    <div v-if="newOpen" class="student-modal-layer" role="presentation" @click.self="newOpen = false">
      <section class="student-modal student-modal-small" role="dialog" aria-modal="true" aria-labelledby="new-conversation-title"><header class="student-modal-head"><div><span class="student-eyebrow">消息通知</span><h2 id="new-conversation-title">发起新会话</h2></div><button class="student-icon-button" aria-label="关闭弹窗" title="关闭弹窗" @click="newOpen = false"><X :size="18" /></button></header><div class="student-modal-body"><label class="student-field"><span>选择教师</span><select v-model="teacherId" class="student-input"><option :value="null">请选择任课教师</option><option v-for="teacher in student.teacherContacts" :key="`${teacher.id}-${teacher.courseName}`" :value="teacher.id">{{ teacher.name }} · {{ teacher.courseName }}</option></select></label><label class="student-field"><span>首条消息</span><textarea v-model="firstMessage" class="student-textarea compact" placeholder="请输入想咨询的问题"></textarea></label><p v-if="feedback" class="student-form-error">{{ feedback }}</p></div><footer class="student-modal-foot"><button class="student-button secondary" @click="newOpen = false">取消</button><button class="student-button primary" :disabled="busy || !teacherId || !firstMessage.trim()" @click="createConversation"><UserRound :size="16" />开始会话</button></footer></section>
    </div>
  </section>
</template>
