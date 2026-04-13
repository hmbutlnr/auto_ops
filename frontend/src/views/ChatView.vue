<template>
  <div class="chat-view">
    <ChatBox />
  </div>
</template>

<script setup>
import ChatBox from '../components/ChatBox.vue'
</script>

<style scoped>
.chat-view {
  max-width: 900px;
  margin: 0 auto;
}
</style>