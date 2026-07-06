import { ref, watch } from 'vue'

import type { ScenarioWarningAlert, ScenarioWarningStatus } from '@/types/platform'

const DEFAULT_STORAGE_KEY = 'waternet-scenario-warning-history'
const MAX_STORED_ALERTS = 300
const MAX_STORED_PROCESSED = 150

function sanitizeAlerts(items: ScenarioWarningAlert[]): ScenarioWarningAlert[] {
  if (items.length <= MAX_STORED_ALERTS) {
    return items
  }

  const processed = items
    .filter((item) => item.status === 'PROCESSED')
    .sort((left, right) =>
      (right.processedAt ?? right.triggeredAt).localeCompare(left.processedAt ?? left.triggeredAt),
    )
    .slice(0, MAX_STORED_PROCESSED)
  const active = items.filter((item) => item.status !== 'PROCESSED')
  const maxActive = Math.max(0, MAX_STORED_ALERTS - processed.length)

  if (active.length <= maxActive) {
    return [...active, ...processed]
  }

  return [...active.slice(0, maxActive), ...processed]
}

function writeHistory(storageKey: string, items: ScenarioWarningAlert[]) {
  const sanitized = sanitizeAlerts(items)
  try {
    localStorage.setItem(storageKey, JSON.stringify(sanitized))
    return sanitized
  } catch (error) {
    if (!(error instanceof DOMException) || error.name !== 'QuotaExceededError') {
      console.warn('[waternet] failed to persist warning history', error)
      return sanitized
    }

    try {
      localStorage.removeItem(storageKey)
      const trimmed = sanitizeAlerts(sanitized.slice(0, 80))
      localStorage.setItem(storageKey, JSON.stringify(trimmed))
      return trimmed
    } catch (retryError) {
      console.warn('[waternet] warning history storage quota exceeded', retryError)
      try {
        localStorage.removeItem(storageKey)
      } catch {
        // ignore cleanup failure
      }
      return []
    }
  }
}

function loadHistory(storageKey: string): ScenarioWarningAlert[] {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw) as ScenarioWarningAlert[]
    if (!Array.isArray(parsed)) {
      localStorage.removeItem(storageKey)
      return []
    }
    const sanitized = sanitizeAlerts(parsed)
    if (sanitized.length !== parsed.length || raw.length > 512_000) {
      writeHistory(storageKey, sanitized)
    }
    return sanitized
  } catch {
    try {
      localStorage.removeItem(storageKey)
    } catch {
      // ignore cleanup failure
    }
    return []
  }
}

export function useScenarioWarningHistory(storageKey = DEFAULT_STORAGE_KEY) {
  const alerts = ref<ScenarioWarningAlert[]>(loadHistory(storageKey))

  watch(
    alerts,
    (items) => {
      const persisted = writeHistory(storageKey, items)
      if (persisted.length !== items.length) {
        alerts.value = persisted
      }
    },
    { deep: true },
  )

  function replaceAlerts(items: ScenarioWarningAlert[]) {
    alerts.value = sanitizeAlerts(items)
  }

  function updateStatus(id: string, status: ScenarioWarningStatus) {
    const now = new Date().toISOString()
    alerts.value = alerts.value.map((item) => {
      if (item.id !== id) {
        return item
      }
      if (status === 'CONFIRMED') {
        return { ...item, status, confirmedAt: now }
      }
      if (status === 'PROCESSED') {
        return {
          ...item,
          status,
          processedAt: now,
          confirmedAt: item.confirmedAt ?? now,
        }
      }
      return { ...item, status }
    })
  }

  function clearProcessed(recordId: number | null) {
    alerts.value = alerts.value.filter((item) => {
      if (recordId === null) {
        return item.status !== 'PROCESSED'
      }
      return item.recordId !== recordId || item.status !== 'PROCESSED'
    })
  }

  return {
    alerts,
    replaceAlerts,
    updateStatus,
    clearProcessed,
  }
}
