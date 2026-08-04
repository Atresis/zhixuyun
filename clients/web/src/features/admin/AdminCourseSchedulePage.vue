<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ArrowLeft, Info, Search, UsersRound } from "@lucide/vue";
import { useRoute, useRouter } from "vue-router";
import AdminModal from "./AdminModal.vue";
import { useAdminStore } from "./admin.store";
import type { AdminClass, AdminUser } from "./admin.api";

const route = useRoute(); const router = useRouter(); const store = useAdminStore();
const teachers = ref<AdminUser[]>([]); const targetClass = ref<AdminClass | null>(null); const dialog = ref(false); const batch = ref(false); const selectedTeacherId = ref<number | null>(null); const keyword = ref(""); const saving = ref(false); const message = ref("");
const courseId = computed(() => Number(route.params.courseId));
const course = computed(() => store.courses.find((item) => item.id === courseId.value) ?? null);
const classes = computed(() => course.value?.classes ?? []);
const teacherRows = computed(() => teachers.value.filter((item) => `${item.displayName} ${item.loginName} ${item.email || ""}`.toLowerCase().includes(keyword.value.trim().toLowerCase())));
const assignedCount = computed(() => classes.value.filter((item) => item.teacherId).length);
onMounted(async () => { await Promise.all([store.loadCourses(), store.loadUsers({ role: "TEACHER" })]); teachers.value = store.users.filter((item) => item.role === "TEACHER" && item.enabled); if (!course.value) router.replace("/admin/courses"); });
function openPicker(item?: AdminClass) { targetClass.value = item ?? null; batch.value = !item; selectedTeacherId.value = item?.teacherId ?? null; keyword.value = ""; message.value = ""; dialog.value = true; }
async function saveTeacher() { if (!selectedTeacherId.value) return; saving.value = true; try { const targets = batch.value ? classes.value.filter((item) => !item.teacherId) : targetClass.value ? [targetClass.value] : []; await Promise.all(targets.map((item) => store.assignClassTeacher(item.id, selectedTeacherId.value!))); dialog.value = false; } catch (error) { message.value = (error as Error).message; } finally { saving.value = false; } }
</script>

<template>
  <section v-if="course" class="admin-page admin-course-schedule-page">
    <div class="admin-page-head"><div><h1>开课安排</h1><p>按班级维护当前课程的任课教师，所有关系均与所选学期绑定。</p></div><div class="admin-head-actions"><button class="admin-secondary-button" @click="router.push('/admin/courses')"><ArrowLeft :size="16" />返回课程管理</button><button class="admin-primary-button" @click="openPicker()"><UsersRound :size="17" />批量安排教师</button></div></div>
    <section class="admin-course-hero"><div><h2>{{ course.name }}</h2><p>课程编码 {{ course.code }} · {{ course.className }} · {{ course.scheduleText || "课程安排" }}</p></div><dl><div><dd>{{ classes.length }}</dd><dt>开课班级</dt></div><div><dd>{{ assignedCount }}</dd><dt>已安排</dt></div><div><dd>{{ classes.length - assignedCount }}</dd><dt>待安排</dt></div></dl></section>
    <div class="admin-toolbar admin-panel"><select class="admin-filter"><option>{{ course.semester }}</option></select><select class="admin-filter"><option>全部学院</option></select><select class="admin-filter"><option>全部专业</option></select><select class="admin-filter"><option>全部安排状态</option></select><label class="admin-search"><Search :size="16" /><input placeholder="班级名称或教师姓名" /></label><span class="admin-toolbar-count">共 {{ classes.length }} 个班级</span></div>
    <section class="admin-panel admin-table-wrap"><div class="admin-table-scroll"><table class="admin-data-table admin-schedule-table"><thead><tr><th>授课班级</th><th>学生人数</th><th>任课教师</th><th>教师所属学院</th><th>安排状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in classes" :key="item.id"><td><span class="admin-cell-main"><strong>{{ item.name }}</strong><small>{{ item.term }}</small></span></td><td>{{ course.studentCount || 0 }} 人</td><td><strong>{{ item.teacherName || "尚未安排" }}</strong></td><td>{{ item.teacherName ? "软件学院" : "—" }}</td><td><span class="admin-status" :class="{ pending: !item.teacherId }">{{ item.teacherId ? "已安排" : "待安排" }}</span></td><td><div class="admin-row-actions"><button class="admin-row-action" @click="openPicker(item)">{{ item.teacherId ? "更换教师" : "安排教师" }}</button></div></td></tr></tbody></table></div><div v-if="!classes.length" class="admin-empty">该课程暂无教学班，请先在班级管理中配置课程。</div><footer class="admin-table-footer"><span>第 1-{{ classes.length }} 条，共 {{ classes.length }} 条</span><nav class="admin-pagination"><button>‹</button><button class="active">1</button><button>›</button></nav></footer></section>
    <AdminModal v-if="dialog" title="选择任课教师" :subtitle="batch ? `${course.name} · 批量安排待安排班级` : `${targetClass?.name} · ${course.name}`" wide @close="dialog = false">
      <div class="admin-info-strip"><Info :size="16" />可检索全部在用教师。同课程、同学院且当前学期有相关任课记录的教师优先显示。</div>
      <div class="admin-toolbar admin-picker-toolbar"><select class="admin-filter"><option>全部学院</option></select><select class="admin-filter"><option>全部教师状态</option></select><label class="admin-search"><Search :size="16" /><input v-model="keyword" placeholder="姓名、账号或邮箱" /></label><span class="admin-toolbar-count">共 {{ teacherRows.length }} 名</span></div>
      <div class="admin-mini-table admin-teacher-picker"><div class="head"><span>教师</span><span>邮箱</span><span>所属学院</span><span>本学期课程</span><span>选择</span></div><label v-for="teacher in teacherRows" :key="teacher.id" :class="{ selected: selectedTeacherId === teacher.id }"><span class="admin-cell-person"><i class="admin-mini-avatar teacher">{{ teacher.displayName.slice(0, 1) }}</i><span class="admin-cell-main"><strong>{{ teacher.displayName }}</strong></span></span><span>{{ teacher.email || "未填写" }}</span><span>软件学院</span><span>{{ store.courses.filter((item) => item.teacherId === teacher.id).length }} 门</span><input v-model.number="selectedTeacherId" type="radio" :value="teacher.id" /></label></div><p v-if="message" class="admin-form-error">{{ message }}</p>
      <template #footer><span class="admin-dialog-counter">已选择：<strong>{{ teachers.find((item) => item.id === selectedTeacherId)?.displayName || "未选择" }}</strong></span><button class="admin-secondary-button" @click="dialog = false">取消</button><button class="admin-primary-button" :disabled="saving || !selectedTeacherId" @click="saveTeacher">确认安排教师</button></template>
    </AdminModal>
  </section>
</template>
