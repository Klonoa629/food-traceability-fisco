<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { parseEvidence, verifyEvidence } from '../api/evidence'
import { shortHash } from '../constants/maps'

const props = defineProps<{ value: string }>()
const evidence = computed(() => parseEvidence(props.value))
const checking = ref(false)
const result = ref('')
watch(() => props.value, () => { result.value = '' })

async function verify() {
  const reference = props.value
  const hash = evidence.value.hash
  checking.value = true
  result.value = ''
  try {
    const response = await fetch(`/api/storage/${hash}`)
    if (!response.ok) throw new Error(response.status === 404 ? '原文缺失，无法核验' : '原文读取失败，请稍后重试或联系管理员')
    const matches = await verifyEvidence(await response.arrayBuffer(), hash)
    if (reference === props.value) result.value = matches ? '核验通过：文件与链上哈希一致' : '核验失败：文件与链上哈希不符'
  } catch (error) {
    if (reference === props.value) result.value = error instanceof Error ? error.message : '核验失败'
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <span class="evidence-badge">
    <span :title="value">{{ evidence.label }} <span class="mono">{{ shortHash(evidence.hash) }}</span></span>
    <template v-if="evidence.kind === 'file'">
      <a :href="`/api/storage/${evidence.hash}`" target="_blank" rel="noopener">下载原文</a>
      <el-button link type="primary" :loading="checking" @click="verify">核验原文</el-button>
    </template>
    <span v-if="result" class="evidence-result" role="status">{{ result }}</span>
  </span>
</template>

<style scoped>
.evidence-badge { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 8px; min-width: 0; overflow-wrap: anywhere; }
.evidence-badge a { color: var(--ft-primary); text-decoration: underline; }
.evidence-result { flex-basis: 100%; }
</style>
