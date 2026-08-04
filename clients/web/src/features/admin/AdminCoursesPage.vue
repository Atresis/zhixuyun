<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Plus, ScanSearch, Search } from "@lucide/vue";
import { useRouter } from "vue-router";
import AdminModal from "./AdminModal.vue";
import { useAdminStore } from "./admin.store";
import type { AdminCourse, AdminUser } from "./admin.api";

const store = useAdminStore();
const router = useRouter();
const teachers = ref<AdminUser[]>([]);
const keyword = ref("");
const status = ref("");
const modal = ref(false);
const saving = ref(false);
const message = ref("");
const form = reactive({ name: "", code: "", semester: "2025至2026学院第2学期", className: "软件学院", courseType: "专业必修", totalHours: 32, credits: 2, scheduleText: "", teacherId: null as number | null });
const rows = computed(() => store.courses.filter((item) => {
  const matches = `${item.name} ${item.code}`.toLowerCase().includes(keyword.value.trim().toLowerCase());
  const arranged = item.classes.some((entry) => entry.teacherId);
  return matches && (!status.value || status.value === String(arranged));
}));

onMounted(async () => {
  await Promise.all([store.loadCourses(), store.loadUsers({ role: "TEACHER" })]);
  teachers.value = store.users.filter((item) => item.role === "TEACHER");
});
function openCreate() { Object.assign(form, { name: "", code: "", semester: "2025至2026学院第2学期", className: "软件学院", courseType: "专业必修", totalHours: 32, credits: 2, scheduleText: "", teacherId: teachers.value[0]?.id ?? null }); message.value = ""; modal.value = true; }
async function createCourse() {
  if (!form.teacherId) { message.value = "请先创建至少一名教师账号，课程需要一个初始负责人。"; return; }
  if (store.courses.some((item) => item.code.toLowerCase() === form.code.trim().toLowerCase())) { message.value = "课程编码已存在"; return; }
  saving.value = true;
  try { await store.createCourse({ ...form, className: `${form.className} · ${form.courseType}`, color: "#07866f", studentCount: 0 }); modal.value = false; }
  catch (error) { message.value = (error as Error).message; }
  finally { saving.value = false; }
}
async function remove(course: AdminCourse) { if (confirm(`确认删除课程“${course.name}”吗？`)) await store.deleteCourse(course.id); }
function openSchedule(course: AdminCourse) { router.push(`/admin/courses/${course.id}/schedule`); }
</script>

<template>
  <section class="admin-page admin-course-page">
    <div class="admin-page-head"><div><h1>课程管理</h1><p>课程名称与课程编码用于基础查重，任课教师按学期单独安排。</p></div><div class="admin-head-actions"><button class="admin-secondary-button" type="button"><ScanSearch :size="16" />检查重复课程</button><button class="admin-primary-button" type="button" @click="openCreate"><Plus :size="17" />新增课程</button></div></div>
    <div class="admin-toolbar admin-panel"><select class="admin-filter"><option>2025至2026学院第2学期</option></select><select class="admin-filter"><option>全部开课学院</option></select><select class="admin-filter"><option>全部课程类型</option></select><select v-model="status" class="admin-filter"><option value="">全部安排状态</option><option value="true">已安排</option><option value="false">待安排</option></select><label class="admin-search"><Search :size="16" /><input v-model="keyword" placeholder="课程名称或编码" /></label><span class="admin-toolbar-count">本学期 {{ rows.length }} 门</span></div>
    <section class="admin-panel admin-table-wrap"><div class="admin-table-scroll"><table class="admin-data-table admin-course-table"><thead><tr><th>课程名称</th><th>课程编码</th><th>开课学院</th><th>课程类型</th><th>任课状态</th><th>操作</th></tr></thead><tbody><tr v-for="course in rows" :key="course.id"><td><span class="admin-cell-main"><strong>{{ course.name }}</strong><small>{{ course.scheduleText || "32 学时 · 2 学分" }}</small></span></td><td>{{ course.code }}</td><td>{{ course.className.split(' · ')[0] || "未设置" }}</td><td>{{ course.className.split(' · ')[1] || "专业必修" }}</td><td><span class="admin-status" :class="{ pending: !course.classes.some((item) => item.teacherId) }">{{ course.classes.some((item) => item.teacherId) ? `已安排 ${course.classes.filter((item) => item.teacherId).length} 人` : "待安排" }}</span></td><td><div class="admin-row-actions"><button class="admin-row-action" @click="openSchedule(course)">开课安排</button><button class="admin-row-action danger" @click="remove(course)">删除课程</button></div></td></tr></tbody></table></div><div v-if="!rows.length" class="admin-empty">暂无课程数据</div><footer class="admin-table-footer"><span>第 1-{{ rows.length }} 条，共 {{ rows.length }} 条</span><nav class="admin-pagination"><button>‹</button><button class="active">1</button><button>›</button></nav></footer></section>
    <AdminModal v-if="modal" title="新增课程" subtitle="建立课程基础信息，任课教师可稍后按学期安排" @close="modal = false">
      <div class="admin-info-strip"><ScanSearch :size="16" />系统将同时校验课程编码精确重复和课程名称相似度，避免同一课程重复建立。</div>
      <form id="course-create-form" class="admin-form-grid admin-form-grid--course" @submit.prevent="createCourse"><label class="admin-field"><span>课程名称</span><input v-model="form.name" required /></label><label class="admin-field"><span>课程编码</span><input v-model="form.code" required /></label><label class="admin-field"><span>开课学院</span><select v-model="form.className"><option>软件学院</option><option>电子与通信工程学院</option><option>创意设计学院</option><option>通识教育中心</option></select></label><label class="admin-field"><span>课程类型</span><select v-model="form.courseType"><option>专业必修</option><option>专业选修</option><option>实践课程</option><option>公共基础</option></select></label><label class="admin-field"><span>课程学时</span><input v-model.number="form.totalHours" type="number" min="1" /></label><label class="admin-field"><span>课程学分</span><input v-model.number="form.credits" type="number" min="0" step=".5" /></label><label class="admin-field admin-field--wide"><span>课程说明</span><textarea v-model="form.scheduleText" /></label></form><p v-if="message" class="admin-form-error">{{ message }}</p>
      <template #footer><span class="admin-status">编码可用</span><button class="admin-secondary-button" @click="modal = false">取消</button><button class="admin-primary-button" form="course-create-form" :disabled="saving">创建课程</button></template>
    </AdminModal>
  </section>
</template>
