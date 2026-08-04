<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Plus } from "@lucide/vue";
import { teacherApi } from "./teacher.api";
import type { RubricDimension, RubricTemplate } from "./teacher.types";

const rubrics = ref<RubricTemplate[]>([]); const editing = ref<RubricTemplate | null>(null); const name = ref(""); const dimensions = ref("实验原理:30\n实现过程:35\n结果分析:35"); const message = ref("");
async function load() { rubrics.value = await teacherApi.rubrics(); }
onMounted(load);
function edit(item?: RubricTemplate) { editing.value = item || null; name.value = item?.name || ""; dimensions.value = item ? item.dimensions.map((part) => `${part.name}:${part.weight}`).join("\n") : "实验原理:30\n实现过程:35\n结果分析:35"; }
function parseDimensions(): RubricDimension[] { return dimensions.value.split("\n").map((line) => { const [dimensionName, rawWeight] = line.split(":"); return { name: dimensionName?.trim(), weight: Number(rawWeight) }; }).filter((item) => item.name && Number.isFinite(item.weight)); }
async function save() { const parts = parseDimensions(); if (!name.value.trim() || !parts.length || parts.reduce((sum, item) => sum + item.weight, 0) !== 100) { message.value = "请填写模板名称，且维度权重合计必须为 100"; return; } if (editing.value) await teacherApi.updateRubric(editing.value.id, name.value, parts); else await teacherApi.createRubric(name.value, parts); editing.value = null; name.value = ""; message.value = ""; await load(); }
async function toggle(item: RubricTemplate) { await teacherApi.setRubricEnabled(item.id, !item.enabled); await load(); }
</script>

<template><section class="page"><div class="page-head"><div><h1>评价模板</h1><p>维护实验报告评价维度；修改会生成新的模板版本。</p></div><button class="btn primary" @click="edit()"><Plus :size="16" />新建模板</button></div><div class="rubric-workspace"><section class="management-list"><article v-for="item in rubrics" :key="item.id" class="management-row"><div class="resource-main"><strong>{{ item.name }}</strong><span>V{{ item.version }} · {{ item.dimensions.map((part) => `${part.name} ${part.weight}%`).join(' / ') }}</span></div><div class="task-actions"><button class="btn" @click="edit(item)">编辑</button><button class="btn" @click="toggle(item)">{{ item.enabled ? '停用' : '启用' }}</button></div></article><div v-if="!rubrics.length" class="empty-state">暂无评价模板</div></section><aside v-if="editing !== null || name || dimensions" class="rubric-editor"><h2>{{ editing ? '编辑模板' : '新建模板' }}</h2><label class="field-label">名称<input v-model="name" /></label><label class="field-label">维度与权重（每行“名称:权重”）<textarea v-model="dimensions" rows="8" /></label><p v-if="message" class="toast-inline">{{ message }}</p><button class="btn primary" @click="save">保存模板</button></aside></div></section></template>
