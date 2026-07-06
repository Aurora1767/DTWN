<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { EMERGENCY_PLAN_DOCUMENTS, type EmergencyPlanDocument } from '@/constants/emergencyPlans'

const props = defineProps<{
  open: boolean
  recordName?: string
  initialDocumentId?: string
}>()

const emit = defineEmits<{
  close: []
}>()

const activeDocument = ref<EmergencyPlanDocument | null>(null)
const previewUrl = computed(() => {
  if (!activeDocument.value) {
    return ''
  }
  return `${activeDocument.value.url}#view=FitH&toolbar=1`
})

watch(
  () => [props.open, props.initialDocumentId] as const,
  ([open, documentId]) => {
    if (open) {
      activeDocument.value =
        EMERGENCY_PLAN_DOCUMENTS.find((document) => document.id === documentId) ??
        EMERGENCY_PLAN_DOCUMENTS[0] ??
        null
      return
    }
    activeDocument.value = null
  },
)

function previewDocument(document: EmergencyPlanDocument) {
  activeDocument.value = document
}

async function downloadDocument(document: EmergencyPlanDocument) {
  try {
    const response = await fetch(document.url)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const link = window.document.createElement('a')
    link.href = blobUrl
    link.download = document.filename
    link.rel = 'noopener'
    window.document.body.appendChild(link)
    link.click()
    window.document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)
  } catch {
    const link = window.document.createElement('a')
    link.href = document.url
    link.download = document.filename
    link.target = '_blank'
    link.rel = 'noopener'
    window.document.body.appendChild(link)
    link.click()
    window.document.body.removeChild(link)
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="emergency-plan-overlay" @click.self="emit('close')">
      <div class="emergency-plan-modal" role="dialog" aria-label="无锡市应急预案查看" @click.stop>
        <header class="emergency-plan-header">
          <div>
            <p class="emergency-plan-eyebrow">EMERGENCY PLAN</p>
            <h3>无锡市应急预案查看</h3>
            <p v-if="recordName" class="emergency-plan-record">关联记录：{{ recordName }}</p>
          </div>
          <button class="emergency-plan-close" type="button" aria-label="关闭" @click="emit('close')">
            ×
          </button>
        </header>

        <div class="emergency-plan-body">
          <section class="emergency-plan-list">
            <h4>预案文件</h4>
            <ul class="emergency-plan-files">
              <li
                v-for="document in EMERGENCY_PLAN_DOCUMENTS"
                :key="document.id"
                class="emergency-plan-file"
                :class="{ active: activeDocument?.id === document.id }"
              >
                <button
                  type="button"
                  class="emergency-plan-file-name"
                  @click="previewDocument(document)"
                >
                  {{ document.filename }}
                </button>
                <div class="emergency-plan-file-actions">
                  <button
                    type="button"
                    class="emergency-plan-action-btn preview"
                    @click="previewDocument(document)"
                  >
                    预览
                  </button>
                  <button
                    type="button"
                    class="emergency-plan-action-btn download"
                    @click="downloadDocument(document)"
                  >
                    下载
                  </button>
                </div>
              </li>
            </ul>
          </section>

          <section class="emergency-plan-preview">
            <div class="emergency-plan-preview-head">
              <h4>{{ activeDocument ? activeDocument.title : 'PDF 预览' }}</h4>
              <button
                v-if="activeDocument"
                type="button"
                class="emergency-plan-action-btn download"
                @click="downloadDocument(activeDocument)"
              >
                下载当前文件
              </button>
            </div>
            <div class="emergency-plan-preview-frame-wrap">
              <iframe
                v-if="activeDocument"
                class="emergency-plan-preview-frame"
                :src="previewUrl"
                :title="activeDocument.title"
              />
              <p v-else class="emergency-plan-preview-empty">请点击左侧预案文件名进行预览</p>
            </div>
          </section>
        </div>
      </div>
    </div>
  </Teleport>
</template>
