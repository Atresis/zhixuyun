<script setup lang="ts">
import { computed } from "vue";
import { ClipboardList, FlaskConical } from "@lucide/vue";
import { useRouter } from "vue-router";
import { useTeacherStore } from "./teacher.store";

const store = useTeacherStore();
const router = useRouter();
const tasks = computed(() => (store.workspace?.courses || []).flatMap((course) => course.tasks.map((task) => ({ ...task, courseName: course.name, className: course.className }))));
</script>

<template>
  <section class="page"><div class="page-head"><div><h1>实验任务</h1><p>集中查看本人授课课程的作业与实验任务。</p></div><button class="btn primary" @click="router.push('/teacher/courses')">进入课程发布任务</button></div>
    <div class="management-list"><article v-for="task in tasks" :key="task.id" class="management-row"><i class="file-icon"><ClipboardList v-if="task.taskType === 'HOMEWORK'" :size="18" /><FlaskConical v-else :size="18" /></i><div class="resource-main"><strong>{{ task.name }}</strong><span>{{ task.courseName }} · {{ task.className }} · 截止 {{ new Date(task.deadline).toLocaleString('zh-CN') }}</span></div><div class="task-actions"><span class="state-pill">提交 {{ task.submittedCount }}/{{ task.studentCount }}</span><span class="state-pill">已批 {{ task.gradedCount }}</span></div></article><div v-if="!tasks.length" class="empty-state">暂无任务</div></div>
  </section>
</template>
