<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronLeft, ChevronRight } from "@lucide/vue";
import { teacherApi } from "./teacher.api";
import { useTeacherStore } from "./teacher.store";
import type { TeachingAlert } from "./teacher.types";
import TeacherModal from "./TeacherModal.vue";

const store = useTeacherStore(); const filter = ref<"ALL" | TeachingAlert["status"]>("ALL"); const page = ref(1); const selected = ref<TeachingAlert | null>(null); const proposal = ref(""); const busy = ref(false); const message = ref("");
const filtered = computed(() => (store.workspace?.alerts || []).filter((item) => filter.value === "ALL" || item.status === filter.value));
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / 10)));
const rows = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10));
const counts = computed(() => ({ ALL: store.workspace?.alerts.length || 0, UNREAD: store.workspace?.alerts.filter((x) => x.status === "UNREAD").length || 0, READ: store.workspace?.alerts.filter((x) => x.status === "READ").length || 0, PROPOSED: store.workspace?.alerts.filter((x) => x.status === "PROPOSED").length || 0 }));
function setFilter(value: typeof filter.value) { filter.value = value; page.value = 1; }
function fmt(value: string) { return new Date(value).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }); }
async function open(item: TeachingAlert) { selected.value = item; proposal.value = item.proposal || ""; message.value = ""; if (item.status === "UNREAD") { selected.value = await teacherApi.readAlert(item.id); await store.reload(); } }
async function save() { if (!selected.value) return; busy.value = true; message.value = ""; try { selected.value = await teacherApi.saveProposal(selected.value.id, proposal.value); await store.reload(); message.value = "解决方案已保存"; } catch (e) { message.value = (e as Error).message; } finally { busy.value = false; } }
</script>

<template>
  <section class="page wide"><div class="page-head"><div><h1>AI 教学预警</h1><p>综合班级知识点掌握、学生表现、任务提交与活跃度生成。</p></div><div class="alert-filter segmented"><button :class="{ active: filter === 'ALL' }" @click="setFilter('ALL')">全部 {{ counts.ALL }}</button><button :class="{ active: filter === 'UNREAD' }" @click="setFilter('UNREAD')">未读 {{ counts.UNREAD }}</button><button :class="{ active: filter === 'READ' }" @click="setFilter('READ')">已读 {{ counts.READ }}</button><button :class="{ active: filter === 'PROPOSED' }" @click="setFilter('PROPOSED')">已提案 {{ counts.PROPOSED }}</button></div></div>
    <section class="panel warning-list"><div class="warning-head"><span>时间</span><span>预警内容</span><span>对象</span><span>级别</span><span>处理状态</span></div>
      <article v-for="item in rows" :key="item.id" class="warning-item" tabindex="0" @dblclick="open(item)" @keydown.enter="open(item)"><time>{{ fmt(item.createdAt) }}</time><div><h3>{{ item.title }}</h3><p>{{ item.summary }}</p></div><span>{{ item.targetName }}</span><span class="level" :class="{ high: item.level === 'HIGH' }">{{ item.level === 'HIGH' ? '高风险' : '需关注' }}</span><span class="state-pill" :class="{ read: item.status === 'READ', proposed: item.status === 'PROPOSED' }">{{ item.status === 'UNREAD' ? '未读' : item.status === 'READ' ? '已读' : '已提案' }}</span></article>
      <div v-if="!rows.length" class="empty-state">当前筛选下没有预警</div>
    </section><div class="pager"><button :disabled="page <= 1" @click="page--"><ChevronLeft :size="17" /></button><span>第 {{ page }} / {{ pageCount }} 页</span><button :disabled="page >= pageCount" @click="page++"><ChevronRight :size="17" /></button></div>
  </section>

  <TeacherModal v-if="selected" :title="selected.title" :subtitle="`${selected.targetName} · ${new Date(selected.createdAt).toLocaleString('zh-CN')}`" size="large" @close="selected = null"><div class="alert-detail"><section class="analysis-side"><h3>AI 分析</h3><p>{{ selected.analysis }}</p><div class="evidence"><strong>关键依据</strong><br /><span v-for="line in selected.evidence.split('\n')" :key="line">{{ line }}<br /></span></div></section><section class="proposal-side"><h3>教师解决方案</h3><textarea v-model="proposal" placeholder="记录你的判断和处理方案" /><p v-if="message" :class="message.includes('已保存') ? 'status-note' : 'toast-inline'">{{ message }}</p><div class="proposal-actions"><span class="time">保存后状态变为“已提案”</span><button class="btn primary" :disabled="busy" @click="save">{{ busy ? '保存中' : '保存解决方案' }}</button></div></section></div></TeacherModal>
</template>
