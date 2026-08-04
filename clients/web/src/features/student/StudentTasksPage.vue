<script setup lang="ts">
import { computed, ref } from "vue";
import { useStudentStore } from "./student.store";
import type { StudentTask } from "./student.types";

const student = useStudentStore();
const keyword = ref("");
const selectedTaskId = ref<number | null>(null);
const textDrafts = ref<Record<number, string>>({});
const files = ref<Record<number, File | undefined>>({});
const saving = ref(false);
const submitError = ref("");

const tasks = computed(() => student.tasks.filter((task) => `${task.name} ${student.taskCourse(task)?.name || ""}`.toLowerCase().includes(keyword.value.trim().toLowerCase())));
const selected = computed(() => student.tasks.find((task) => task.id === selectedTaskId.value) || null);

function openTask(task: StudentTask) {
  selectedTaskId.value = task.id;
  textDrafts.value[task.id] = task.reportText || "";
}

async function submitSelected() {
  if (!selected.value || saving.value) return;
  saving.value = true;
  submitError.value = "";
  try {
    if (files.value[selected.value.id]) await student.submitFile(selected.value.id, files.value[selected.value.id] as File);
    else await student.submitText(selected.value.id, textDrafts.value[selected.value.id] || "");
  } catch (cause) {
    submitError.value = (cause as Error).message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="student-content">
    <section class="student-panel">
      <div class="student-panel-head">
        <h2>实验任务列表</h2>
        <input v-model="keyword" placeholder="搜索任务或课程" />
      </div>
      <div v-if="tasks.length" class="student-task-list">
        <div v-for="task in tasks" :key="task.id" class="student-task-row">
          <div><strong>{{ task.name }}</strong><p>{{ student.taskCourse(task)?.name }}</p></div>
          <span>{{ task.taskType === "EXPERIMENT" ? "实验任务" : "作业任务" }}</span>
          <span>{{ task.deadline.slice(5, 16).replace("T", " ") }}</span>
          <span class="student-state" :class="task.submissionStatus === '教师已复核' ? 'done' : task.submissionStatus === 'AI 初评完成' ? 'ai' : 'pending'">{{ task.submissionStatus }}</span>
          <button class="student-task-action primary" @click="openTask(task)">查看详情</button>
        </div>
      </div>
      <div v-else class="student-empty">没有匹配到任务。</div>
    </section>

    <section v-if="selected" class="student-task-detail">
      <div class="student-task-head">
        <div><small>{{ student.taskCourse(selected)?.name }}</small><h2>{{ selected.name }}</h2></div>
        <span class="student-chip">{{ selected.maxScore }} 分</span>
      </div>
      <p>{{ selected.description || "教师暂未填写补充说明。" }}</p>
      <div class="student-task-meta">
        <span class="student-chip">开始：{{ selected.startAt.slice(5, 16).replace("T", " ") }}</span>
        <span class="student-chip">截止：{{ selected.deadline.slice(5, 16).replace("T", " ") }}</span>
        <span class="student-chip">{{ selected.submissionStatus }}</span>
      </div>
      <div v-if="selected.aiReview" class="student-panel">
        <strong>AI 初评建议</strong>
        <p>{{ selected.aiReview }}</p>
      </div>
      <div v-if="selected.teacherComment" class="student-panel">
        <strong>教师评语</strong>
        <p>{{ selected.teacherComment }}</p>
      </div>
      <div class="student-task-form">
        <textarea v-model="textDrafts[selected.id]" placeholder="如果当前任务需要文本作答，可以直接在这里填写或粘贴实验报告内容。"></textarea>
        <div class="student-file-input">
          <input type="file" accept=".pdf,.doc,.docx,.txt" @change="files[selected.id] = ($event.target as HTMLInputElement).files?.[0]" />
          <span>{{ files[selected.id]?.name || selected.attachment?.fileName || "也可以选择文件作为本次提交版本" }}</span>
        </div>
        <div v-if="submitError" class="student-submit-error">{{ submitError }}</div>
        <button class="student-btn primary" @click="submitSelected">{{ saving ? "提交中..." : "提交当前版本" }}</button>
      </div>
    </section>
  </section>
</template>
