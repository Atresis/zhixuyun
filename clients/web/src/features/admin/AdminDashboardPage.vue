<script setup lang="ts">
import { computed, onMounted } from "vue";
import { AlertTriangle, BookOpen, ClipboardCheck, GraduationCap, KeyRound, MessageSquareText, UserRoundPlus, UsersRound } from "@lucide/vue";
import { useAdminStore } from "./admin.store";

const store = useAdminStore();
const cards = computed(() => [
  { label: "在用学生账号", value: store.studentCount, note: `本学期新增 ${store.metrics.newStudentCount ?? 0} 个`, icon: UsersRound },
  { label: "在用教师账号", value: store.teacherCount, note: `覆盖 ${store.metrics.collegeCount ?? 0} 个二级学院`, icon: GraduationCap },
  { label: "本学期开设课程", value: store.metrics.courseCount ?? 0, note: `已完成任课安排 ${store.metrics.assignedCourseCount ?? 0} 门`, icon: BookOpen },
  { label: "需要处理的数据异常", value: store.metrics.issueCount ?? 0, note: `较昨日减少 ${store.metrics.resolvedIssueCount ?? 0} 项`, icon: AlertTriangle },
]);
const bars = [42, 55, 68, 61, 79, 96];
onMounted(() => store.loadDashboard());
</script>

<template>
  <section class="admin-page admin-dashboard-page">
    <div class="admin-page-head">
      <div><h1>平台概览</h1><p>2025至2026学院第2学期，今日有 {{ store.metrics.issueCount ?? 0 }} 项平台维护任务需要处理。</p></div>
      <div class="admin-head-actions">
        <select class="admin-filter admin-term-filter" aria-label="学期"><option>2025至2026学院第2学期</option></select>
        <RouterLink class="admin-primary-button" to="/admin/students"><UserRoundPlus :size="17" />新增账号</RouterLink>
      </div>
    </div>

    <div class="admin-overview-metrics">
      <article v-for="card in cards" :key="card.label" class="admin-overview-metric">
        <div><span>{{ card.label }}</span><strong>{{ Number(card.value || 0).toLocaleString("zh-CN") }}</strong><small>{{ card.note }}</small></div>
        <span class="admin-overview-icon"><component :is="card.icon" :size="19" /></span>
      </article>
    </div>

    <div class="admin-dashboard-grid">
      <section class="admin-panel admin-todo-panel">
        <header class="admin-panel-head"><div><h2>管理员待办</h2><p>账号、课程关系与系统配置中的可执行事项</p></div><RouterLink to="/admin/settings">查看系统设置</RouterLink></header>
        <div class="admin-todo-list">
          <article><span class="danger"><UsersRound :size="18" /></span><div><strong>学生账号缺少有效班级关系</strong><small>导入批次 STU-20260803-02，建议补全或退回导入</small></div><RouterLink class="admin-secondary-button" to="/admin/students">处理异常</RouterLink></article>
          <article><span class="warning"><ClipboardCheck :size="18" /></span><div><strong>{{ store.metrics.unassignedCourseCount ?? 14 }} 门课程尚未安排任课教师</strong><small>涉及软件学院、创意设计学院等</small></div><RouterLink class="admin-secondary-button" to="/admin/courses">安排教师</RouterLink></article>
          <article><span class="warning"><KeyRound :size="18" /></span><div><strong>AI 服务密钥将在 12 天后到期</strong><small>当前服务可用，建议提前完成密钥轮换</small></div><RouterLink class="admin-secondary-button" to="/admin/settings">更新配置</RouterLink></article>
          <article><span><MessageSquareText :size="18" /></span><div><strong>迎新系统维护通知等待发布</strong><small>草稿更新于今天 08:42，计划覆盖全部用户</small></div><RouterLink class="admin-secondary-button" to="/admin/settings">继续编辑</RouterLink></article>
        </div>
      </section>

      <section class="admin-panel admin-chart-panel">
        <header class="admin-panel-head"><div><h2>近六个月账号变化</h2><p>新增与停用账号数量对比</p></div><span class="admin-status">数据已更新</span></header>
        <div class="admin-bars" aria-label="近六个月账号变化柱状图">
          <div v-for="(height, index) in bars" :key="height"><span class="new" :style="{ height: `${height}px` }" /><span class="stopped" :style="{ height: `${Math.max(22, height * .48)}px` }" /><small>{{ index + 3 }}月</small></div>
        </div>
        <div class="admin-chart-legend"><span><i />新增账号</span><span><i />停用账号</span></div>
        <div class="admin-quick-grid">
          <RouterLink to="/admin/students"><UsersRound :size="17" />管理学生</RouterLink><RouterLink to="/admin/teachers"><GraduationCap :size="17" />管理教师</RouterLink><RouterLink to="/admin/classes"><ClipboardCheck :size="17" />维护班级</RouterLink><RouterLink to="/admin/courses"><BookOpen :size="17" />维护课程</RouterLink>
        </div>
      </section>
    </div>
  </section>
</template>
