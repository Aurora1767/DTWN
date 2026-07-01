<script setup lang="ts">
defineProps<{
  title: string
  eyebrow?: string
}>()
</script>

<template>
  <section class="panel-shell">
    <div class="panel-heading">
      <span v-if="eyebrow" class="panel-eyebrow">{{ eyebrow }}</span>
      <h2>{{ title }}</h2>
    </div>
    <slot />
  </section>
</template>
