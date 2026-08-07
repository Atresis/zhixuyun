<script setup lang="ts">
import { computed, ref } from "vue";
import { ArrowLeft, BookOpen, Check, Clock3, Download, FileText, FlaskConical, ListChecks, X } from "@lucide/vue";
import { RouterLink, useRoute } from "vue-router";
import { studentApi } from "./student.api";
import { useStudentStore } from "./student.store";
import type { StudentQuestion, StudentTask } from "./student.types";

const route = useRoute();
const student = useStudentStore();
const tab = ref<"tasks" | "experiments" | "resources">("tasks");
const selectedTask = ref<StudentTask | null>(null);
const answers = ref<Record<string, string | string[]>>({});
const reportText = ref("");
const reportFile = ref<File>();
const submitBusy = ref(false);
const submitError = ref("");
const downloadBusy = ref<number | null>(null);

const course = computed(() => student.courses.find((item) => item.id === Number(route.params.courseId)) || null);
const courseTasks = computed(() => student.tasks.filter((task) => task.courseId === Number(route.params.courseId)));
const homeworks = computed(() => courseTasks.value.filter((task) => task.taskType === "HOMEWORK"));
const experiments = computed(() => courseTasks.value.filter((task) => task.taskType === "EXPERIMENT"));
const resources = computed(() => course.value?.resources || []);
const canSubmit = computed(() => selectedTask.value?.submissionStatus === "待提交" || selectedTask.value?.submissionStatus === "已退回");

function date(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function statusClass(status: StudentTask["submissionStatus"]) {
  return status === "教师已复核" ? "success" : status === "AI 初评完成" ? "info" : status === "已退回" ? "danger" : "warning";
}

function openTask(task: StudentTask) {
  selectedTask.value = task;
  submitError.value = "";
  reportFile.value = undefined;
  reportText.value = task.reportText || "";
  answers.value = { ...(task.answers || {}) };
}

function valueFor(question: StudentQuestion) {
  return answers.value[String(question.id)] || (question.type === "MULTIPLE" ? [] : "");
}

function toggleOption(question: StudentQuestion, option: string) {
  const key = String(question.id);
  const current = Array.isArray(answers.value[key]) ? [...answers.value[key] as string[]] : [];
  answers.value[key] = current.includes(option) ? current.filter((item) => item !== option) : [...current, option];
}

async function submit() {
  if (!selectedTask.value || submitBusy.value) return;
  submitBusy.value = true;
  submitError.value = "";
  try {
    if (selectedTask.value.taskType === "HOMEWORK") await student.submitAnswers(selectedTask.value.id, answers.value);
    else if (reportFile.value) await student.submitFile(selectedTask.value.id, reportFile.value);
    else await student.submitText(selectedTask.value.id, reportText.value);
    selectedTask.value = null;
  } catch (error) { submitError.value = (error as Error).message; }
  finally { submitBusy.value = false; }
}

async function download(id: number) {
  downloadBusy.value = id;
  try {
    const file = await studentApi.downloadResource(id);
    const url = URL.createObjectURL(file.blob);
    const anchor = document.createElement("a"); anchor.href = url; anchor.download = file.filename; anchor.click();
    URL.revokeObjectURL(url);
  } catch (error) { submitError.value = (error as Error).message; }
  finally { downloadBusy.value = null; }
}
</script>

<template>
  <section v-if="course" class="student-page student-course-detail-page">
    <div class="student-course-detail-head">
      <RouterLink class="student-back-link" to="/student/courses"><ArrowLeft :size="16" />返回课程首页</RouterLink>
      <div class="student-course-title-row"><span class="student-course-color large" :style="{ background: course.color || '#07866f' }"><FlaskConical :size="22" /></span><div><h1>{{ course.name }}</h1><p>{{ course.code }} · {{ course.teacherName }} · {{ course.scheduleText }}</p></div></div>
      <div class="student-course-stats"><span><b>{{ homeworks.length }}</b> 项作业</span><span><b>{{ experiments.length }}</b> 项实验</span><span><b>{{ resources.length }}</b> 份资料</span></div>
    </div>

    <div class="student-tabs" role="tablist">
      <button :class="{ active: tab === 'tasks' }" role="tab" @click="tab = 'tasks'">作业任务</button>
      <button :class="{ active: tab === 'experiments' }" role="tab" @click="tab = 'experiments'">实验任务</button>
      <button :class="{ active: tab === 'resources' }" role="tab" @click="tab = 'resources'">课程资料</button>
    </div>

    <section v-if="tab === 'tasks' || tab === 'experiments'" class="student-table-panel">
      <div class="student-panel-heading"><div><h2>{{ tab === 'tasks' ? '作业列表' : '实验列表' }}</h2><span>按截止时间查看当前课程任务</span></div><span class="student-count-label">{{ (tab === 'tasks' ? homeworks : experiments).length }} 项</span></div>
      <div v-if="(tab === 'tasks' ? homeworks : experiments).length" class="student-task-table">
        <div class="student-task-table-head"><span>任务名称</span><span>截止时间</span><span>分值</span><span>状态</span><span>操作</span></div>
        <button v-for="task in (tab === 'tasks' ? homeworks : experiments)" :key="task.id" class="student-task-table-row" @click="openTask(task)"><span><strong>{{ task.name }}</strong><small>{{ task.description || (tab === 'tasks' ? '完成题目并提交答案' : '提交实验报告或报告文件') }}</small></span><span><Clock3 :size="14" />{{ date(task.deadline) }}</span><span>{{ task.maxScore }} 分</span><span><em class="student-status" :class="statusClass(task.submissionStatus)"><i></i>{{ task.submissionStatus }}</em></span><span class="student-row-action">{{ task.submissionStatus === '待提交' || task.submissionStatus === '已退回' ? '去完成' : '查看详情' }}</span></button>
      </div>
      <div v-else class="student-table-empty"><ListChecks :size="24" /><strong>暂无{{ tab === 'tasks' ? '作业' : '实验' }}任务</strong><span>教师发布任务后会显示在这里。</span></div>
    </section>

    <section v-else class="student-table-panel">
      <div class="student-panel-heading"><div><h2>课程资料</h2><span>教师发布的共享资料和题库</span></div><span class="student-count-label">{{ resources.length }} 份</span></div>
      <div v-if="resources.length" class="student-resource-list"><article v-for="resource in resources" :key="resource.id" class="student-resource-row"><span class="student-resource-icon"><FileText :size="18" /></span><div><strong>{{ resource.name }}</strong><small>{{ resource.sourceLabel || '课程资料' }}<span v-if="resource.fileSize"> · {{ Math.ceil(resource.fileSize / 1024) }} KB</span></small></div><button class="student-icon-text-button" :disabled="downloadBusy === resource.id" @click="download(resource.id)"><Download :size="15" />{{ downloadBusy === resource.id ? '下载中' : '下载' }}</button></article></div>
      <div v-else class="student-table-empty"><FileText :size="24" /><strong>暂无课程资料</strong><span>教师上传资料后会显示在这里。</span></div>
    </section>

    <div v-if="selectedTask" class="student-modal-layer" role="presentation" @click.self="selectedTask = null">
      <section class="student-modal student-task-modal" role="dialog" aria-modal="true" :aria-labelledby="`task-title-${selectedTask.id}`">
        <header class="student-modal-head"><div><span class="student-eyebrow">{{ selectedTask.taskType === 'HOMEWORK' ? '作业答题' : '实验报告' }}</span><h2 :id="`task-title-${selectedTask.id}`">{{ selectedTask.name }}</h2><p>{{ date(selectedTask.startAt) }} 至 {{ date(selectedTask.deadline) }} · {{ selectedTask.maxScore }} 分</p></div><button class="student-icon-button" aria-label="关闭弹窗" title="关闭弹窗" @click="selectedTask = null"><X :size="18" /></button></header>
        <div class="student-modal-body student-task-modal-body">
          <template v-if="selectedTask.taskType === 'HOMEWORK'">
            <div v-if="selectedTask.questions?.length" class="student-question-list"><fieldset v-for="(question, index) in selectedTask.questions" :key="question.id" class="student-question"><legend><b>{{ index + 1 }}</b>{{ question.title }}<span>{{ question.score || 0 }} 分</span></legend><div v-if="question.type === 'SINGLE' || question.type === 'TRUE_FALSE'" class="student-option-list"><label v-for="option in (question.options || ['正确', '错误'])" :key="option"><input v-model="answers[String(question.id)]" type="radio" :name="`question-${question.id}`" :value="option" />{{ option }}</label></div><div v-else-if="question.type === 'MULTIPLE'" class="student-option-list"><label v-for="option in (question.options || [])" :key="option"><input type="checkbox" :checked="Array.isArray(valueFor(question)) && (valueFor(question) as string[]).includes(option)" @change="toggleOption(question, option)" />{{ option }}</label></div><input v-else-if="question.type === 'FILL'" v-model="answers[String(question.id)]" class="student-input" placeholder="请输入答案" /><textarea v-else v-model="answers[String(question.id)]" class="student-textarea compact" placeholder="请输入你的答案"></textarea></fieldset></div>
            <div v-else class="student-table-empty"><ListChecks :size="24" /><strong>题目正在准备中</strong><span>教师尚未发布可答题目。</span></div>
          </template>
          <template v-else>
            <div class="student-task-instruction"><span class="student-info-icon">i</span><p>{{ selectedTask.description || '请根据实验要求完成报告，并提交文本或文件。' }}</p></div>
            <label class="student-field"><span>报告内容</span><textarea v-model="reportText" class="student-textarea" placeholder="请输入实验报告内容"></textarea></label>
            <label class="student-upload-field"><span>或上传报告文件</span><input type="file" accept=".pdf,.doc,.docx,.txt,.md" @change="reportFile = ($event.target as HTMLInputElement).files?.[0]" /><em>{{ reportFile?.name || selectedTask.attachment?.fileName || '支持 PDF、Word、TXT、Markdown，单文件不超过 20MB' }}</em></label>
            <div v-if="selectedTask.aiReview || selectedTask.teacherComment" class="student-review-summary"><div v-if="selectedTask.aiReview"><strong>AI 初评建议</strong><p>{{ selectedTask.aiReview }}</p></div><div v-if="selectedTask.teacherComment"><strong>教师评语</strong><p>{{ selectedTask.teacherComment }}</p></div></div>
          </template>
          <p v-if="submitError" class="student-form-error">{{ submitError }}</p>
        </div>
        <footer class="student-modal-foot"><button class="student-button secondary" @click="selectedTask = null">取消</button><button class="student-button primary" :disabled="submitBusy || !canSubmit" @click="submit"><Check :size="16" />{{ submitBusy ? '提交中...' : selectedTask.submissionStatus === '已退回' ? '重新提交' : canSubmit ? '提交' : '已提交' }}</button></footer>
      </section>
    </div>
  </section>
  <section v-else class="student-page student-course-empty-page">
    <div class="student-page-heading"><div><h1>课程详情</h1><p>请从课程首页选择一门课程，查看作业、实验和课程资料。</p></div></div>
    <div class="student-empty-course"><div class="student-empty-icon"><BookOpen :size="26" /></div><h2>尚未选择课程</h2><p>返回课程首页选择一门课程后继续。</p><RouterLink class="student-button primary" to="/student/courses">返回课程首页</RouterLink></div>
  </section>
</template>
