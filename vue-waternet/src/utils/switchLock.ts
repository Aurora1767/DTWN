import type { Ref } from 'vue'

const switchDepthByRef = new WeakMap<Ref<boolean>, number>()

export function acquireSwitchLock(switching: Ref<boolean>) {
  const depth = (switchDepthByRef.get(switching) ?? 0) + 1
  switchDepthByRef.set(switching, depth)
  switching.value = true
}

export function releaseSwitchLock(switching: Ref<boolean>) {
  const depth = Math.max(0, (switchDepthByRef.get(switching) ?? 1) - 1)
  switchDepthByRef.set(switching, depth)
  if (depth === 0) {
    switching.value = false
  }
}
