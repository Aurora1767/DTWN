<script setup lang="ts">
const props = defineProps<{
  values: number[]
  color?: string
}>()

function points() {
  const values = props.values.length ? props.values : [0]
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = Math.max(max - min, 0.001)
  return values
    .map((value, index) => {
      const x = values.length === 1 ? 0 : (index / (values.length - 1)) * 100
      const y = 42 - ((value - min) / range) * 34
      return `${x},${y}`
    })
    .join(' ')
}
</script>

<template>
  <svg class="mini-line" viewBox="0 0 100 48" preserveAspectRatio="none">
    <polyline :points="points()" :stroke="color ?? '#35d4ff'" />
  </svg>
</template>
