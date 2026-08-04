<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { teacherApi } from "./teacher.api";
import { useTeacherStore } from "./teacher.store";
import type { TaskAnalytics } from "./teacher.types";

const store = useTeacherStore();
const tasks = computed(() => (store.workspace?.courses || []).flatMap((course) => course.tasks.map((task) => ({ ...task, courseName: course.name }))));
const taskId = ref<number | null>(null); const analytics = ref<TaskAnalytics | null>(null); const error = ref("");
watch(tasks, (items) => { if (!taskId.value && items.length) taskId.value = items[0].id; }, { immediate: true });
watch(taskId, async (id) => { if (!id) return; try { analytics.value = await teacherApi.analytics(id); error.value = ""; } catch (cause) { error.value = (cause as Error).message; } }, { immediate: true });
</script>

<template><section class="page"><div class="page-head"><div><h1>班级分析</h1><p>按任务查看提交进度、待复核数量和成绩分布。</p></div><select v-model="taskId" class="analysis-select"><option v-for="task in tasks" :key="task.id" :value="task.id">{{ task.courseName }} · {{ task.name }}</option></select></div><p v-if="error" class="toast-inline">{{ error }}</p><template v-if="analytics"><div class="metric-grid"><article><span>已提交</span><strong>{{ analytics.summary.submittedCount }}/{{ analytics.summary.totalCount }}</strong></article><article><span>待复核</span><strong>{{ analytics.summary.pendingReviewCount }}</strong></article><article><span>平均分</span><strong>{{ analytics.summary.averageScore == null ? '--' : Number(analytics.summary.averageScore).toFixed(1) }}</strong></article><article><span>成绩区间</span><strong>{{ analytics.summary.minimumScore ?? '--' }} - {{ analytics.summary.maximumScore ?? '--' }}</strong></article></div><section class="analytics-panel"><h2>成绩分布</h2><div v-for="item in analytics.distribution" :key="item.range" class="distribution-row"><span>{{ item.range }}</span><div><i :style="{ width: `${Math.min(100, item.count * 12)}%` }" /></div><strong>{{ item.count }}</strong></div><div v-if="!analytics.distribution.length" class="empty-state">暂无可统计成绩</div></section></template></section></template>
