<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";
import { BookOpen, CalendarDays, ClipboardCheck, Gauge, Plus, Users } from "@lucide/vue";
import { useTeacherStore } from "./teacher.store";

const store = useTeacherStore();
const workspace = computed(() => store.workspace!);
const tasks = computed(() => workspace.value.courses.flatMap((course) => course.tasks.map((task) => ({ ...task, course })))
  .sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)).slice(0, 5));
const alerts = computed(() => workspace.value.alerts.slice(0, 5));
const attentionCount = computed(() => tasks.value.filter((task) => new Date(task.deadline) > new Date()).length);
const greeting = computed(() => new Date().getHours() < 12 ? "上午好" : new Date().getHours() < 18 ? "下午好" : "晚上好");
function ago(value: string) { const minutes = Math.max(1, Math.floor((Date.now() - +new Date(value)) / 60000)); return minutes < 60 ? `${minutes}分钟前` : minutes < 1440 ? `${Math.floor(minutes / 60)}小时前` : `${Math.floor(minutes / 1440)}天前`; }
</script>

<template>
  <section class="page">
    <div class="page-head"><div><h1>{{ greeting }}，{{ workspace.profile.displayName }}</h1><p>今日有 {{ attentionCount }} 项任务需要关注，{{ alerts.length }} 条教学预警尚未处理。</p></div><div class="page-actions"><button class="btn"><CalendarDays :size="16" />本学期</button><RouterLink class="btn primary" to="/teacher/courses"><Plus :size="16" />发布教学任务</RouterLink></div></div>
    <div class="metric-grid">
      <article class="metric"><span>当前授课班级</span><span class="metric-icon"><Users :size="18" /></span><strong>{{ workspace.metrics.courseCount }}</strong><small>覆盖 <b>{{ workspace.courses.reduce((sum, item) => sum + item.studentCount, 0) }}</b> 名学生</small></article>
      <article class="metric"><span>进行中的任务</span><span class="metric-icon"><BookOpen :size="18" /></span><strong>{{ workspace.metrics.activeTaskCount }}</strong><small>本周新增 <b>{{ attentionCount }}</b> 项</small></article>
      <article class="metric"><span>待批改提交</span><span class="metric-icon"><ClipboardCheck :size="18" /></span><strong>{{ workspace.metrics.pendingReviewCount }}</strong><small>建议及时处理</small></article>
      <article class="metric"><span>本周平均提交率</span><span class="metric-icon"><Gauge :size="18" /></span><strong>{{ workspace.metrics.weeklySubmissionRate }}%</strong><small>基于全部授课班级</small></article>
    </div>
    <div class="dashboard-grid">
      <section class="panel"><header class="panel-head"><div><h2>最新任务提交与批改</h2><p>按发布时间排序，最多显示 5 项</p></div><RouterLink class="section-link" to="/teacher/courses">查看全部</RouterLink></header><div class="data-list">
        <article v-for="task in tasks" :key="task.id" class="data-row"><div><h3>{{ task.name }}</h3><p>{{ task.course.className }} · {{ new Date(task.deadline) > new Date() ? '进行中' : '已截止' }}</p></div><div class="progress-meta"><strong>提交 {{ task.submittedCount }}/{{ task.studentCount }} · 已批 {{ task.gradedCount }}</strong><div class="progress-track"><span :style="{ width: `${task.studentCount ? task.gradedCount / task.studentCount * 100 : 0}%` }" /></div></div></article>
        <div v-if="!tasks.length" class="empty-state">暂无任务</div>
      </div></section>
      <section class="panel"><header class="panel-head"><div><h2>最新 AI 教学预警</h2><p>基于课程表现与班级统计生成</p></div><RouterLink class="section-link" to="/teacher/alerts">进入预警中心</RouterLink></header><div class="data-list">
        <RouterLink v-for="alert in alerts" :key="alert.id" class="alert-row" to="/teacher/alerts"><span class="alert-dot" :class="{ high: alert.level === 'HIGH' }" /><div><h3>{{ alert.title }}</h3><p>{{ alert.targetName }} · {{ alert.status === 'UNREAD' ? '未读' : alert.status === 'PROPOSED' ? '已提案' : '已读' }}</p></div><time class="time">{{ ago(alert.createdAt) }}</time></RouterLink>
        <div v-if="!alerts.length" class="empty-state">暂无教学预警</div>
      </div></section>
    </div>
  </section>
</template>
