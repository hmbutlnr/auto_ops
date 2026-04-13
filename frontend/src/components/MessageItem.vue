<template>
  <div class="message" :class="role">
    <div class="avatar" v-if="role === 'assistant'">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
      </svg>
    </div>
    <div class="bubble" v-html="formattedContent"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  role: {
    type: String,
    required: true,
    validator: (value) => ['user', 'assistant', 'system'].includes(value)
  },
  content: {
    type: String,
    required: true
  }
})

const formattedContent = computed(() => {
  let content = props.content
  content = content.replace(/\n/g, '<br>')
  content = content.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  content = content.replace(/\*(.*?)\*/g, '<em>$1</em>')
  content = content.replace(/`(.*?)`/g, '<code>$1</code>')
  return content
})
</script>

<style scoped>
.message {
  display: flex;
  gap: 12px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  justify-content: flex-end;
}

.message.system {
  justify-content: center;
}

.avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.bubble {
  max-width: 75%;
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  line-height: 1.6;
  word-wrap: break-word;
}

.message.user .bubble {
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .bubble {
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  color: var(--text-primary);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.message.system .bubble {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(251, 191, 36, 0.05) 100%);
  color: #b45309;
  font-size: 13px;
  text-align: center;
  max-width: 90%;
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 20px;
}

.bubble :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.bubble :deep(strong) {
  font-weight: 600;
}

.bubble :deep(br) {
  display: block;
  content: '';
  margin: 4px 0;
}
</style>