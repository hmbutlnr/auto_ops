<template>
  <div class="chat-box">
    <!-- 主机选择器 -->
    <div class="host-selector" :class="{ active: selectedHost }">
      <div class="selector-content" v-if="selectedHost">
        <div class="host-avatar">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
            <line x1="8" y1="21" x2="16" y2="21"/>
            <line x1="12" y1="17" x2="12" y2="21"/>
          </svg>
        </div>
        <div class="host-info">
          <span class="host-name">{{ selectedHost.name }}</span>
          <span class="host-address">{{ selectedHost.hostname }}:{{ selectedHost.port }}</span>
        </div>
        <button @click="clearHostSelection" class="clear-btn" title="清除选择">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <button v-else @click="handleShowHostList" class="select-host-btn">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="16"/>
          <line x1="8" y1="12" x2="16" y2="12"/>
        </svg>
        <span>选择主机（可选）</span>
      </button>
    </div>

    <!-- 消息区域 -->
    <div class="messages" ref="messagesRef">
      <div v-if="messages.length === 0 && !isSending" class="empty-chat">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <h3>开始对话</h3>
        <p>可以向AI助手询问服务器信息、运维操作等问题</p>
      </div>
      <MessageItem
        v-for="(msg, index) in messages"
        :key="index"
        :role="msg.role"
        :content="msg.content"
        @host-list-detected="handleHostListDetected"
      />
    </div>

    <!-- AI 思考中提示 -->
    <div v-if="isSending && !isWaitingForHostSelect" class="loading-message">
      <span class="loading-spinner"></span>
      <span>AI 正在思考中...</span>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <div class="input-wrapper">
        <input
          type="text"
          v-model="userInput"
          placeholder="输入消息..."
          @keypress="handleKeyPress"
          :disabled="isWaitingForHostSelect"
        />
        <button @click="sendMessage" class="send-btn" :disabled="isSending || isWaitingForHostSelect || !userInput.trim()">
          <svg v-if="!isSending" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
          <span v-else class="loading-spinner"></span>
        </button>
      </div>
      <button @click="newSession" class="new-session-btn">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="1 4 1 10 7 10"/>
          <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
        </svg>
        新对话
      </button>
    </div>

    <!-- 主机列表弹窗 -->
    <Transition name="modal">
      <div class="host-list-modal" v-if="showHostList" @click.self="closeHostList">
        <div class="modal-content">
          <div class="modal-header">
            <h3>
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                <line x1="8" y1="21" x2="16" y2="21"/>
                <line x1="12" y1="17" x2="12" y2="21"/>
              </svg>
              选择主机
            </h3>
            <button @click="closeHostList" class="close-btn">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div v-if="loadingHosts" class="loading">
              <div class="loading-spinner large"></div>
              <span>加载中...</span>
            </div>
            <div v-else-if="hosts.length === 0" class="empty-state">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                <line x1="8" y1="21" x2="16" y2="21"/>
                <line x1="12" y1="17" x2="12" y2="21"/>
              </svg>
              <p>暂无可用主机，请先在主机管理中添加</p>
            </div>
            <div v-else class="host-list">
              <div
                v-for="host in hosts"
                :key="host.id"
                class="host-item"
                :class="{ selected: selectedHost && selectedHost.id === host.id }"
                @click="selectHost(host)"
              >
                <div class="host-icon" :class="'status-' + (host.status || 'unknown').toLowerCase()">
                  <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                    <line x1="8" y1="21" x2="16" y2="21"/>
                    <line x1="12" y1="17" x2="12" y2="21"/>
                  </svg>
                </div>
                <div class="host-details">
                  <div class="host-name">{{ host.name }}</div>
                  <div class="host-address">{{ host.hostname }}:{{ host.port }}</div>
                </div>
                <div class="host-status" :class="'status-' + (host.status || 'unknown').toLowerCase()">
                  {{ getStatusText(host.status) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { createSession, sendMessage as sendChatMessage, getSelectedHost, clearSelectedHost } from '../api/chat'
import { getHosts } from '../api/host'
import MessageItem from './MessageItem.vue'

const messages = ref([])
const userInput = ref('')
const sessionId = ref('')
const isSending = ref(false)
const isWaitingForHostSelect = ref(false)
const messagesRef = ref(null)
const selectedHost = ref(null)
const showHostList = ref(false)
const hosts = ref([])
const loadingHosts = ref(false)
const pendingQuestion = ref('')

const initSession = async () => {
  try {
    sessionId.value = await createSession()
  } catch (error) {
    console.error('创建会话失败:', error)
  }
}

const loadSelectedHost = async () => {
  try {
    const response = await getSelectedHost(sessionId.value)
    if (response.success && response.host) {
      selectedHost.value = response.host
    } else {
      selectedHost.value = null
    }
  } catch (error) {
    console.error('获取选中主机失败:', error)
    selectedHost.value = null
  }
}

const loadHosts = async () => {
  loadingHosts.value = true
  try {
    const response = await getHosts()
    if (response.success && response.data) {
      hosts.value = response.data
    }
  } catch (error) {
    console.error('加载主机列表失败:', error)
  } finally {
    loadingHosts.value = false
  }
}

const selectHost = async (host) => {
  selectedHost.value = host
  showHostList.value = false
  isWaitingForHostSelect.value = false

  messages.value.push({
    role: 'system',
    content: `已选择主机: ${host.name} (${host.hostname}:${host.port})`
  })

  // 保存主机选择到后端，然后继续发送待处理问题
  try {
    await fetch(`/api/chat/session/${sessionId.value}/select-host`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hostId: host.id })
    })
    
    // 如果有待处理的问题，继续发送
    if (pendingQuestion.value) {
      const question = pendingQuestion.value
      pendingQuestion.value = ''
      
      isSending.value = true
      try {
        const response = await sendChatMessage(sessionId.value, question)
        if (response.success) {
          messages.value.push({ role: 'assistant', content: response.message })
        }
      } catch (error) {
        messages.value.push({ role: 'assistant', content: '处理选择结果失败: ' + error.message })
      } finally {
        isSending.value = false
        await scrollToBottom()
      }
    }
  } catch (error) {
    console.error('保存主机选择失败:', error)
  }
}

const clearHostSelection = async () => {
  try {
    await clearSelectedHost(sessionId.value)
    selectedHost.value = null
    messages.value.push({
      role: 'system',
      content: '已清除主机选择'
    })
  } catch (error) {
    console.error('清除主机选择失败:', error)
  }
}

const handleShowHostList = async () => {
  showHostList.value = true
  await loadHosts()
}

const closeHostList = () => {
  showHostList.value = false
  if (isWaitingForHostSelect.value) {
    isWaitingForHostSelect.value = false
    pendingQuestion.value = ''
  }
}

const newSession = async () => {
  messages.value = []
  selectedHost.value = null
  await initSession()
}

const sendMessage = async () => {
  const message = userInput.value.trim()
  if (!message || isSending.value) return

  messages.value.push({ role: 'user', content: message })
  userInput.value = ''
  isSending.value = true

  try {
    const response = await sendChatMessage(sessionId.value, message)
    if (response.success) {
      sessionId.value = response.sessionId
      if (response.needsHostSelection) {
        isWaitingForHostSelect.value = true
        pendingQuestion.value = message
        messages.value.push({ role: 'assistant', content: response.message })
        showHostList.value = true
        await loadHosts()
      } else {
        messages.value.push({ role: 'assistant', content: response.message })
        // 更新当前主机（可能是从消息中自动检测到的）
        if (response.selectedHost) {
          selectedHost.value = response.selectedHost
        }
        await loadSelectedHost()
      }
    } else {
      messages.value.push({ role: 'assistant', content: '错误: ' + response.error })
    }
  } catch (error) {
    messages.value.push({ role: 'assistant', content: '请求失败: ' + error.message })
  } finally {
    isSending.value = false
    await scrollToBottom()
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const handleKeyPress = (e) => {
  if (e.key === 'Enter') {
    sendMessage()
  }
}

const handleHostListDetected = async () => {
  isWaitingForHostSelect.value = true
  showHostList.value = true
  await loadHosts()
}

const getStatusText = (status) => {
  const map = { ONLINE: '在线', OFFLINE: '离线', UNKNOWN: '未知' }
  return map[status] || status
}

watch(showHostList, (newValue) => {
  if (newValue) {
    loadHosts()
  }
})

initSession()
</script>

<style scoped>
.chat-box {
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  height: 680px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.6);
}

.host-selector {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(180deg, #fafbfc 0%, #f1f5f9 100%);
}

.host-selector.active {
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.05) 0%, rgba(99, 102, 241, 0.02) 100%);
}

.selector-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.host-avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.host-info {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.host-info .host-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 14px;
}

.host-info .host-address {
  font-size: 12px;
  color: var(--text-muted);
}

.clear-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  border: none;
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: rgba(239, 68, 68, 0.2);
}

.select-host-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  padding: 12px;
  border: 2px dashed var(--border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.select-host-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(99, 102, 241, 0.04);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--text-secondary);
}

.empty-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(129, 140, 248, 0.1) 100%);
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  margin-bottom: 20px;
}

.empty-chat h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.empty-chat p {
  font-size: 14px;
  color: var(--text-muted);
}

.input-area {
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fafbfc;
}

.input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  background: white;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 4px;
  transition: all 0.2s;
}

.input-wrapper:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.input-wrapper input {
  flex: 1;
  padding: 12px 16px;
  border: none;
  outline: none;
  font-size: 15px;
  background: transparent;
}

.input-wrapper input::placeholder {
  color: var(--text-muted);
}

.send-btn {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  border: none;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.new-session-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: white;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.new-session-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

/* 弹窗样式 */
.host-list-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: var(--radius-xl);
  width: 90%;
  max-width: 500px;
  max-height: 75vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.modal-header h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
}

.modal-header h3 svg {
  color: var(--primary);
}

.close-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--bg-primary);
  color: var(--text-primary);
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.loading, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  padding: 40px 20px;
  gap: 12px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-spinner.large {
  width: 32px;
  height: 32px;
  border-width: 3px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.host-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.host-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 2px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s;
}

.host-item:hover {
  border-color: var(--primary-light);
  background: rgba(99, 102, 241, 0.04);
}

.host-item.selected {
  border-color: var(--primary);
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08) 0%, rgba(99, 102, 241, 0.04) 100%);
}

.host-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  color: var(--text-secondary);
}

.host-icon.status-online {
  background: rgba(16, 185, 129, 0.1);
  color: var(--secondary);
}

.host-icon.status-offline {
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger);
}

.host-details {
  flex: 1;
}

.host-details .host-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 15px;
}

.host-details .host-address {
  font-size: 13px;
  color: var(--text-muted);
}

.host-status {
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.host-status.status-online {
  background: rgba(16, 185, 129, 0.1);
  color: var(--secondary);
}

.host-status.status-offline {
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger);
}

.host-status.status-unknown {
  background: var(--bg-primary);
  color: var(--text-muted);
}

/* 弹窗动画 */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-content,
.modal-leave-to .modal-content {
  transform: scale(0.95) translateY(20px);
}

/* 加载提示 */
.loading-message {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin-bottom: 10px;
  background: #f5f5f5;
  border-radius: 8px;
  color: #666;
  font-size: 14px;
}
</style>