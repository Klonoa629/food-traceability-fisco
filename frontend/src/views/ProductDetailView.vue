<!-- 产品详情页:基本信息 + 溯源时间线 + 责任方操作区 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import AppShell from '../components/AppShell.vue'
import { addRecord, getProduct, handover, inspect, listOrgs } from '../api'
import { useAuthStore } from '../stores/auth'
import {
  ROLE_NAMES, ROLE_STAGE, STAGE_COLORS, STAGE_NAMES,
  formatTime, shortAddr, shortHash
} from '../constants/maps'
import type { ProductVO, UserInfo } from '../types'

const route = useRoute()
const auth = useAuthStore()
const id = Number(route.params.id)

const product = ref<ProductVO | null>(null)
const orgs = ref<UserInfo[]>([])
const loading = ref(true)

const orgNameOf = computed(() => {
  const m = new Map<string, string>()
  for (const o of orgs.value) m.set(o.chainAddress?.toLowerCase(), o.orgName)
  return (addr?: string) => (addr ? m.get(addr.toLowerCase()) ?? shortAddr(addr) : '-')
})

// 记录按时间正序
const timeline = computed(() =>
  [...(product.value?.records ?? [])].sort((a, b) => a.timestamp - b.timestamp)
)

// 操作区仅当前链上责任方可见,召回终态禁止写操作
const isHolder = computed(() => product.value && auth.isHolder(product.value.currentHolder))
const canOperate = computed(() => !!isHolder.value && !product.value?.recalled && !auth.isRegulator)

const myStage = computed(() => (auth.user ? ROLE_STAGE[auth.user.role] : undefined))
const canWriteRecord = computed(() => canOperate.value && myStage.value !== undefined)
const canInspect = computed(() => canOperate.value && auth.user?.role === 3)

async function load() {
  loading.value = true
  try {
    const [p, os] = await Promise.all([getProduct(id), listOrgs()])
    product.value = p
    orgs.value = os
  } finally {
    loading.value = false
  }
}
onMounted(load)

// 环节记录
const recordDialog = ref(false)
const recordFormRef = ref<FormInstance>()
const recordForm = reactive({ description: '', location: '', dataHash: '' })
const recordRules: FormRules = {
  description: [{ required: true, message: '请输入记录内容', trigger: 'blur' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }]
}
const recordSubmitting = ref(false)

async function submitRecord() {
  await recordFormRef.value?.validate()
  recordSubmitting.value = true
  try {
    product.value = await addRecord(id, {
      stage: myStage.value!,
      description: recordForm.description.trim(),
      location: recordForm.location.trim(),
      dataHash: recordForm.dataHash.trim() || null
    })
    ElMessage.success('记录已上链')
    recordDialog.value = false
    Object.assign(recordForm, { description: '', location: '', dataHash: '' })
  } finally {
    recordSubmitting.value = false
  }
}

// 交接
const handoverDialog = ref(false)
const nextHolder = ref('')
const handoverSubmitting = ref(false)
const handoverTargets = computed(() =>
  orgs.value.filter((o) => o.chainAddress?.toLowerCase() !== auth.user?.chainAddress?.toLowerCase())
)

async function submitHandover() {
  if (!nextHolder.value) {
    ElMessage.warning('请选择下游机构')
    return
  }
  const target = handoverTargets.value.find((o) => o.chainAddress === nextHolder.value)
  await ElMessageBox.confirm(
    `确认将产品交接给「${target?.orgName ?? shortAddr(nextHolder.value)}」?交接后责任方立即变更,不可撤销。`,
    '交接确认',
    { type: 'warning' }
  )
  handoverSubmitting.value = true
  try {
    product.value = await handover(id, nextHolder.value)
    ElMessage.success('交接完成,已上链')
    handoverDialog.value = false
    nextHolder.value = ''
  } finally {
    handoverSubmitting.value = false
  }
}

// 质检裁决
const inspectDialog = ref(false)
const inspectForm = reactive({ qualified: true, reportHash: '' })
const inspectSubmitting = ref(false)

async function submitInspect() {
  if (!inspectForm.qualified) {
    await ElMessageBox.confirm(
      '判定不合格将直接把产品置为召回态,不可逆。确认?',
      '质检裁决',
      { type: 'error', confirmButtonText: '确认不合格' }
    )
  }
  inspectSubmitting.value = true
  try {
    product.value = await inspect(id, {
      qualified: inspectForm.qualified,
      reportHash: inspectForm.reportHash.trim() || null
    })
    ElMessage.success(inspectForm.qualified ? '质检合格,已上链' : '质检不合格,产品已召回')
    inspectDialog.value = false
    inspectForm.reportHash = ''
  } finally {
    inspectSubmitting.value = false
  }
}
</script>

<template>
  <AppShell>
    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="product">
      <div class="detail-head ft-card" :class="{ 'is-recalled': product.recalled }">
        <div class="dh-left">
          <div class="dh-badges">
            <span class="stage-badge" :style="{ background: STAGE_COLORS[product.stage] ?? '#888' }">
              当前环节 · {{ STAGE_NAMES[product.stage] ?? '未知' }}
            </span>
            <span v-if="product.recalled" class="recalled-tag">已召回</span>
          </div>
          <h2>{{ product.name }}</h2>
          <p class="batch mono">批次号 {{ product.batchNo }}</p>
          <p v-if="product.description" class="desc">{{ product.description }}</p>
        </div>
        <div class="dh-right">
          <div class="meta-row"><span class="muted">源头基地</span><b>{{ orgNameOf(product.originFarm) }}</b></div>
          <div class="meta-row"><span class="muted">当前持有</span><b>{{ orgNameOf(product.currentHolder) }}</b></div>
          <div class="meta-row"><span class="muted">持有地址</span><span class="mono">{{ shortAddr(product.currentHolder) }}</span></div>
          <div class="meta-row"><span class="muted">溯源记录</span><b>{{ product.records?.length ?? 0 }} 条</b></div>
        </div>
      </div>

      <div class="detail-body">
        <section class="timeline-wrap ft-card">
          <h3 class="section-title">溯源时间线</h3>
          <el-empty v-if="!timeline.length" description="暂无记录" :image-size="80" />
          <div v-else class="tl">
            <div v-for="(r, i) in timeline" :key="i" class="tl-item" :class="{ 'is-recall': r.stage === 6 }">
              <div class="tl-rail">
                <span class="tl-node" :style="{ background: STAGE_COLORS[r.stage] ?? '#888' }">{{ i + 1 }}</span>
                <span v-if="i < timeline.length - 1" class="tl-line" :style="{ background: STAGE_COLORS[r.stage] ?? '#888' }" />
              </div>
              <div class="tl-card">
                <div class="tl-head">
                  <span class="tl-stage" :style="{ color: STAGE_COLORS[r.stage] ?? '#888' }">{{ STAGE_NAMES[r.stage] ?? '未知环节' }}</span>
                  <span class="tl-time muted">{{ formatTime(r.timestamp) }}</span>
                </div>
                <p class="tl-desc">{{ r.description }}</p>
                <div class="tl-meta">
                  <span>操作方 <b>{{ orgNameOf(r.operator) }}</b></span>
                  <span>地点 <b>{{ r.location || '-' }}</b></span>
                  <span v-if="r.dataHash" class="mono muted" :title="r.dataHash">存证 {{ shortHash(r.dataHash) }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <aside class="action-panel ft-card">
          <h3 class="section-title">操作区</h3>
          <template v-if="product.recalled">
            <el-alert type="error" :closable="false" title="产品已召回" description="召回是终态,链上拒绝一切后续写操作。" />
          </template>
          <template v-else-if="canOperate">
            <p class="muted action-hint">您是当前责任方({{ ROLE_NAMES[auth.user?.role ?? 0] }}),可执行:</p>
            <el-button v-if="canWriteRecord" type="primary" class="action-btn" @click="recordDialog = true">
              {{ auth.user?.role === 1 ? '补记种植记录' : `记录${STAGE_NAMES[myStage!]}环节` }}
            </el-button>
            <el-button v-if="canInspect" type="primary" class="action-btn" @click="inspectDialog = true">
              质检裁决
            </el-button>
            <el-button class="action-btn" @click="handoverDialog = true">交接给下游机构</el-button>
          </template>
          <template v-else>
            <p class="muted action-hint">您不是当前责任方,仅可查看溯源信息。</p>
          </template>
        </aside>
      </div>

      <el-dialog v-model="recordDialog" :title="`记录${STAGE_NAMES[myStage ?? 0]}环节(上链)`" width="480px" :close-on-click-modal="false">
        <el-form ref="recordFormRef" :model="recordForm" :rules="recordRules" label-position="top">
          <el-form-item label="记录内容" prop="description">
            <el-input v-model="recordForm.description" type="textarea" :rows="3" placeholder="如:首批采摘完成,冷链待发" />
          </el-form-item>
          <el-form-item label="地点" prop="location">
            <el-input v-model="recordForm.location" placeholder="如:云南昆明" />
          </el-form-item>
          <el-form-item label="数据哈希(选填)">
            <el-input v-model="recordForm.dataHash" placeholder="留空则由后端自动计算 SHA-256 存证" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="recordDialog = false" :disabled="recordSubmitting">取消</el-button>
          <el-button type="primary" :loading="recordSubmitting" @click="submitRecord">
            {{ recordSubmitting ? '上链中…' : '确认上链' }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="handoverDialog" title="交接给下游机构(上链)" width="480px" :close-on-click-modal="false">
        <el-select v-model="nextHolder" placeholder="选择下游机构" style="width: 100%" size="large">
          <el-option v-for="o in handoverTargets" :key="o.chainAddress" :value="o.chainAddress"
            :label="`${o.orgName}(${ROLE_NAMES[o.role] ?? ''})`">
            <div style="display: flex; justify-content: space-between; gap: 12px">
              <span>{{ o.orgName }}({{ ROLE_NAMES[o.role] ?? '' }})</span>
              <span class="mono muted">{{ shortAddr(o.chainAddress) }}</span>
            </div>
          </el-option>
        </el-select>
        <template #footer>
          <el-button @click="handoverDialog = false" :disabled="handoverSubmitting">取消</el-button>
          <el-button type="primary" :loading="handoverSubmitting" @click="submitHandover">
            {{ handoverSubmitting ? '上链中…' : '确认交接' }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="inspectDialog" title="质检裁决(上链)" width="480px" :close-on-click-modal="false">
        <el-form label-position="top">
          <el-form-item label="质检结论">
            <el-radio-group v-model="inspectForm.qualified">
              <el-radio :value="true">合格</el-radio>
              <el-radio :value="false">不合格(将直接召回)</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="检测报告哈希(选填)">
            <el-input v-model="inspectForm.reportHash" placeholder="留空则由后端自动计算 SHA-256 存证" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="inspectDialog = false" :disabled="inspectSubmitting">取消</el-button>
          <el-button :type="inspectForm.qualified ? 'primary' : 'danger'" :loading="inspectSubmitting" @click="submitInspect">
            {{ inspectSubmitting ? '上链中…' : '提交裁决' }}
          </el-button>
        </template>
      </el-dialog>
    </template>
  </AppShell>
</template>

<style scoped>
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  margin-bottom: 20px;
  border-left: 4px solid var(--ft-primary);
}
.detail-head.is-recalled { border-left-color: var(--ft-danger); }
.dh-badges { display: flex; gap: 8px; margin-bottom: 10px; }
.dh-left h2 { margin: 0 0 4px; font-size: 24px; }
.batch { margin: 0; font-size: 14px; color: var(--ft-text-secondary); }
.desc { margin: 10px 0 0; color: var(--ft-text-secondary); font-size: 15px; }
.dh-right { min-width: 260px; display: flex; flex-direction: column; gap: 8px; justify-content: center; }
.meta-row { display: flex; justify-content: space-between; font-size: 14px; gap: 16px; }

.detail-body {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 20px;
  align-items: start;
}
@media (max-width: 900px) {
  .detail-body { grid-template-columns: 1fr; }
}

.section-title { margin: 0 0 16px; font-size: 16px; }
.timeline-wrap { padding: 24px 28px; }

.tl { display: flex; flex-direction: column; }
.tl-item { display: flex; gap: 16px; }
.tl-rail { display: flex; flex-direction: column; align-items: center; width: 30px; flex-shrink: 0; }
.tl-node {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 0 0 4px rgba(47, 158, 99, 0.10);
}
.tl-line { width: 2px; flex: 1; min-height: 18px; opacity: 0.45; margin: 4px 0; }
.tl-card {
  flex: 1;
  border: 1px solid var(--ft-border);
  border-radius: 10px;
  padding: 14px 18px;
  margin-bottom: 16px;
  background: #fcfdfb;
}
.tl-item.is-recall .tl-card { background: #fdf3f4; border-color: #eccdd0; }
.tl-head { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
.tl-stage { font-weight: 700; font-size: 16px; }
.tl-time { font-size: 13px; }
.tl-desc { margin: 8px 0 10px; font-size: 15px; }
.tl-meta { display: flex; flex-wrap: wrap; gap: 16px; font-size: 13px; color: var(--ft-text-secondary); }
.tl-meta b { color: var(--ft-text); font-weight: 600; }

.action-panel { padding: 20px 22px; position: sticky; top: 80px; }
.action-hint { font-size: 14px; margin: 0 0 14px; }
.action-btn { width: 100%; margin: 0 0 10px !important; }
</style>
