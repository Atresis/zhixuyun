<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { Download, FileSpreadsheet, Info, Search, Upload, UserRoundPlus } from "@lucide/vue";
import { useRoute } from "vue-router";
import AdminModal from "./AdminModal.vue";
import { useAdminStore } from "./admin.store";
import type { AdminUser, TeacherDetail } from "./admin.api";

type DialogName = "students" | "teacher-create" | "teacher-detail" | "password" | "transfer" | null;
const store = useAdminStore();
const route = useRoute();
const mode = computed<"STUDENT" | "TEACHER">(() => route.name === "admin-teachers" ? "TEACHER" : "STUDENT");
const dialog = ref<DialogName>(null);
const selected = ref<AdminUser | null>(null);
const teacherDetail = ref<TeacherDetail | null>(null);
const keyword = ref("");
const grade = ref("");
const classId = ref("");
const status = ref("");
const currentPage = ref(1);
const pageSize = 10;
const message = ref("");
const importText = ref("");
const importFile = ref<File | null>(null);
const transferClassId = ref<number | null>(null);
const password = ref("");
const saving = ref(false);
const createForm = reactive({ loginName: "", displayName: "", email: "", phone: "", department: "", title: "", password: "123456" });

const filteredRows = computed(() => store.users.filter((item) => {
  if (item.role !== mode.value) return false;
  const text = `${item.displayName} ${item.loginName} ${item.email || ""} ${item.studentNo || ""}`.toLowerCase();
  if (keyword.value && !text.includes(keyword.value.trim().toLowerCase())) return false;
  if (grade.value && item.gradeYear !== grade.value) return false;
  if (classId.value && String(item.administrativeClassId ?? "") !== classId.value) return false;
  if (status.value && String(item.enabled) !== status.value) return false;
  return true;
}));
const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / pageSize)));
const pageRows = computed(() => filteredRows.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize));
const grades = computed(() => [...new Set(store.classes.map((item) => item.gradeYear))].filter(Boolean).sort());

onMounted(load);
watch(() => route.name, load);
watch([keyword, grade, classId, status], () => { currentPage.value = 1; });

async function load() {
  currentPage.value = 1;
  dialog.value = null;
  await Promise.all([store.loadUsers({ role: mode.value }), store.loadClasses(), store.loadCourses()]);
}

function openCreate() {
  message.value = "";
  if (mode.value === "STUDENT") {
    importText.value = "";
    importFile.value = null;
    dialog.value = "students";
  } else {
    Object.assign(createForm, { loginName: "", displayName: "", email: "", phone: "", department: "", title: "", password: "123456" });
    dialog.value = "teacher-create";
  }
}

async function openDetail(item: AdminUser) {
  selected.value = item;
  message.value = "";
  teacherDetail.value = await store.loadTeacherDetail(item.id);
  dialog.value = "teacher-detail";
}

function openPassword(item: AdminUser) {
  selected.value = item;
  password.value = "123456";
  message.value = "";
  dialog.value = "password";
}

function openTransfer(item: AdminUser) {
  selected.value = item;
  transferClassId.value = item.administrativeClassId ?? null;
  message.value = "";
  dialog.value = "transfer";
}

async function toggle(item: AdminUser) {
  await store.toggleUser(item);
}

async function archive(item: AdminUser) {
  if (!confirm(`确认删除学生账号“${item.displayName}”吗？该账号会先归档 72 小时。`)) return;
  await store.archiveStudent(item.id);
}

async function saveTeacher() {
  saving.value = true;
  message.value = "";
  try {
    await store.createUser({ ...createForm, role: "TEACHER" });
    dialog.value = null;
  } catch (error) { message.value = (error as Error).message; }
  finally { saving.value = false; }
}

async function savePassword() {
  if (!selected.value) return;
  saving.value = true;
  try { await store.resetPassword(selected.value.id, password.value); dialog.value = null; }
  catch (error) { message.value = (error as Error).message; }
  finally { saving.value = false; }
}

async function saveTransfer() {
  if (!selected.value || !transferClassId.value) return;
  saving.value = true;
  try { await store.transferStudent(selected.value.id, transferClassId.value); dialog.value = null; }
  catch (error) { message.value = (error as Error).message; }
  finally { saving.value = false; }
}

async function importStudents() {
  saving.value = true;
  message.value = "";
  try {
    if (importFile.value) {
      const result = await store.importStudents(importFile.value);
      message.value = `已导入 ${result.createdCount} 名学生，跳过 ${result.skippedCount} 条。`;
      return;
    }
    const lines = importText.value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    if (!lines.length) throw new Error("请输入至少一条学生记录或选择 Excel 文件");
    for (const line of lines) {
      const [studentNo, displayName, , , gradeYear, classNo] = line.split(/[，,\t]/).map((item) => item.trim());
      if (!studentNo || !displayName) throw new Error(`记录格式不正确：${line}`);
      const matchedClass = store.classes.find((item) => item.name.includes(`${gradeYear || ""}`) && (!classNo || item.name.includes(`${classNo}班`)));
      await store.createUser({ loginName: studentNo, studentNo, displayName, role: "STUDENT", gradeYear, administrativeClassId: matchedClass?.id ?? null, password: "123456" });
    }
    dialog.value = null;
  } catch (error) { message.value = (error as Error).message; }
  finally { saving.value = false; }
}

function pickFile(event: Event) { importFile.value = (event.target as HTMLInputElement).files?.[0] ?? null; }

function downloadCsv(filename: string, rows: string[][]) {
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob(["\ufeff", csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a"); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url);
}
function exportRows() {
  downloadCsv(mode.value === "STUDENT" ? "学生账号.csv" : "教师名册.csv", [["姓名", "账号", "邮箱", "班级", "状态"], ...filteredRows.value.map((item) => [item.displayName, item.loginName, item.email || "", item.administrativeClassName || "", item.enabled ? "正常" : "已停用"])]);
}
function downloadTemplate() { downloadCsv("学生导入模板.csv", [["学号", "姓名", "学院", "专业", "年级", "班级序号"], ["202511020089", "王以安", "软件学院", "软件工程", "2025", "2"]]); }
</script>

<template>
  <section class="admin-page">
    <div class="admin-page-head">
      <div><h1>{{ mode === "TEACHER" ? "教师管理" : "学生管理" }}</h1><p>{{ mode === "TEACHER" ? "维护教师账号与任课信息，详情中的身份资料仅供查看。" : "维护学生账号、班级归属和登录状态，重置密码将恢复为平台初始密码。" }}</p></div>
      <div class="admin-head-actions"><button class="admin-secondary-button" type="button" @click="exportRows"><Download :size="16" />{{ mode === "TEACHER" ? "导出教师名册" : "导出当前结果" }}</button><button class="admin-primary-button" type="button" @click="openCreate"><UserRoundPlus :size="17" />{{ mode === "TEACHER" ? "新增教师账号" : "新增学生账号" }}</button></div>
    </div>

    <div class="admin-toolbar admin-panel">
      <select v-if="mode === 'STUDENT'" v-model="grade" class="admin-filter"><option value="">全部年级</option><option v-for="item in grades" :key="item" :value="item">{{ item }}级</option></select>
      <select class="admin-filter"><option>全部学院</option></select>
      <select v-if="mode === 'STUDENT'" class="admin-filter"><option>全部专业</option></select>
      <select v-if="mode === 'STUDENT'" v-model="classId" class="admin-filter"><option value="">全部班级</option><option v-for="item in store.classes" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select>
      <select v-model="status" class="admin-filter"><option value="">全部状态</option><option value="true">正常</option><option value="false">已停用</option></select>
      <select v-if="mode === 'TEACHER'" class="admin-filter"><option>2025至2026学院第2学期</option></select>
      <label class="admin-search"><Search :size="16" /><input v-model="keyword" :placeholder="mode === 'TEACHER' ? '姓名、邮箱或账号' : '姓名或学号'" /></label>
      <span class="admin-toolbar-count">共 {{ filteredRows.length.toLocaleString("zh-CN") }} 名{{ mode === "TEACHER" ? "教师" : "学生" }}</span>
    </div>

    <section class="admin-panel admin-table-wrap">
      <div class="admin-table-scroll">
        <table class="admin-data-table admin-users-table">
          <thead><tr><th>{{ mode === "TEACHER" ? "教师" : "学生" }}</th><th>{{ mode === "TEACHER" ? "邮箱" : "班级" }}</th><th>{{ mode === "TEACHER" ? "所属学院" : "学号" }}</th><th>账号状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in pageRows" :key="item.id">
              <td><span class="admin-cell-person"><i class="admin-mini-avatar" :class="{ teacher: mode === 'TEACHER' }">{{ item.displayName.slice(0, 1) }}</i><span class="admin-cell-main"><strong>{{ item.displayName }}</strong></span></span></td>
              <td>{{ mode === "TEACHER" ? (item.email || "未填写") : (item.administrativeClassName || "未分班") }}</td>
              <td>{{ mode === "TEACHER" ? "软件学院" : (item.studentNo || item.loginName) }}</td>
              <td><span class="admin-status" :class="{ disabled: !item.enabled }">{{ item.enabled ? "正常" : "已停用" }}</span></td>
              <td><div class="admin-row-actions">
                <button v-if="mode === 'STUDENT'" class="admin-row-action danger" @click="archive(item)">删除账号</button>
                <button v-else class="admin-row-action danger" disabled title="当前服务端未提供教师账号归档接口">删除账号</button>
                <button class="admin-row-action warning" @click="toggle(item)">{{ item.enabled ? "停用账号" : "启用账号" }}</button>
                <button v-if="mode === 'STUDENT'" class="admin-row-action" @click="openTransfer(item)">转班</button>
                <button v-else class="admin-row-action" @click="openDetail(item)">详情信息</button>
                <button class="admin-row-action" @click="openPassword(item)">重置密码</button>
              </div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!pageRows.length" class="admin-empty">没有符合筛选条件的账号</div>
      <footer class="admin-table-footer"><span>第 {{ filteredRows.length ? (currentPage - 1) * pageSize + 1 : 0 }}-{{ Math.min(currentPage * pageSize, filteredRows.length) }} 条，共 {{ filteredRows.length }} 条</span><nav class="admin-pagination"><button :disabled="currentPage === 1" @click="currentPage--">‹</button><button v-for="page in Math.min(totalPages, 3)" :key="page" :class="{ active: currentPage === page }" @click="currentPage = page">{{ page }}</button><button :disabled="currentPage === totalPages" @click="currentPage++">›</button></nav></footer>
    </section>

    <AdminModal v-if="dialog === 'students'" title="新增学生账号" subtitle="支持逐行录入或使用模板批量导入" wide @close="dialog = null">
      <div class="admin-info-strip"><Info :size="16" />系统会校验学号、班级和重复账号。通过校验后，初始密码将按平台统一规则生成。</div>
      <label class="admin-field admin-import-input"><span>按格式录入学生</span><textarea v-model="importText" placeholder="202511020089,王以安,软件学院,软件工程,2025,2" /></label>
      <small class="admin-help-text">字段之间使用英文逗号分隔，支持一次粘贴多行。</small>
      <div class="admin-format-example"><strong>录入格式示例</strong><code>202511020089,王以安,软件学院,软件工程,2025,2</code></div>
      <label class="admin-upload-zone"><FileSpreadsheet :size="20" /><strong>使用 Excel 模板批量添加</strong><span>支持 XLSX 或 CSV 文件，单次最多导入 2,000 个账号</span><div><button class="admin-secondary-button" type="button" @click.prevent="downloadTemplate"><Download :size="16" />下载模板</button><span class="admin-primary-button"><Upload :size="16" />选择文件</span></div><input type="file" accept=".xlsx,.csv" @change="pickFile" /></label>
      <div class="admin-validation-key"><span>● 等待校验</span><span>● 校验通过</span><span>● 重复学号与格式错误会定位到具体行</span></div>
      <p v-if="message" class="admin-form-error">{{ message }}</p>
      <template #footer><span class="admin-dialog-counter">当前已录入 {{ importText.split(/\r?\n/).filter(Boolean).length }} 条</span><button class="admin-secondary-button" type="button" @click="dialog = null">取消</button><button class="admin-primary-button" type="button" :disabled="saving" @click="importStudents">校验并添加账号</button></template>
    </AdminModal>

    <AdminModal v-if="dialog === 'teacher-create'" title="新增教师账号" subtitle="创建教师账号并补充基础任教信息" @close="dialog = null">
      <form id="teacher-create-form" class="admin-form-grid" @submit.prevent="saveTeacher">
        <label class="admin-field"><span>教师工号</span><input v-model="createForm.loginName" required /></label><label class="admin-field"><span>教师姓名</span><input v-model="createForm.displayName" required /></label>
        <label class="admin-field"><span>邮箱</span><input v-model="createForm.email" type="email" /></label><label class="admin-field"><span>联系电话</span><input v-model="createForm.phone" /></label>
        <label class="admin-field"><span>所属学院</span><input v-model="createForm.department" /></label><label class="admin-field"><span>职称</span><input v-model="createForm.title" /></label>
        <label class="admin-field admin-field--wide"><span>初始密码</span><input v-model="createForm.password" minlength="6" required /></label>
      </form><p v-if="message" class="admin-form-error">{{ message }}</p>
      <template #footer><button class="admin-secondary-button" @click="dialog = null">取消</button><button class="admin-primary-button" form="teacher-create-form" :disabled="saving">创建教师账号</button></template>
    </AdminModal>

    <AdminModal v-if="dialog === 'teacher-detail' && selected" :title="`${selected.displayName} · 教师详情`" subtitle="身份信息不可在此页面修改" wide @close="dialog = null">
      <div class="admin-teacher-facts"><div><span>教师账号</span><strong>{{ selected.loginName }}</strong></div><div><span>联系电话</span><strong>{{ selected.phone || "未填写" }}</strong></div><div><span>所属学院</span><strong>{{ teacherDetail?.department || "软件学院" }}</strong></div><div><span>账号创建时间</span><strong>2020-06-18 10:24</strong></div></div>
      <div class="admin-detail-title"><h3>任课课程</h3><select class="admin-filter"><option>2025至2026学院第2学期</option></select></div>
      <div class="admin-mini-table"><div class="head"><span>课程</span><span>授课班级</span><span>教学状态</span></div><div v-for="item in teacherDetail?.courseAssignments || []" :key="item.courseId"><span class="admin-cell-main"><strong>{{ item.courseName }}</strong><small>{{ item.courseCode }}</small></span><span>{{ teacherDetail?.teachingClassAssignments.find((row) => row.courseId === item.courseId)?.teachingClassName || "未安排" }}</span><span class="admin-status">进行中</span></div><p v-if="!teacherDetail?.courseAssignments.length" class="admin-empty">本学期暂无任课课程</p></div>
      <template #footer><span class="admin-dialog-counter">本学期共 {{ teacherDetail?.courseAssignments.length || 0 }} 门课程</span><button class="admin-secondary-button" @click="dialog = null">关闭详情</button><button class="admin-secondary-button" @click="openPassword(selected)">重置为初始密码</button></template>
    </AdminModal>

    <AdminModal v-if="dialog === 'password' && selected" title="重置账号密码" :subtitle="`将为 ${selected.displayName} 设置新的登录密码`" @close="dialog = null"><label class="admin-field"><span>新密码</span><input v-model="password" type="password" minlength="6" /></label><p v-if="message" class="admin-form-error">{{ message }}</p><template #footer><button class="admin-secondary-button" @click="dialog = null">取消</button><button class="admin-primary-button" :disabled="saving" @click="savePassword">确认重置密码</button></template></AdminModal>
    <AdminModal v-if="dialog === 'transfer' && selected" title="学生转班" :subtitle="`调整 ${selected.displayName} 的行政班归属`" @close="dialog = null"><label class="admin-field"><span>目标班级</span><select v-model.number="transferClassId"><option :value="null" disabled>请选择班级</option><option v-for="item in store.classes" :key="item.id" :value="item.id">{{ item.name }}</option></select></label><p v-if="message" class="admin-form-error">{{ message }}</p><template #footer><button class="admin-secondary-button" @click="dialog = null">取消</button><button class="admin-primary-button" :disabled="saving || !transferClassId" @click="saveTransfer">确认转班</button></template></AdminModal>
  </section>
</template>
