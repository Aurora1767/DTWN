export interface BoundaryControlPoint {
  /** Hours from simulation start (0 … durationHours). */
  t: number
  value: number
}

export function interpolateBoundarySeries(
  controlPoints: BoundaryControlPoint[],
  stepCount: number,
  dtSeconds: number,
): number[] {
  if (stepCount < 1) {
    return []
  }

  const sorted = [...controlPoints].sort((left, right) => left.t - right.t)
  if (!sorted.length) {
    return []
  }

  const pointCount = stepCount + 1
  const series: number[] = []

  for (let step = 0; step < pointCount; step += 1) {
    const tHours = (step * dtSeconds) / 3600
    series.push(interpolateAt(sorted, tHours))
  }

  return series
}

function interpolateAt(points: BoundaryControlPoint[], tHours: number): number {
  const first = points[0]
  if (!first) {
    return 0
  }
  if (points.length === 1) {
    return first.value
  }

  if (tHours <= first.t) {
    return first.value
  }

  const last = points[points.length - 1]
  if (!last) {
    return first.value
  }
  if (tHours >= last.t) {
    return last.value
  }

  for (let index = 0; index < points.length - 1; index += 1) {
    const start = points[index]
    const end = points[index + 1]
    if (!start || !end) {
      continue
    }
    if (tHours >= start.t && tHours <= end.t) {
      const span = end.t - start.t
      if (span <= 0) {
        return end.value
      }
      const ratio = (tHours - start.t) / span
      return start.value + ratio * (end.value - start.value)
    }
  }

  return last.value
}

export function buildDefaultControlPoints(
  durationHours: number,
  defaultValue: number,
): BoundaryControlPoint[] {
  return [
    { t: 0, value: defaultValue },
    { t: Math.max(durationHours, 0), value: defaultValue },
  ]
}

export function formatBoundaryTimeLabel(startIso: string, offsetHours: number): string {
  const start = new Date(startIso)
  if (Number.isNaN(start.getTime())) {
    return `${offsetHours.toFixed(1)}h`
  }
  const point = new Date(start.getTime() + offsetHours * 3600 * 1000)
  const month = `${point.getMonth() + 1}`.padStart(2, '0')
  const day = `${point.getDate()}`.padStart(2, '0')
  const hour = `${point.getHours()}`.padStart(2, '0')
  const minute = `${point.getMinutes()}`.padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}
