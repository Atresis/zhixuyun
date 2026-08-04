<script setup lang="ts">
import { BookOpen, CheckCheck, ClipboardList, Sparkles } from "@lucide/vue";
import { useStudentStore } from "./student.store";

const student = useStudentStore();
</script>

<template>
  <section class="student-content">
    <div class="student-metrics">
      <article class="student-card"><span>待提交任务</span><b>{{ student.metrics.pendingTaskCount }}</b><small>优先处理临近截止的实验</small><i><ClipboardList :size="20" /></i></article>
      <article class="student-card"><span>已提交报告</span><b>{{ student.metrics.submittedCount }}</b><small>本账号已同步的提交记录</small><i><BookOpen :size="20" /></i></article>
      <article class="student-card"><span>AI 初评完成</span><b>{{ student.metrics.aiReadyCount }}</b><small>可根据建议继续修改</small><i><Sparkles :size="20" /></i></article>
      <article class="student-card"><span>教师已复核</span><b>{{ student.metrics.reviewedCount }}</b><small>最终评价已发布</small><i><CheckCheck :size="20" /></i></article>
    </div>

    <div class="student-grid-two">
      <section class="student-panel">
        <div class="student-panel-head"><h2>近期任务</h2><span class="student-chip">按截止时间排序</span></div>
        <div v-if="student.urgentTasks.length" class="student-task-list">
          <div v-for="task in student.urgentTasks" :key="task.id" class="student-task-row">
            <div><strong>{{ task.name }}</strong><p>{{ student.taskCourse(task)?.name }}</p></div>
            <span>{{ task.taskType === "EXPERIMENT" ? "实验" : "作业" }}</span>
            <span>{{ task.deadline.slice(5, 16).replace("T", " ") }}</span>
            <span class="student-state" :class="task.submissionStatus === '教师已复核' ? 'done' : task.submissionStatus === 'AI 初评完成' ? 'ai' : 'pending'">{{ task.submissionStatus }}</span>
            <RouterLink class="student-task-action primary" to="/student/tasks">去处理</RouterLink>
          </div>
        </div>
        <div v-else class="student-empty">当前没有可展示的任务。</div>
      </section>

      <section class="student-panel">
        <div class="student-panel-head"><h2>本周提醒</h2><span class="student-chip">自动整理</span></div>
        <div v-if="student.notifications.length" class="student-notice-list">
          <article v-for="notice in student.notifications.slice(0, 4)" :key="notice.id" class="student-notice">
            <strong>{{ notice.title }}</strong>
            <p>{{ notice.content }}</p>
          </article>
        </div>
        <div v-else class="student-empty">暂无新的提醒。</div>
      </section>
    </div>
  </section>
</template>
