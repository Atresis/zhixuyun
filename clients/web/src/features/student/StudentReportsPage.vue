<script setup lang="ts">
import { ref } from "vue";
import { studentApi } from "./student.api";
import { useStudentStore } from "./student.store";
import type { SubmissionVersion } from "./student.types";

const student = useStudentStore();
const versions = ref<Record<number, SubmissionVersion[]>>({});
async function toggleVersions(submissionId: number) {
  if (versions.value[submissionId]) { delete versions.value[submissionId]; return; }
  versions.value[submissionId] = await studentApi.versions(submissionId);
}
</script>

<template>
  <section class="student-content student-report-grid">
    <section class="student-panel">
      <div class="student-panel-head"><h2>我的报告</h2><span class="student-chip">{{ student.reports.length }} 条记录</span></div>
      <div v-if="student.reports.length" class="student-report-list">
        <article v-for="report in student.reports" :key="report.submissionId" class="student-report-card">
          <div class="student-report-head">
            <div><strong>{{ report.taskName }}</strong><span>{{ student.courses.find((course) => course.id === report.courseId)?.name }}</span></div>
            <span class="student-state" :class="report.submissionStatus === '教师已复核' ? 'done' : report.submissionStatus === 'AI 初评完成' ? 'ai' : 'pending'">{{ report.submissionStatus }}</span>
          </div>
          <p>{{ report.reportText || (report.attachment?.fileName ? `文件提交：${report.attachment.fileName}` : "已提交报告") }}</p>
          <div class="student-task-meta">
            <span class="student-chip">提交时间：{{ report.submittedAt ? report.submittedAt.slice(5, 16).replace("T", " ") : "未记录" }}</span>
            <span class="student-chip">AI：{{ report.aiScore ?? "--" }}</span>
            <span class="student-chip">教师：{{ report.teacherScore ?? "--" }}</span>
          </div>
          <p v-if="report.aiReview"><strong>AI 初评：</strong>{{ report.aiReview }}</p>
          <p v-if="report.teacherComment"><strong>教师评语：</strong>{{ report.teacherComment }}</p>
          <button class="student-secondary-button" @click="toggleVersions(report.submissionId)">{{ versions[report.submissionId] ? "收起版本" : `查看版本历史（${report.currentVersionNo || 1}）` }}</button>
          <div v-if="versions[report.submissionId]" class="student-version-list">
            <article v-for="version in versions[report.submissionId]" :key="version.id"><strong>V{{ version.versionNo }}</strong><span>{{ version.createdAt.slice(0, 16).replace("T", " ") }} · AI {{ version.aiScore ?? "--" }}</span><p>{{ version.reportText || version.attachment?.fileName || "文件报告" }}</p></article>
          </div>
        </article>
      </div>
      <div v-else class="student-empty">暂时还没有报告提交记录。</div>
    </section>
  </section>
</template>
