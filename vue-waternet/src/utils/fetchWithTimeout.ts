export class FetchTimeoutError extends Error {
  constructor(message = '请求超时，请稍后重试') {
    super(message)
    this.name = 'FetchTimeoutError'
  }
}

export class FetchAbortedError extends Error {
  constructor(message = '请求已取消') {
    super(message)
    this.name = 'FetchAbortedError'
  }
}

export async function fetchWithTimeout(
  input: RequestInfo | URL,
  init: RequestInit & { timeoutMs?: number } = {},
): Promise<Response> {
  const { timeoutMs = 30_000, signal, ...rest } = init
  const controller = new AbortController()

  const onParentAbort = () => controller.abort()
  if (signal) {
    if (signal.aborted) {
      controller.abort()
    } else {
      signal.addEventListener('abort', onParentAbort, { once: true })
    }
  }

  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    return await fetch(input, { ...rest, signal: controller.signal })
  } catch (error) {
    if (controller.signal.aborted) {
      if (signal?.aborted) {
        throw new FetchAbortedError()
      }
      throw new FetchTimeoutError()
    }
    throw error
  } finally {
    window.clearTimeout(timeoutId)
    signal?.removeEventListener('abort', onParentAbort)
  }
}
