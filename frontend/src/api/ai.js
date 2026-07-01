import http from './index'

/**
 * AI 对话（SSE 流式）
 * @param {String}   message  - 用户消息
 * @param {Object}   context  - 页面上下文 { contextType, contextName, contextId, roleName }
 * @param {Object}   callbacks - { onChunk, onDone, onError }
 */
export function chatStream(message, context, { onChunk, onDone, onError, signal }) {
  const token = localStorage.getItem('token')
  const url = '/api/v1/ai/chat'

  fetch(url, {
    signal,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ message, context }),
  })
    .then(async (response) => {
      if (!response.ok) {
        const errData = await response.json().catch(() => ({}))
        onError && onError(errData.message || '请求失败')
        return
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop()

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.substring(5).trim()
            if (data === '[DONE]') {
              onDone && onDone()
              return
            }
            onChunk && onChunk(data)
          }
        }
      }
      onDone && onDone()
    })
    .catch((err) => {
      if (err.name === 'AbortError') return // 用户主动停止，不报错
      onError && onError(err.message || '网络异常')
    })
}

/**
 * 上传文件用于 AI 分析
 */
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/ai/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 获取对话历史
 */
export function getHistory(limit = 50) {
  return http.get('/ai/history', { params: { limit } })
}

/**
 * 清空对话历史
 */
export function clearHistory() {
  return http.delete('/ai/history')
}
