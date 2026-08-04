<script setup lang="ts">
import { ref } from "vue";
import { BookOpen, KeyRound } from "@lucide/vue";
import { useStudentStore } from "./student.store";

const student = useStudentStore();
const inviteCode = ref("");
const busy = ref(false);
const message = ref("");

async function join() {
  message.value = "";
  if (!inviteCode.value.trim()) { message.value = "请输入教师提供的邀请码"; return; }
  busy.value = true;
  try { await student.joinCourse(inviteCode.value); inviteCode.value = ""; message.value = "课程已加入"; }
  catch (error) { message.value = (error as Error).message; }
  finally { busy.value = false; }
}
</script>

<template>
  <section class="student-content student-course-page">
    <section class="student-panel student-join-course">
      <div><h2>我的课程</h2><p>使用任课教师提供的邀请码加入额外课程。</p></div>
      <form @submit.prevent="join"><label><KeyRound :size="17" /><input v-model="inviteCode" maxlength="20" placeholder="课程邀请码" /></label><button class="student-primary-button" :disabled="busy">{{ busy ? "正在加入" : "加入课程" }}</button></form>
      <span v-if="message" class="student-inline-message">{{ message }}</span>
    </section>
    <section class="student-course-list">
      <article v-for="course in student.courses" :key="course.id" class="student-panel student-course-card">
        <span class="student-course-icon" :style="{ background: course.color }"><BookOpen :size="20" /></span>
        <div><strong>{{ course.name }}</strong><p>{{ course.code }} · {{ course.semester }}</p><span>{{ course.teacherName }} · {{ course.scheduleText }}</span></div>
      </article>
      <div v-if="!student.courses.length" class="student-empty">尚未加入课程。</div>
    </section>
  </section>
</template>
