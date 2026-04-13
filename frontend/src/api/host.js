import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api/host',
  headers: {
    'Content-Type': 'application/json'
  }
})

// 获取所有主机
export const getHosts = async () => {
  const response = await apiClient.get('')
  return response.data
}

// 获取单个主机
export const getHostById = async (id) => {
  const response = await apiClient.get(`/${id}`)
  return response.data
}

// 创建主机
export const createHost = async (hostData) => {
  const response = await apiClient.post('', hostData)
  return response.data
}

// 更新主机
export const updateHost = async (id, hostData) => {
  const response = await apiClient.put(`/${id}`, hostData)
  return response.data
}

// 删除主机
export const deleteHost = async (id) => {
  const response = await apiClient.delete(`/${id}`)
  return response.data
}

// 测试主机连接
export const testConnection = async (id) => {
  const response = await apiClient.post(`/${id}/test`)
  return response.data
}

// 刷新所有主机状态
export const refreshAllStatus = async () => {
  const response = await apiClient.post('/refresh-status')
  return response.data
}
