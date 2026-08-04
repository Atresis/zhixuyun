<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { Search } from "@lucide/vue";
import { adminApi, type AuditLog } from "./admin.api";

const rows = ref<AuditLog[]>([]); const keyword = ref(""); const page = ref(0); const totalPages = ref(0); const error = ref("");
async function load() { try { const result = await adminApi.logs(page.value, keyword.value); rows.value = result.content; totalPages.value = result.totalPages; error.value = ""; } catch (cause) { error.value = (cause as Error).message; } }
let timer: number | undefined;
watch(keyword, () => { page.value = 0; window.clearTimeout(timer); timer = window.setTimeout(load, 250); });
onMounted(load);
</script>

<template><section class="admin-page"><div class="admin-page-head"><div><h1>操作日志</h1><p>核对账号、课程、班级和系统配置的管理操作。</p></div></div><div class="admin-toolbar admin-panel"><label class="admin-search"><Search :size="16" /><input v-model="keyword" placeholder="操作人、动作或详情" /></label></div><p v-if="error" class="admin-form-error">{{ error }}</p><div class="admin-panel admin-table-wrap"><table class="admin-table"><thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>详情</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</td><td>{{ item.actorName || '系统' }}</td><td>{{ item.action }}</td><td>{{ item.targetType || '--' }} {{ item.targetId || '' }}</td><td>{{ item.detail || '--' }}</td></tr></tbody></table><div v-if="!rows.length" class="admin-empty">暂无日志</div></div><div class="admin-pagination"><button :disabled="page <= 0" @click="page--; load()">上一页</button><span>第 {{ page + 1 }} / {{ Math.max(1, totalPages) }} 页</span><button :disabled="page + 1 >= totalPages" @click="page++; load()">下一页</button></div></section></template>
