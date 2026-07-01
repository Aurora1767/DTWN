<script setup lang="ts">
import { onMounted } from 'vue'

import MainMapStage from '@/components/MainMapStage.vue'
import PanelShell from '@/components/PanelShell.vue'
import { usePlatformStore } from '@/stores/platform'
import type { MapLayerKey } from '@/types/platform'

const store = usePlatformStore()

onMounted(() => {
  void store.loadDashboard()
})

const layerOptions: Array<{ key: MapLayerKey; label: string; description: string }> = [
  { key: 'rivers', label: '河道线网', description: '蠡河、大溪港、京杭运河概化河段' },
  { key: 'nodes', label: '水位节点', description: '边界点、交汇点和关键监测节点' },
  { key: 'structures', label: '闸站泵站', description: '水利工程点位和运行参数弹窗' },
  { key: 'warnings', label: '预警事件', description: '后续叠加超限节点和风险区域' },
  { key: 'simulation', label: '模型结果', description: '后续叠加仿真水位、流量和流速结果' },
]
</script>

<template>
  <div class="workspace-grid">
    <MainMapStage />
    <PanelShell title="图层控制" eyebrow="LAYERS">
      <div class="layer-list">
        <label v-for="layer in layerOptions" :key="layer.key" class="layer-toggle">
          <input
            type="checkbox"
            :checked="store.mapLayers[layer.key]"
            @change="store.setMapLayer(layer.key, ($event.target as HTMLInputElement).checked)"
          />
          <span>
            <strong>{{ layer.label }}</strong>
            <em>{{ layer.description }}</em>
          </span>
        </label>
      </div>
    </PanelShell>
  </div>
</template>
