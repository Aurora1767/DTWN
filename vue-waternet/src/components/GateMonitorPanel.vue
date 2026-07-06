<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()

const videoRef = ref<HTMLVideoElement | null>(null)

const activeGate = computed(() =>
  store.activeGateId != null
    ? store.gates.find(g => g.id === store.activeGateId) ?? null
    : null
)

const videoSrc = computed(() => {
  if (!activeGate.value) return null
  const id = activeGate.value.id
  const phase = activeGate.value.openingPct > 0 ? 'after' : 'before'
  return `${import.meta.env.BASE_URL}videos/gate${id}-${phase}.mp4`
})

const label = computed(() => {
  if (!activeGate.value) return null
  return activeGate.value.openingPct > 0 ? '开闸后实况' : '开闸前实况'
})

const videoError = ref(false)

watch(videoSrc, () => {
  videoError.value = false
})

async function onCanPlay() {
  try { await videoRef.value?.play() } catch { /* autoplay blocked */ }
}

function onError() {
  videoError.value = true
}
</script>

<template>
  <div class="gm-wrap">
    <div class="gm-header">
      <span class="gm-eyebrow">MONITOR</span>
      <strong class="gm-title">实时监控</strong>
      <span v-if="activeGate" class="gm-badge">{{ activeGate.name }} · {{ label }}</span>
      <span v-else class="gm-hint">点击视角切换按钮以启动监控</span>
    </div>

    <div class="gm-body">
      <template v-if="activeGate && videoSrc && !videoError">
        <video
          ref="videoRef"
          class="gm-video"
          :src="videoSrc"
          autoplay
          loop
          muted
          playsinline
          @canplay="onCanPlay"
          @error="onError"
        />
      </template>
      <div v-else class="gm-placeholder">
        <svg class="gm-placeholder-icon" viewBox="0 0 48 48" fill="none">
          <circle cx="24" cy="24" r="22" stroke="rgba(0,242,255,0.2)" stroke-width="1.5"/>
          <path d="M18 16 L34 24 L18 32 Z" fill="rgba(0,242,255,0.25)" stroke="rgba(0,242,255,0.5)" stroke-width="1.2"/>
        </svg>
        <p v-if="videoError">暂无该水闸视频文件<br><small>{{ videoSrc }}</small></p>
        <p v-else>请在左侧闸门监测中<br>点击「视角切换」启动监控</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gm-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 10px;
  border: 1px solid rgba(0, 242, 255, 0.42);
  background: linear-gradient(180deg, rgba(9, 30, 61, 0.82), rgba(9, 30, 61, 0.68));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow:
    inset 0 0 28px rgba(0, 242, 255, 0.06),
    0 0 10px rgba(0, 242, 255, 0.1),
    0 4px 20px rgba(0, 11, 33, 0.42);
}

.gm-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-bottom: 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(0, 242, 255, 0.28);
}

.gm-eyebrow {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: #00f2ff;
  text-shadow: 0 0 8px rgba(0, 242, 255, 0.35);
}

.gm-title {
  font-size: 13px;
  font-weight: 700;
  color: #ffffff;
}

.gm-badge {
  margin-left: auto;
  padding: 2px 8px;
  border: 1px solid rgba(0, 242, 255, 0.4);
  border-radius: 3px;
  background: rgba(0, 60, 90, 0.6);
  color: #00f2ff;
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.gm-hint {
  margin-left: auto;
  color: rgba(160, 216, 239, 0.4);
  font-size: 10px;
}

.gm-body {
  flex: 1;
  min-height: 0;
  height: 0;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(0, 242, 255, 0.14);
  border-radius: 4px;
  background: rgba(0, 8, 20, 0.6);
}

.gm-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.gm-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(160, 216, 239, 0.35);
  font-size: 11px;
  line-height: 1.7;
  text-align: center;
}

.gm-placeholder p {
  margin: 0;
}

.gm-placeholder small {
  display: block;
  margin-top: 4px;
  color: rgba(160, 216, 239, 0.25);
  font-size: 9px;
  word-break: break-all;
}
</style>
