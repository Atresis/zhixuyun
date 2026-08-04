<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { BellRing, ChevronLeft, ChevronRight, MessageSquarePlus, Paperclip, Search, Send, ShieldCheck, Smile, Users, X } from "@lucide/vue";
import { teacherApi } from "./teacher.api";
import type { ContactCandidate, Conversation } from "./teacher.types";
import { useTeacherStore } from "./teacher.store";

const store = useTeacherStore();
const search = ref("");
const selectedId = ref<number | null>(store.workspace?.conversations[0]?.id || null);
const message = ref("");
const busy = ref(false);
const error = ref("");
const showNewConversation = ref(false);
const candidateCourseId = ref<number | null>(null);
const candidateQuery = ref("");
const candidatePage = ref(1);
const candidates = ref<ContactCandidate[]>([]);
const candidateTotal = ref(0);
const candidatePages = ref(0);
const candidateLoading = ref(false);
const candidateError = ref("");
const selectedStudent = ref<ContactCandidate | null>(null);
const pendingStudent = ref<ContactCandidate | null>(null);

const conversations = computed(() => (store.workspace?.conversations || []).filter((item) =>
  !search.value || `${item.contactName} ${lastMessage(item.messages)?.content || ""}`.includes(search.value)));
const current = computed<Conversation | null>(() => store.workspace?.conversations.find((item) => item.id === selectedId.value)
  || conversations.value[0] || null);
const currentName = computed(() => current.value?.contactName || pendingStudent.value?.name || "新会话");
const isSystem = computed(() => current.value?.contactType === "SYSTEM");

function fmt(value: string) {
  const date = new Date(value);
  return date.toDateString() === new Date().toDateString()
    ? date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}
function lastMessage<T>(items: T[]) { return items.length ? items[items.length - 1] as T & { content?: string } : undefined; }
function openNewConversation() {
  showNewConversation.value = true; candidateCourseId.value = store.workspace?.courses[0]?.id || null;
  candidateQuery.value = ""; candidatePage.value = 1; selectedStudent.value = null; candidateError.value = ""; loadCandidates();
}
function closeNewConversation() { showNewConversation.value = false; }
async function loadCandidates() {
  if (!candidateCourseId.value) { candidates.value = []; return; }
  candidateLoading.value = true; candidateError.value = "";
  try {
    const page = await teacherApi.contactCandidates(candidateCourseId.value, candidateQuery.value, candidatePage.value, 10);
    candidates.value = page.items; candidateTotal.value = page.total; candidatePages.value = page.pages;
    if (selectedStudent.value && !candidates.value.some((item) => item.id === selectedStudent.value?.id)) selectedStudent.value = null;
  } catch (e) { candidateError.value = (e as Error).message; } finally { candidateLoading.value = false; }
}
function chooseCourse() { candidatePage.value = 1; selectedStudent.value = null; loadCandidates(); }
function searchCandidates() { candidatePage.value = 1; loadCandidates(); }
function enterConversation() {
  if (!selectedStudent.value) return;
  pendingStudent.value = selectedStudent.value; selectedId.value = null; message.value = ""; error.value = ""; closeNewConversation();
}
async function select(id: number) {
  pendingStudent.value = null; selectedId.value = id;
  const item = store.workspace?.conversations.find((row) => row.id === id);
  if (item?.unreadCount) { await teacherApi.readConversation(id); await store.reload(); }
}
async function send() {
  const content = message.value.trim();
  if (!content || busy.value || isSystem.value) return;
  busy.value = true; error.value = "";
  try {
    const result = pendingStudent.value
      ? await teacherApi.createConversation(pendingStudent.value.id, content)
      : current.value ? await teacherApi.sendConversationMessage(current.value.id, content) : null;
    if (result) { pendingStudent.value = null; selectedId.value = result.id; message.value = ""; await store.reload(); }
  } catch (e) { error.value = (e as Error).message; } finally { busy.value = false; }
}
watch(conversations, (rows) => { if (!selectedId.value && !pendingStudent.value && rows[0]) selectedId.value = rows[0].id; }, { immediate: true });
</script>

<template>
  <section class="messages-layout">
    <aside class="contacts">
      <div class="contacts-head">
        <div class="contact-search-row">
          <div class="search-box"><Search :size="17" /><input v-model="search" placeholder="搜索联系人或消息" /></div>
          <button class="new-conversation-icon" title="发起新会话" aria-label="发起新会话" @click="openNewConversation"><MessageSquarePlus :size="18" /></button>
        </div>
      </div>
      <div class="contact-list">
        <button v-for="item in conversations" :key="item.id" class="contact" :class="{ active: current?.id === item.id }" @click="select(item.id)">
          <span class="contact-avatar" :class="{ system: item.contactType === 'SYSTEM' }"><BellRing v-if="item.contactType === 'SYSTEM'" :size="18" /><template v-else>{{ item.avatarText || item.contactName.slice(0, 1) }}</template></span>
          <div class="contact-main"><div class="contact-top"><strong>{{ item.contactName }}</strong><time>{{ fmt(item.updatedAt) }}</time></div><p>{{ lastMessage(item.messages)?.content || "暂无消息" }}</p></div><span v-if="item.unreadCount" class="badge">{{ item.unreadCount }}</span>
        </button>
        <div v-if="!conversations.length" class="empty-state">没有匹配的联系人</div>
      </div>
    </aside>
    <div class="conversation">
      <header class="conversation-head"><strong>{{ currentName }}</strong><span v-if="isSystem"><ShieldCheck :size="16" /> 官方消息</span><span v-else-if="pendingStudent"><Users :size="16" /> {{ pendingStudent.studentNo }} · {{ pendingStudent.className }}</span></header>
      <div class="conversation-body">
        <template v-if="isSystem"><article v-for="item in current?.messages" :key="item.id" class="system-card"><h3>{{ item.title }}</h3><p>{{ item.content }}</p><footer>知序云系统通知 · {{ new Date(item.createdAt).toLocaleString('zh-CN') }}</footer></article></template>
        <template v-else-if="current"><div v-for="item in current.messages" :key="item.id" class="message-line" :class="{ teacher: item.sender === 'TEACHER' }"><div class="message-bubble">{{ item.content }}<small>{{ fmt(item.createdAt) }}</small></div></div></template>
        <div v-else-if="pendingStudent" class="blank-conversation"><Users :size="28" /><strong>开始与 {{ pendingStudent.name }} 对话</strong><span>发送第一条消息后，双方联系人列表中才会创建该会话。</span></div>
        <div v-else class="empty-state">请选择联系人，或发起一个新会话</div>
      </div>
      <div class="conversation-compose"><div class="compose-tools"><button title="发送文件"><Paperclip :size="17" /></button><button title="插入表情"><Smile :size="17" /></button></div><textarea v-model="message" :disabled="isSystem || (!current && !pendingStudent)" :placeholder="isSystem ? '系统通知为只读会话' : '输入消息'" @keydown.enter.exact.prevent="send" /><button v-if="!isSystem" class="send-message-btn" :disabled="busy || (!current && !pendingStudent)" title="发送" @click="send"><Send :size="17" /></button><span v-if="error" class="toast-inline">{{ error }}</span></div>
    </div>
  </section>

  <div v-if="showNewConversation" class="modal-root open" @keydown.esc="closeNewConversation">
    <div class="backdrop" @click="closeNewConversation" />
    <section class="modal small new-conversation-modal" role="dialog" aria-modal="true">
      <header class="modal-titlebar"><div><h2>发起新会话</h2><p>只能联系当前任课班级中的学生</p></div><button class="close-btn" aria-label="关闭" @click="closeNewConversation"><X :size="18" /></button></header>
      <div class="new-conversation-body">
        <label class="field-label">选择任课班级<select v-model="candidateCourseId" class="select" @change="chooseCourse"><option v-for="course in store.workspace?.courses" :key="course.id" :value="course.id">{{ course.className }} · {{ course.name }}</option></select></label>
        <div class="search-box candidate-search"><Search :size="16" /><input v-model="candidateQuery" placeholder="搜索姓名或学号" @keydown.enter="searchCandidates" /><button title="搜索" @click="searchCandidates"><Search :size="15" /></button></div>
        <div class="candidate-meta"><span>学生列表</span><small>{{ candidateTotal }} 名学生</small></div>
        <div v-if="candidateLoading" class="candidate-empty">正在加载学生...</div>
        <div v-else-if="candidateError" class="candidate-empty error-text">{{ candidateError }}</div>
        <div v-else-if="!candidates.length" class="candidate-empty">暂无符合条件的学生</div>
        <div v-else class="candidate-list"><button v-for="student in candidates" :key="student.id" class="candidate-row" :class="{ selected: selectedStudent?.id === student.id }" @click="selectedStudent = student"><span class="candidate-avatar">{{ student.name.slice(0, 1) }}</span><span><strong>{{ student.name }}</strong><small>{{ student.studentNo }}</small></span><i /></button></div>
        <div class="candidate-pagination"><button :disabled="candidatePage <= 1 || candidateLoading" title="上一页" @click="candidatePage--; loadCandidates()"><ChevronLeft :size="16" /></button><span>{{ candidatePages ? candidatePage + ' / ' + candidatePages : '0 / 0' }}</span><button :disabled="!candidatePages || candidatePage >= candidatePages || candidateLoading" title="下一页" @click="candidatePage++; loadCandidates()"><ChevronRight :size="16" /></button></div>
      </div>
      <footer class="modal-actions"><button class="ghost-btn" @click="closeNewConversation">取消</button><button class="primary-btn" :disabled="!selectedStudent" @click="enterConversation">进入会话</button></footer>
    </section>
  </div>
</template>
