<!-- 产品工作台:产品卡片列表 + 区块高度 + 注册产品(仅基地) -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import AppShell from '../components/AppShell.vue'
import { chainPing, createProduct, listOrgs, listProducts } from '../api'
import { useAuthStore } from '../stores/auth'
import { STAGE_COLORS, STAGE_NAMES, shortAddr } from '../constants/maps'
import type { ProductVO, UserInfo } from '../types'

const auth = useAuthStore()
const router = useRouter()

const products = ref<ProductVO[]>([])
const orgs = ref<UserInfo[]>([])
const loading = ref(false)
// 分页状态
const page = ref(1)
const size = ref(20)
const total = ref(0)

// 链上地址 -> 机构名
const orgNameOf = computed(() => {
  const m = new Map<string, string>()
  for (const o of orgs.value) m.set(o.chainAddress?.toLowerCase(), o.orgName)
  return (addr?: string) => (addr ? m.get(addr.toLowerCase()) ?? shortAddr(addr) : '-')
})

// 区块高度小部件,15 秒轮询
const blockHeight = ref<string>('-')
let timer: ReturnType<typeof setInterval> | null = null

async function refreshHeight() {
  try {
    const data = await chainPing()
    blockHeight.value = String(data?.blockNumber ?? '-')
  } catch {
    blockHeight.value = '连接中…'
  }
}

let loadVersion = 0
async function load() {
  const version = ++loadVersion
  loading.value = true
  try {
    const [ps, os] = await Promise.all([
      listProducts({ page: page.value, size: size.value, ...filters }),
      listOrgs()
    ])
    if (version !== loadVersion) return
    products.value = ps.records
    total.value = ps.total
    orgs.value = os
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

// 每页条数变化时回到第一页
function resetPage() {
  page.value = 1
  load()
}

onMounted(() => {
  load()
  refreshHeight()
  timer = setInterval(refreshHeight, 15000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })

// 筛选由后端完成，分页总数为全部匹配产品数。
const keyword = ref('')
const filters = reactive<{ keyword?: string; stage?: number; holder?: string; recalled?: boolean }>({})
function search() {
  filters.keyword = keyword.value.trim() || undefined
  resetPage()
}
function clearFilters() {
  keyword.value = ''
  filters.keyword = undefined
  filters.stage = undefined
  filters.holder = undefined
  filters.recalled = undefined
  resetPage()
}
// 注册产品
const isFarm = computed(() => auth.user?.role === 1)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', batchNo: '', description: '', location: '', dataHash: '' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号(全局唯一)', trigger: 'blur' }],
  location: [{ required: true, message: '请输入产地', trigger: 'blur' }],
  description: [{ required: true, whitespace: true, message: '请输入产品描述', trigger: 'blur' }]
}
const submitting = ref(false)

async function submitCreate() {
  if (!await formRef.value?.validate().catch(() => false)) return
  submitting.value = true // 上链写操作 1-3 秒,防重复提交
  try {
    const p = await createProduct({
      name: form.name.trim(),
      batchNo: form.batchNo.trim(),
      description: form.description.trim(),
      location: form.location.trim(),
      dataHash: form.dataHash.trim() || null
    })
    ElMessage.success(`产品「${p.name}」已注册上链`)
    dialogVisible.value = false
    Object.assign(form, { name: '', batchNo: '', description: '', location: '', dataHash: '' })
    // 新产品排在最前，回到第一页可见
    keyword.value = ''
    Object.assign(filters, { keyword: undefined, stage: undefined, holder: undefined, recalled: undefined })
    page.value = 1
    await load()
  } finally {
    submitting.value = false
  }
}

function openDetail(p: ProductVO) {
  router.push({ name: 'product-detail', params: { id: p.id } })
}
</script>

<template>
  <AppShell>
    <template #header-extra>
      <div class="chain-widget mono" title="FISCO BCOS 当前区块高度">
        <span class="dot" /> 区块高度 {{ blockHeight }}
      </div>
    </template>

    <div class="page-head">
      <div>
        <h2>产品工作台</h2>
      </div>
      <div class="page-head-right">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索全部产品：名称 / 批次号"
          @keyup.enter="search"
          @clear="search"
          clearable
          size="large"
        />
        <el-button @click="search">搜索</el-button>
        <el-button v-if="isFarm" type="primary" size="large" @click="dialogVisible = true">
          + 注册产品
        </el-button>
      </div>
    </div>

    <div class="filters">
      <el-select v-model="filters.stage" placeholder="全部环节" aria-label="环节筛选" clearable @change="search">
        <el-option v-for="(name, stage) in STAGE_NAMES" :key="stage" :label="name" :value="Number(stage)" />
      </el-select>
      <el-select v-model="filters.holder" placeholder="全部持有机构" aria-label="持有机构筛选" clearable @change="search">
        <el-option v-for="org in orgs.filter(o => o.chainAddress)" :key="org.id" :label="org.orgName" :value="org.chainAddress" />
      </el-select>
      <el-select v-model="filters.recalled" placeholder="全部召回状态" aria-label="召回状态筛选" clearable @change="search">
        <el-option label="未召回" :value="false" />
        <el-option label="已召回" :value="true" />
      </el-select>
      <el-button @click="clearFilters">重置筛选</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty v-else-if="!products.length" :description="keyword ? '没有匹配的产品' : '暂无产品'" />

    <div v-else class="product-grid">
      <div
        v-for="p in products"
        :key="p.id"
        class="product-card ft-card"
        :class="{ recalled: p.recalled }"
        @click="openDetail(p)"
      >
        <div class="pc-head">
          <span class="stage-badge" :style="{ background: STAGE_COLORS[p.stage] ?? '#888' }">
            {{ STAGE_NAMES[p.stage] ?? '未知' }}
          </span>
          <span v-if="p.recalled" class="recalled-tag">已召回</span>
        </div>
        <h3>{{ p.name }}</h3>
        <p class="batch mono">批次 {{ p.batchNo }}</p>
        <div class="pc-meta">
          <div class="pc-row"><span class="muted">当前持有</span><b>{{ orgNameOf(p.currentHolder) }}</b></div>
          <div class="pc-row"><span class="muted">溯源记录</span><b>{{ p.records?.length ?? 0 }} 条</b></div>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
      class="product-pager"
      background
      layout="total, prev, pager, next"
      :pager-count="5"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      v-model:current-page="page"
      v-model:page-size="size"
      @current-change="load"
      @size-change="resetPage"
    />

    <el-dialog v-model="dialogVisible" title="注册产品(上链)" width="min(480px, 94vw)" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="产品名称" prop="name">
          <el-input v-model="form.name" placeholder="如:阳光草莓" />
        </el-form-item>
        <el-form-item label="批次号(全局唯一)" prop="batchNo">
          <el-input v-model="form.batchNo" placeholder="如:B20260922-A1" />
        </el-form-item>
        <el-form-item label="产地" prop="location">
          <el-input v-model="form.location" placeholder="如:云南昆明" />
        </el-form-item>
        <el-form-item label="产品描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="描述种植或生产情况" />
        </el-form-item>
        <el-form-item label="数据哈希(选填)">
          <el-input v-model="form.dataHash" placeholder="留空则由后端自动计算 SHA-256 存证" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">
          {{ submitting ? '上链中…' : '确认注册' }}
        </el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<style scoped>
.chain-widget {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--ft-primary-deep);
  background: var(--ft-primary-soft);
  border: 1px solid var(--ft-border);
  padding: 5px 12px;
  border-radius: 999px;
}
.chain-widget .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ft-primary);
  box-shadow: 0 0 0 3px rgba(47, 158, 99, 0.2);
}
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-head h2 { margin: 0 0 4px; font-size: 22px; }
.page-head p { margin: 0; font-size: 14px; }
.page-head-right { display: flex; gap: 12px; align-items: center; }
.search-input { width: 260px; }
.product-pager { margin-top: 20px; justify-content: flex-end; }
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}
.product-card {
  padding: 26px 30px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(30, 122, 76, 0.12);
}
.product-card.recalled { opacity: 0.75; border-color: #eccdd0; }
.pc-head { display: flex; gap: 8px; margin-bottom: 14px; }
.product-card h3 { margin: 0 0 6px; font-size: 21px; }
.batch { margin: 0 0 16px; font-size: 14px; color: var(--ft-text-secondary); }
.pc-meta { display: flex; flex-direction: column; gap: 8px; font-size: 15px; }
.pc-row { display: flex; justify-content: space-between; align-items: baseline; }
.pc-row .muted { font-size: 14px; }
.pc-row b { font-weight: 600; }
.filters { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.filters .el-select { width: 180px; }
@media (max-width: 600px) {
  .page-head { align-items: stretch; flex-direction: column; gap: 12px; }
  .page-head-right { flex-wrap: wrap; }
  .search-input { width: 100%; }
  .filters .el-select { width: 100%; }
  .product-grid { grid-template-columns: minmax(0, 1fr); }
  .product-pager { justify-content: center; flex-wrap: wrap; }
}
</style>
