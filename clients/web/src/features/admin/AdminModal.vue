<script setup lang="ts">
import { X } from "@lucide/vue";

withDefaults(defineProps<{ title: string; subtitle?: string; wide?: boolean }>(), {
  subtitle: "",
  wide: false,
});

defineEmits<{ close: [] }>();
</script>

<template>
  <div class="admin-modal-backdrop" role="presentation" @click.self="$emit('close')">
    <section class="admin-dialog" :class="{ 'admin-dialog--wide': wide }" role="dialog" aria-modal="true" :aria-label="title">
      <header class="admin-dialog__header">
        <div>
          <h2>{{ title }}</h2>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <button class="admin-dialog__close" type="button" aria-label="关闭" title="关闭" @click="$emit('close')"><X :size="20" /></button>
      </header>
      <div class="admin-dialog__body"><slot /></div>
      <footer v-if="$slots.footer" class="admin-dialog__footer"><slot name="footer" /></footer>
    </section>
  </div>
</template>
