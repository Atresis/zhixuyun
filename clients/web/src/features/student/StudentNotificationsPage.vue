<script setup lang="ts">
import { useStudentStore } from "./student.store";

const student = useStudentStore();
async function read(id: number) { await student.readNotification(id); }
</script>

<template>
  <section class="student-content student-notification-grid">
    <section class="student-panel">
      <div class="student-panel-head"><h2>消息通知</h2><button v-if="student.notifications.some((item) => item.read === false)" class="student-primary-button" @click="student.readAllNotifications()">全部已读</button><span class="student-chip">{{ student.notifications.length }} 条</span></div>
      <div v-if="student.notifications.length" class="student-notice-list">
        <article v-for="notice in student.notifications" :key="notice.id" class="student-notice" :class="{ unread: notice.read === false }">
          <div class="student-report-head">
            <div><strong>{{ notice.title }}</strong><span>{{ notice.createdAt.slice(5, 16).replace("T", " ") }}</span></div>
            <span class="student-state" :class="notice.status === 'DONE' ? 'done' : notice.status === 'INFO' ? 'ai' : 'pending'">{{ notice.type }}</span>
          </div>
          <p>{{ notice.content }}</p>
          <button v-if="notice.read === false" class="student-secondary-button" @click="read(notice.id)">标记已读</button>
        </article>
      </div>
      <div v-else class="student-empty">暂无通知。</div>
    </section>
  </section>
</template>
