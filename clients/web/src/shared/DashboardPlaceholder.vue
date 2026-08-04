<script setup lang="ts">
import { useRoute } from "vue-router";
const route = useRoute();
</script>

<template>
  <main class="placeholder-page"><p>ZHIXUYUN</p><h1>{{ route.meta.title }}</h1><span>业务工作台正在接入。</span></main>
</template>
