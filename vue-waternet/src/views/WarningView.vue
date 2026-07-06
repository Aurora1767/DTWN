<script setup lang="ts">
</script>

<template>
  <div class="water-env-page">
    <div class="water-env-placeholder">
      <span class="water-env-eyebrow">WATER ENVIRONMENT</span>
      <h2 class="water-env-title">水环境业务</h2>
      <p class="water-env-hint">功能建设中</p>
    </div>
  </div>
</template>

<style scoped>
.water-env-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 60vh;
  background: transparent;
}

.water-env-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  opacity: 0.45;
}

.water-env-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #4dc9f6;
  text-transform: uppercase;
}

.water-env-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #b8deff;
  letter-spacing: 0.06em;
}

.water-env-hint {
  margin: 0;
  font-size: 13px;
  color: rgba(180, 220, 255, 0.5);
  letter-spacing: 0.08em;
}
</style>
