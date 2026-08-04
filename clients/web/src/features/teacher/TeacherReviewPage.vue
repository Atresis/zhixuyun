<script setup lang="ts">
import { computed, ref } from "vue";
import { teacherApi } from "./teacher.api";
import { useTeacherStore } from "./teacher.store";
import type { Submission } from "./teacher.types";

const store = useTeacherStore();
const selected = ref<Submission | null>(null); const score = ref(0); const comment = ref(""); const message = ref(""); const busy = ref(false);
const rows = computed(() => (store.workspace?.courses || []).flatMap((course) => course.tasks.flatMap((task) => task.submissions.filter((item) => item.submitted).map((submission) => ({ submission, task, course })))).sort((a, b) => new Date(b.submission.submittedAt || 0).getTime() - new Date(a.submission.submittedAt || 0).getTime()));
function open(item: Submission) { selected.value = item; score.value = item.teacherScore ?? item.aiScore ?? 0; comment.value = item.teacherComment || ""; message.value = ""; }
async function grade() { if (!selected.value) return; busy.value = true; try { await teacherApi.grade(selected.value.id, { teacherScore: score.value, teacherComment: comment.value }); await store.reload(); selected.value = null; } catch (error) { message.value = (error as Error).message; } finally { busy.value = false; } }
async function returnForRevision() { if (!selected.value) return; busy.value = true; try { await teacherApi.returnSubmission(selected.value.id, comment.value || "请根据批注意见修改后重新提交。"); await store.reload(); selected.value = null; } catch (error) { message.value = (error as Error).message; } finally { busy.value = false; } }
</script>

<template><section class="page"><div class="page-head"><div><h1>报告批阅</h1><p>复核 AI 初评、退回修改或发布最终成绩。</p></div></div><div class="review-workspace"><section class="management-list"><button v-for="row in rows" :key="row.submission.id" class="management-row review-row" @click="open(row.submission)"><div class="resource-main"><strong>{{ row.submission.studentName }} · {{ row.task.name }}</strong><span>{{ row.course.name }} · {{ row.submission.studentNo }} · V{{ row.submission.currentVersionNo || 1 }}</span></div><span class="state-pill">{{ row.submission.reviewStatus === 'PUBLISHED' ? '已发布' : row.submission.reviewStatus === 'RETURNED' ? '已退回' : '待复核' }}</span></button><div v-if="!rows.length" class="empty-state">暂无提交</div></section><aside v-if="selected" class="review-detail"><h2>{{ selected.studentName }}</h2><p class="ai-review">{{ selected.aiReview || '暂无 AI 初评' }}</p><div class="report-preview">{{ selected.reportText || '文件报告，请在课程工作区查看附件。' }}</div><label class="field-label">最终评分<input v-model.number="score" type="number" min="0" max="100" /></label><label class="field-label">评语或退回原因<textarea v-model="comment" /></label><p v-if="message" class="toast-inline">{{ message }}</p><div class="task-actions"><button class="btn" :disabled="busy" @click="returnForRevision">退回修改</button><button class="btn primary" :disabled="busy" @click="grade">发布成绩</button></div></aside></div></section></template>
