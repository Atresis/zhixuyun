<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Bot, ChevronDown, Plus, Send, Sparkles } from "@lucide/vue";
import { useStudentStore } from "./student.store";

const student = useStudentStore();
const input = ref("");
const busy = ref(false);
const feedback = ref("");
const activeId = ref<number | null>(null);
const activeSession = computed(() => student.assistantSessions.find((session) => session.id === activeId.value) || null);

watch(() => student.assistantSessions, (sessions) => {
  if (!activeId.value && sessions.length) activeId.value = sessions[0].id;
}, { immediate: true });

async function newSession() {
  feedback.value = "";
  try { const session = await student.createAssistantSession(); activeId.value = session.id; }
  catch (error) { feedback.value = (error as Error).message; }
}

async function send(prompt = input.value) {
  const content = prompt.trim();
  if (!content || busy.value) return;
  busy.value = true; feedback.value = ""; input.value = "";
  try {
    if (!activeId.value) { const session = await student.createAssistantSession(); activeId.value = session.id; }
    await student.sendAssistantMessage(activeId.value as number, content);
  } catch (error) { feedback.value = (error as Error).message; }
  finally { busy.value = false; }
}
</script>

<template>
  <section class="student-page student-assistant-page">
    <div class="student-page-heading"><div><h1>AI 问答</h1><p>基于你的课程、任务与提交状态提供学习建议。</p></div><button class="student-button primary" @click="newSession"><Plus :size="16" />新建对话</button></div>
    <div class="student-assistant-layout">
      <aside class="student-session-list">
        <div class="student-section-label">历史对话 <span>{{ student.assistantSessions.length }}</span></div>
        <button v-for="session in student.assistantSessions" :key="session.id" class="student-session-item" :class="{ active: session.id === activeId }" @click="activeId = session.id"><span class="student-session-icon"><Bot :size="15" /></span><span><strong>{{ session.title }}</strong><small>{{ session.messages.length ? session.messages[session.messages.length - 1].content.slice(0, 24) : '开始新的学习提问' }}</small></span></button>
        <div v-if="!student.assistantSessions.length" class="student-list-empty">还没有历史对话</div>
      </aside>
      <section class="student-assistant-chat">
        <header class="student-assistant-chat-head"><div class="student-assistant-title"><span class="student-ai-mark"><Sparkles :size="17" /></span><div><strong>{{ activeSession?.title || '新的学习对话' }}</strong><small>知序云 AI 学习助手</small></div></div><button class="student-icon-button" aria-label="对话选项" title="对话选项"><ChevronDown :size="16" /></button></header>
        <div class="student-assistant-messages">
          <div v-if="!activeSession?.messages.length" class="student-assistant-welcome"><span class="student-ai-mark large"><Sparkles :size="22" /></span><h2>你好，我是你的学习助手</h2><p>可以帮你梳理待办、解释课程知识或根据 AI 初评给出修改建议。</p><div class="student-prompt-grid"><button v-for="prompt in student.workspace?.assistantPrompts || []" :key="prompt" @click="send(prompt)">{{ prompt }}</button></div></div>
          <div v-for="message in activeSession?.messages || []" :key="message.id" class="student-chat-message" :class="message.role === 'USER' ? 'user' : 'assistant'"><span v-if="message.role === 'ASSISTANT'" class="student-ai-mark tiny"><Sparkles :size="13" /></span><p>{{ message.content }}</p></div>
          <div v-if="busy" class="student-chat-message assistant"><span class="student-ai-mark tiny"><Sparkles :size="13" /></span><p class="student-thinking"><i></i><i></i><i></i></p></div>
        </div>
        <div class="student-assistant-compose"><textarea v-model="input" :disabled="busy" placeholder="输入你的问题" @keydown.enter.exact.prevent="send()"></textarea><button class="student-send-button" :disabled="busy || !input.trim()" aria-label="发送问题" title="发送问题" @click="send()"><Send :size="17" /></button><p v-if="feedback" class="student-form-error">{{ feedback }}</p></div>
      </section>
    </div>
  </section>
</template>
