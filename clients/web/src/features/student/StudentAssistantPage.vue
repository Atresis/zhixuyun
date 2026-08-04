<script setup lang="ts">
import { ref } from "vue";
import { useStudentStore } from "./student.store";

const student = useStudentStore();
const input = ref("");

async function send() {
  const prompt = input.value.trim();
  if (!prompt) return;
  input.value = "";
  await student.askAssistant(prompt);
}
</script>

<template>
  <section class="student-content student-assistant-grid">
    <section class="student-chat">
      <div class="student-chat-head"><h2>AI 学习助手</h2><span class="student-chip">基于当前任务状态提供建议</span></div>
      <div class="student-message-list">
        <div v-for="(message, index) in student.messages" :key="index" class="student-message" :class="message.role">{{ message.content }}</div>
      </div>
      <div class="student-chat-compose">
        <textarea v-model="input" placeholder="例如：帮我总结最近最需要优先完成的实验任务"></textarea>
        <button class="student-btn primary" @click="send">{{ student.assistantBusy ? "思考中..." : "发送问题" }}</button>
      </div>
    </section>

    <aside class="student-chat-side">
      <div class="student-chat-head"><h3>快捷提问</h3></div>
      <div class="student-prompt-list">
        <div v-for="prompt in student.workspace?.assistantPrompts || []" :key="prompt" class="student-prompt">
          <button @click="student.askAssistant(prompt)">{{ prompt }}</button>
        </div>
      </div>
    </aside>
  </section>
</template>
