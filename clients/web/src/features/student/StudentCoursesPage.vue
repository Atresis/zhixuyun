<script setup lang="ts">
import { computed, ref } from "vue";
import { ArrowRight, BookOpen, KeyRound, Plus, Search, X } from "@lucide/vue";
import { RouterLink } from "vue-router";
import { useStudentStore } from "./student.store";

const student = useStudentStore();
const joinOpen = ref(false);
const inviteCode = ref("");
const busy = ref(false);
const feedback = ref("");
const semester = ref("");
const keyword = ref("");
const semesters = computed(() => [...new Set(student.courses.map((course) => course.semester).filter(Boolean))]);
const filteredCourses = computed(() => student.courses.filter((course) => {
  const matchesSemester = !semester.value || course.semester === semester.value;
  const needle = keyword.value.trim().toLowerCase();
  const matchesKeyword = !needle || [course.name, course.teacherName, course.className, course.code].some((value) => value.toLowerCase().includes(needle));
  return matchesSemester && matchesKeyword;
}));
const pendingSoon = computed(() => {
  const limit = Date.now() + 48 * 60 * 60 * 1000;
  return student.tasks.filter((task) => task.submissionStatus === "待提交" && +new Date(task.deadline) <= limit && +new Date(task.deadline) >= Date.now()).length;
});
function tasksFor(courseId: number) { return student.tasks.filter((task) => task.courseId === courseId); }
function pendingFor(courseId: number) { return tasksFor(courseId).filter((task) => task.submissionStatus === "待提交").length; }

async function join() {
  feedback.value = "";
  if (!inviteCode.value.trim()) { feedback.value = "请输入课程邀请码"; return; }
  busy.value = true;
  try { await student.joinCourse(inviteCode.value.trim()); inviteCode.value = ""; joinOpen.value = false; }
  catch (error) { feedback.value = (error as Error).message; }
  finally { busy.value = false; }
}
</script>

<template>
  <section class="student-page">
    <div class="student-page-heading">
      <div><h1>我的课程</h1><p>本学期共有 {{ student.courses.length }} 门课程，{{ pendingSoon }} 项任务将在 48 小时内截止。</p></div>
      <div class="student-course-filters">
        <select v-model="semester" aria-label="筛选学期"><option value="">全部学期</option><option v-for="item in semesters" :key="item" :value="item">{{ item }}</option></select>
        <label><Search :size="16" /><input v-model="keyword" placeholder="搜索课程" /></label>
      </div>
    </div>

    <div v-if="student.courses.length" class="student-course-grid">
      <RouterLink v-for="course in filteredCourses" :key="course.id" :to="`/student/courses/${course.id}`" class="student-course-card">
        <div class="student-course-card-top"><span class="student-course-color" :style="{ background: course.color || '#07866f' }"><BookOpen :size="19" /></span><span class="student-course-state" :class="{ pending: pendingFor(course.id) }">{{ pendingFor(course.id) ? `${pendingFor(course.id)} 项待办` : '学习中' }}</span></div>
        <h2>{{ course.name }}</h2>
        <p>{{ course.teacherName }} · {{ course.className }}</p>
        <div class="student-course-card-foot"><span>{{ tasksFor(course.id).length ? `${tasksFor(course.id).length} 个进行中任务` : '暂无待办' }}</span><span class="student-course-enter">进入课程<ArrowRight :size="15" /></span></div>
      </RouterLink>
      <div v-if="!filteredCourses.length" class="student-filter-empty">没有符合筛选条件的课程</div>
    </div>

    <div v-else class="student-empty-course">
      <div class="student-empty-icon"><BookOpen :size="26" /></div>
      <h2>尚未选择课程</h2>
      <p>加入课程后，可以查看作业、实验和课程资料。</p>
      <button class="student-button primary" @click="joinOpen = true"><Plus :size="16" />使用邀请码加入课程</button>
    </div>

    <div v-if="joinOpen" class="student-modal-layer" role="presentation" @click.self="joinOpen = false">
      <section class="student-modal student-modal-small" role="dialog" aria-modal="true" aria-labelledby="join-title">
        <header class="student-modal-head"><div><span class="student-eyebrow">课程管理</span><h2 id="join-title">加入新课程</h2></div><button class="student-icon-button" aria-label="关闭弹窗" title="关闭弹窗" @click="joinOpen = false"><X :size="18" /></button></header>
        <div class="student-modal-body"><label class="student-field"><span>课程邀请码</span><div class="student-input-with-icon"><KeyRound :size="16" /><input v-model="inviteCode" maxlength="20" autocomplete="off" placeholder="请输入教师提供的邀请码" @keyup.enter="join" /></div></label><p v-if="feedback" class="student-form-error">{{ feedback }}</p></div>
        <footer class="student-modal-foot"><button class="student-button secondary" @click="joinOpen = false">取消</button><button class="student-button primary" :disabled="busy" @click="join">{{ busy ? "加入中..." : "确认加入" }}</button></footer>
      </section>
    </div>
  </section>
</template>
