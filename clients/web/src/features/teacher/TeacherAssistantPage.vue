<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { ArrowUp, ShieldCheck, SquarePen } from "@lucide/vue";
import { teacherApi } from "./teacher.api";
import { useTeacherStore } from "./teacher.store";
import type { AssistantSession } from "./teacher.types";

const store = useTeacherStore(); const selectedId = ref<number | null>(null); const prompt = ref(""); const busy = ref(false); const error = ref(""); const stage = ref<HTMLElement | null>(null);
const sessions = computed(() => store.workspace?.assistantSessions || []);
const current = computed(() => sessions.value.find((item) => item.id === selectedId.value) || null);
const messages = computed(() => current.value?.messages || []);
function fmt(value: string) { const date = new Date(value); return date.toDateString() === new Date().toDateString() ? date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" }) : date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" }); }
async function newChat() { const created = await teacherApi.newAssistantSession(); await store.reload(); selectedId.value = created.id; prompt.value = ""; }
async function send(value = prompt.value) { const text = value.trim(); if (!text || busy.value) return; if (!selectedId.value) await newChat(); if (!selectedId.value) return; busy.value = true; error.value = ""; prompt.value = ""; try { await teacherApi.sendAssistantMessage(selectedId.value, text); await store.reload(); await nextTick(); if (stage.value) stage.value.scrollTop = stage.value.scrollHeight; } catch (e) { error.value = (e as Error).message; prompt.value = text; } finally { busy.value = false; } }
function keydown(event: KeyboardEvent) { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); send(); } }
</script>

<template>
  <section class="assistant-layout"><aside class="chat-rail"><button class="new-chat" @click="newChat"><SquarePen :size="17" />新增对话</button><p class="history-label">最近对话</p><div class="history-list"><button v-for="item in sessions" :key="item.id" class="history-item" :class="{ active: item.id === selectedId }" @click="selectedId = item.id"><strong>{{ item.title }}</strong><span>{{ fmt(item.updatedAt) }}</span></button><div v-if="!sessions.length" class="empty-state">暂无历史对话</div></div></aside>
    <div class="chat-main"><header class="chat-header"><strong>{{ current?.title || '新对话' }}</strong><span><ShieldCheck :size="16" />仅使用授权教学数据</span></header>
      <div ref="stage" class="chat-stage" :class="{ 'has-messages': messages.length }">
        <div v-if="!messages.length" class="chat-empty"><h1>今天想解决什么教学问题？</h1><p>我可以基于你的课程、任务、提交统计与历史对话提供帮助。</p><div class="suggestions"><button v-for="(item, index) in store.workspace?.recommendations" :key="item" class="suggestion" @click="send(item)"><strong>{{ item }}</strong><span>{{ ['汇总错误类型、可能原因与课堂建议','按当前课程难度生成题目与参考答案','结合近期提交、成绩与活跃度给出名单','总结授课进度、任务完成情况与下周安排'][index] }}</span></button></div></div>
        <div v-else class="chat-thread"><article v-for="message in messages" :key="message.id" class="chat-message" :class="{ user: message.role === 'USER' }"><div class="bubble">{{ message.content }}</div></article><article v-if="busy" class="chat-message"><div class="bubble">正在整理授权教学数据...</div></article></div>
      </div>
      <div class="composer"><textarea v-model="prompt" rows="1" placeholder="输入教学问题，Enter 发送，Shift+Enter 换行" @keydown="keydown" /><button class="send-btn" aria-label="发送" :disabled="busy" @click="send()"><ArrowUp :size="18" /></button><span v-if="error" class="toast-inline">{{ error }}</span></div>
    </div>
  </section>
</template>
