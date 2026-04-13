import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

// 创建新会话
export const createSession = async () => {
  const response = await apiClient.post('/chat/new-session')
  return response.data
}

// 发送聊天消息
export const sendMessage = async (sessionId, message) => {
  const response = await apiClient.post('/chat/chat', {
    sessionId,
    message
  })
  return response.data
}

// 获取当前选中的主机
export const getSelectedHost = async (sessionId) => {
  const response = await apiClient.get(`/chat/session/${sessionId}/selected-host`)
  return response.data
}

// 清除选中的主机
export const clearSelectedHost = async (sessionId) => {
  const response = await apiClient.delete(`/chat/session/${sessionId}/selected-host`)
  return response.data
}
