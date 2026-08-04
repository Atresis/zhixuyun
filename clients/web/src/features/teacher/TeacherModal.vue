<script setup lang="ts">
import { X } from "@lucide/vue";
defineProps<{ title: string; subtitle?: string; size?: "small" | "large" | "nested" }>();
defineEmits<{ close: [] }>();
</script>

<template>
  <div class="modal-root open" @keydown.esc="$emit('close')">
    <div class="backdrop" @click="$emit('close')" />
    <section class="modal" :class="size || 'large'" role="dialog" aria-modal="true">
      <header class="modal-titlebar"><div><h2>{{ title }}</h2><p v-if="subtitle">{{ subtitle }}</p></div><button class="close-btn" aria-label="关闭" @click="$emit('close')"><X :size="18" /></button></header>
      <slot />
    </section>
  </div>
</template>
