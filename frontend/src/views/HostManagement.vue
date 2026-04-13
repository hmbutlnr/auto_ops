<template>
  <div class="host-management">
    <div class="header">
      <div class="header-title">
        <h2>主机管理</h2>
        <p>管理您的服务器资产</p>
      </div>
      <div class="actions">
        <button @click="refreshStatus" :disabled="loading" class="btn-secondary">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"/>
            <polyline points="1 20 1 14 7 14"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
          {{ loading ? '刷新中...' : '刷新状态' }}
        </button>
        <button @click="showAddDialog" class="btn-primary">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          添加主机
        </button>
      </div>
    </div>

    <div class="host-list">
      <div v-if="hosts.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
            <line x1="8" y1="21" x2="16" y2="21"/>
            <line x1="12" y1="17" x2="12" y2="21"/>
          </svg>
        </div>
        <h3>暂无主机</h3>
        <p>点击"添加主机"开始添加您的第一台服务器</p>
      </div>

      <div v-else class="host-grid">
        <div v-for="host in hosts" :key="host.id" class="host-card">
          <div class="card-header">
            <div class="host-icon" :class="'status-' + host.status.toLowerCase()">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                <line x1="8" y1="21" x2="16" y2="21"/>
                <line x1="12" y1="17" x2="12" y2="21"/>
              </svg>
            </div>
            <div class="host-info">
              <h3>{{ host.name }}</h3>
              <span class="status-badge" :class="'status-' + host.status.toLowerCase()">
                {{ getStatusText(host.status) }}
              </span>
            </div>
          </div>
          <div class="card-body">
            <div class="info-row">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="2" y1="12" x2="22" y2="12"/>
                <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
              </svg>
              <span>{{ host.hostname }}:{{ host.port }}</span>
            </div>
            <div class="info-row">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <span>{{ host.username }}</span>
            </div>
            <div class="info-row" v-if="host.description">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              <span>{{ host.description }}</span>
            </div>
          </div>
          <div class="card-actions">
            <button @click="testHostConnection(host.id)" class="btn-test" :disabled="testing === host.id">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              {{ testing === host.id ? '测试中...' : '测试' }}
            </button>
            <button @click="showEditDialog(host)" class="btn-edit">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              编辑
            </button>
            <button @click="confirmDelete(host.id)" class="btn-delete">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
              删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <Transition name="modal">
      <div v-if="dialogVisible" class="dialog-overlay" @click="closeDialog">
        <div class="dialog" @click.stop>
          <div class="dialog-header">
            <h3>{{ isEdit ? '编辑主机' : '添加主机' }}</h3>
            <button @click="closeDialog" class="btn-close">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="dialog-body">
            <form @submit.prevent="submitForm">
              <div class="form-row">
                <div class="form-group">
                  <label>主机名称 *</label>
                  <input v-model="formData.name" type="text" required placeholder="例如: 生产服务器1" />
                </div>
              </div>
              <div class="form-row two-col">
                <div class="form-group">
                  <label>主机地址 *</label>
                  <input v-model="formData.hostname" type="text" required placeholder="例如: 192.168.1.100" />
                </div>
                <div class="form-group">
                  <label>端口</label>
                  <input v-model.number="formData.port" type="number" min="1" max="65535" />
                </div>
              </div>
              <div class="form-row two-col">
                <div class="form-group">
                  <label>用户名 *</label>
                  <input v-model="formData.username" type="text" required placeholder="例如: root" />
                </div>
                <div class="form-group">
                  <label>密码 {{ isEdit ? '(留空则不修改)' : '*' }}</label>
                  <input v-model="formData.password" type="password" :required="!isEdit" />
                </div>
              </div>
              <div class="form-group">
                <label>描述</label>
                <textarea v-model="formData.description" rows="3" placeholder="可选的主机描述"></textarea>
              </div>
              <div class="form-actions">
                <button type="button" @click="closeDialog" class="btn-cancel">取消</button>
                <button type="submit" class="btn-submit" :disabled="submitting">
                  {{ submitting ? '提交中...' : '确定' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getHosts, createHost, updateHost, deleteHost, testConnection, refreshAllStatus } from '../api/host'

const hosts = ref([])
const loading = ref(false)
const testing = ref(null)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const formData = ref({
  name: '',
  hostname: '',
  port: 22,
  username: '',
  password: '',
  description: ''
})

const loadHosts = async () => {
  try {
    const response = await getHosts()
    if (response.success) {
      hosts.value = response.data || []
    }
  } catch (error) {
    console.error('加载主机列表失败:', error)
    alert('加载主机列表失败: ' + error.message)
  }
}

const refreshStatus = async () => {
  loading.value = true
  try {
    const response = await refreshAllStatus()
    if (response.success) {
      hosts.value = response.data || []
      alert('状态刷新成功')
    }
  } catch (error) {
    console.error('刷新状态失败:', error)
    alert('刷新状态失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const testHostConnection = async (id) => {
  testing.value = id
  try {
    const response = await testConnection(id)
    alert(response.message)
    await loadHosts()
  } catch (error) {
    console.error('测试连接失败:', error)
    alert('测试连接失败: ' + error.message)
  } finally {
    testing.value = null
  }
}

const showAddDialog = () => {
  isEdit.value = false
  editId.value = null
  formData.value = {
    name: '',
    hostname: '',
    port: 22,
    username: '',
    password: '',
    description: ''
  }
  dialogVisible.value = true
}

const showEditDialog = (host) => {
  isEdit.value = true
  editId.value = host.id
  formData.value = {
    name: host.name,
    hostname: host.hostname,
    port: host.port,
    username: host.username,
    password: '',
    description: host.description || ''
  }
  dialogVisible.value = true
}

const closeDialog = () => {
  dialogVisible.value = false
  formData.value = {
    name: '',
    hostname: '',
    port: 22,
    username: '',
    password: '',
    description: ''
  }
}

const submitForm = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      const response = await updateHost(editId.value, formData.value)
      if (response.success) {
        alert('更新成功')
        closeDialog()
        await loadHosts()
      }
    } else {
      const response = await createHost(formData.value)
      if (response.success) {
        alert('添加成功')
        closeDialog()
        await loadHosts()
      }
    }
  } catch (error) {
    console.error('提交失败:', error)
    alert('操作失败: ' + error.message)
  } finally {
    submitting.value = false
  }
}

const confirmDelete = async (id) => {
  if (!confirm('确定要删除这个主机吗?')) return

  try {
    const response = await deleteHost(id)
    if (response.success) {
      alert('删除成功')
      await loadHosts()
    }
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败: ' + error.message)
  }
}

const getStatusText = (status) => {
  const statusMap = { ONLINE: '在线', OFFLINE: '离线', UNKNOWN: '未知' }
  return statusMap[status] || status
}

onMounted(() => {
  loadHosts()
})
</script>

<style scoped>
.host-management {
  padding: 24px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.header-title h2 {
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 4px 0;
}

.header-title p {
  color: var(--text-muted);
  font-size: 14px;
  margin: 0;
}

.actions {
  display: flex;
  gap: 12px;
}

.btn-primary, .btn-secondary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
}

.btn-secondary {
  background: white;
  color: var(--text-secondary);
  border: 1px solid var(--border);
}

.btn-secondary:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.host-list {
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  padding: 24px;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-secondary);
}

.empty-icon {
  width: 100px;
  height: 100px;
  margin: 0 auto 24px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(129, 140, 248, 0.1) 100%);
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.empty-state p {
  font-size: 14px;
  color: var(--text-muted);
}

.host-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}

.host-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  transition: all 0.2s;
  background: white;
}

.host-card:hover {
  border-color: var(--primary-light);
  box-shadow: var(--shadow-md);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.host-icon {
  width: 48px;
  height: 48px;
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

.host-icon.status-unknown {
  background: var(--bg-primary);
  color: var(--text-muted);
}

.host-info {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.host-info h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.status-online {
  background: rgba(16, 185, 129, 0.1);
  color: var(--secondary);
}

.status-badge.status-offline {
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger);
}

.status-badge.status-unknown {
  background: var(--bg-primary);
  color: var(--text-muted);
}

.card-body {
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 14px;
  color: var(--text-secondary);
}

.info-row svg {
  color: var(--text-muted);
  flex-shrink: 0;
}

.card-actions {
  display: flex;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.btn-test, .btn-edit, .btn-delete {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border: none;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-test {
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary);
}

.btn-test:hover:not(:disabled) {
  background: rgba(99, 102, 241, 0.2);
}

.btn-test:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-edit {
  background: rgba(245, 158, 11, 0.1);
  color: var(--warning);
}

.btn-edit:hover {
  background: rgba(245, 158, 11, 0.2);
}

.btn-delete {
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger);
}

.btn-delete:hover {
  background: rgba(239, 68, 68, 0.2);
}

/* 对话框样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.dialog {
  background: white;
  border-radius: var(--radius-xl);
  width: 90%;
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid var(--border);
}

.dialog-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 600;
}

.btn-close {
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

.btn-close:hover {
  background: var(--bg-primary);
  color: var(--text-primary);
}

.dialog-body {
  padding: 24px;
}

.form-row {
  margin-bottom: 16px;
}

.form-row.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-primary);
  font-weight: 500;
  font-size: 14px;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  transition: all 0.2s;
  background: var(--bg-primary);
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  background: white;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}

.btn-cancel, .btn-submit {
  padding: 12px 24px;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel {
  background: var(--bg-primary);
  color: var(--text-secondary);
}

.btn-cancel:hover {
  background: var(--border);
}

.btn-submit {
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
  color: white;
}

.btn-submit:hover:not(:disabled) {
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

.modal-enter-from .dialog,
.modal-leave-to .dialog {
  transform: scale(0.95) translateY(20px);
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }
  
  .form-row.two-col {
    grid-template-columns: 1fr;
  }
  
  .host-grid {
    grid-template-columns: 1fr;
  }
}
</style>