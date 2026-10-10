<!-- 监管控制台(深色):待审批、账户管理、审计日志、产品召回 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminApprove, adminListUsers, adminLogs, adminRecall, adminRevoke, adminVerifyChain, adminVerifyLogs,
  listProducts, listOrgs
} from '../../api'
import { ACTION_NAMES, ROLE_NAMES, STATUS_NAMES, STAGE_COLORS, STAGE_NAMES, formatDateTime, shortAddr, shortHash } from '../../constants/maps'
import type { OperateLog, ProductVO, UserInfo } from '../../types'
import { useAuthStore } from '../../stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

const tab = ref<'pending' | 'users' | 'logs' | 'recall'>('pending')

// 账户
const users = ref<UserInfo[]>([])
const usersLoading = ref(false)
const statusFilter = ref<number | undefined>(undefined)
const roleFilter = ref<number | undefined>(undefined)

const pendingUsers = computed(() => users.value.filter((u) => u.status === 0))
// 角色筛选在前端做,状态筛选走后端参数
const filteredUsers = computed(() =>
  roleFilter.value === undefined ? users.value : users.value.filter((u) => u.role === roleFilter.value)
)

async function loadUsers() {
  usersLoading.value = true
  try {
    users.value = await adminListUsers(statusFilter.value)
  } finally {
    usersLoading.value = false
  }
}

// 审批时可调整角色,上链发角色,耗时 1-3 秒
const approveDialog = ref(false)
const approveTarget = ref<UserInfo | null>(null)
const approveRole = ref(1)
const approveLoading = ref(false)

function openApprove(u: UserInfo) {
  approveTarget.value = u
  approveRole.value = u.role
  approveDialog.value = true
}

async function submitApprove() {
  if (!approveTarget.value) return
  approveLoading.value = true
  try {
    await adminApprove(approveTarget.value.id, approveRole.value)
    ElMessage.success(`已审批「${approveTarget.value.orgName}」,链上角色已发放`)
    approveDialog.value = false
    await loadUsers()
  } finally {
    approveLoading.value = false
  }
}

const revokingId = ref<number | null>(null)
async function revoke(u: UserInfo) {
  await ElMessageBox.confirm(
    `确认吊销「${u.orgName}(${u.username})」?链上角色将被收回,账户立即失效。`,
    '吊销账户',
    { type: 'error', confirmButtonText: '确认吊销' }
  )
  revokingId.value = u.id
  try {
    await adminRevoke(u.id)
    ElMessage.success('已吊销')
    await loadUsers()
  } finally {
    revokingId.value = null
  }
}

// 审计日志
const logs = ref<OperateLog[]>([])
const logsLoading = ref(false)
const logFilter = reactive({ action: '', userId: undefined as number | undefined })
// 分页状态
const logPage = ref(1)
const logSize = ref(20)
const logTotal = ref(0)

// 链上校验结果:记录 id -> 是否一致;false 即数据库疑似被篡改
const verifyMap = ref<Record<string, boolean>>({})
const verifyLoading = ref(false)
const tamperedCount = computed(
  () => Object.entries(verifyMap.value).filter(([, ok]) => !ok).length
)

async function loadLogs() {
  logsLoading.value = true
  try {
    const result = await adminLogs({
      action: logFilter.action || undefined,
      userId: logFilter.userId || undefined,
      page: logPage.value,
      size: logSize.value
    })
    logs.value = result.records
    logTotal.value = result.total
    await verifyLogs()
  } finally {
    logsLoading.value = false
  }
}

// 筛选条件变化时回到第一页
function resetLogPage() {
  logPage.value = 1
  loadLogs()
}

async function verifyLogs() {
  verifyLoading.value = true
  try {
    verifyMap.value = await adminVerifyLogs()
  } catch {
    verifyMap.value = {}
  } finally {
    verifyLoading.value = false
  }
}

async function verifyChain() {
  const result = await adminVerifyChain()
  if (result.intact) {
    ElMessage.success(`审计链完整，共 ${result.total} 条`)
  } else {
    ElMessage.error(`审计链不完整：${result.reason}`)
  }
}

function verifyState(row: OperateLog): 'none' | 'ok' | 'bad' | 'pending' {
  if (!row.chainTxHash) return 'none'
  const r = verifyMap.value[String(row.id)]
  if (r === undefined) return 'pending'
  return r ? 'ok' : 'bad'
}

function logRowClass({ row }: { row: OperateLog }) {
  return verifyState(row) === 'bad' ? 'row-tampered' : ''
}

// 产品召回
const products = ref<ProductVO[]>([])
const productsLoading = ref(false)
const orgs = ref<UserInfo[]>([])
const orgNameOf = computed(() => {
  const m = new Map<string, string>()
  for (const o of orgs.value) m.set(o.chainAddress?.toLowerCase(), o.orgName)
  return (addr?: string) => (addr ? m.get(addr.toLowerCase()) ?? shortAddr(addr) : '-')
})

async function loadProducts() {
  productsLoading.value = true
  try {
    // 召回对象为在途产品，取第一页大条数即可覆盖演示规模
    const [ps, os] = await Promise.all([listProducts({ page: 1, size: 100 }), listOrgs()])
    products.value = ps.records
    orgs.value = os
  } finally {
    productsLoading.value = false
  }
}

const recallDialog = ref(false)
const recallTarget = ref<ProductVO | null>(null)
const recallReason = ref('')
const recallLoading = ref(false)

function openRecall(p: ProductVO) {
  recallTarget.value = p
  recallReason.value = ''
  recallDialog.value = true
}

async function submitRecall() {
  if (!recallTarget.value) return
  if (!recallReason.value.trim()) {
    ElMessage.warning('请填写召回原因')
    return
  }
  recallLoading.value = true
  try {
    await adminRecall(recallTarget.value.id, recallReason.value.trim())
    ElMessage.success(`产品「${recallTarget.value.name}」已召回`)
    recallDialog.value = false
    await loadProducts()
  } finally {
    recallLoading.value = false
  }
}

function statusTagType(s: number) {
  return s === 1 ? 'success' : s === 2 ? 'danger' : 'warning'
}

onMounted(() => {
  loadUsers()
  loadLogs()
  loadProducts()
})

async function logout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-dark">
    <header class="ad-header">
      <div class="ad-brand">
        <span class="ad-logo">禾</span>
        <b>溯禾 · 食品溯源平台</b>
      </div>
      <router-link class="ad-link" to="/">产品工作台</router-link>
      <router-link class="ad-link" to="/admin">监管控制台</router-link>
      <div class="spacer" />
      <span class="ad-user mono">{{ auth.user?.orgName }}</span>
      <button class="ad-btn-ghost" @click="logout">退出</button>
    </header>

    <main class="ad-main">
      <div class="ad-tabs">
        <button :class="{ on: tab === 'pending' }" @click="tab = 'pending'">
          待审批 <span v-if="pendingUsers.length" class="ad-count">{{ pendingUsers.length }}</span>
        </button>
        <button :class="{ on: tab === 'users' }" @click="tab = 'users'">账户管理</button>
        <button :class="{ on: tab === 'logs' }" @click="tab = 'logs'">审计日志</button>
        <button :class="{ on: tab === 'recall' }" @click="tab = 'recall'">产品召回</button>
      </div>

      <section v-show="tab === 'pending'" class="ad-panel">
        <div class="ad-panel-head">
          <h3>待审批账户</h3>
          <button class="ad-btn-ghost" @click="loadUsers">刷新</button>
        </div>
        <el-table v-loading="usersLoading" :data="pendingUsers" empty-text="没有待审批的账户">
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="orgName" label="机构名称" min-width="160" />
          <el-table-column label="申请角色" width="110">
            <template #default="{ row }">{{ ROLE_NAMES[row.role] ?? row.role }}</template>
          </el-table-column>
          <el-table-column label="注册时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <button class="ad-btn" @click="openApprove(row)">审批</button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-show="tab === 'users'" class="ad-panel">
        <div class="ad-panel-head">
          <h3>账户管理</h3>
          <div class="ad-filters">
            <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="loadUsers">
              <el-option v-for="(name, s) in STATUS_NAMES" :key="s" :label="name" :value="Number(s)" />
            </el-select>
            <el-select v-model="roleFilter" placeholder="全部角色" clearable style="width: 130px">
              <el-option v-for="(name, r) in ROLE_NAMES" :key="r" :label="name" :value="Number(r)" />
            </el-select>
            <button class="ad-btn-ghost" @click="loadUsers">刷新</button>
          </div>
        </div>
        <el-table v-loading="usersLoading" :data="filteredUsers" empty-text="暂无账户">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" width="130" />
          <el-table-column prop="orgName" label="机构名称" min-width="150" />
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              {{ row.regulator ? '监管机构' : ROLE_NAMES[row.role] ?? row.role }}
            </template>
          </el-table-column>
          <el-table-column label="链上地址" min-width="140">
            <template #default="{ row }"><span class="mono ad-hash" :title="row.chainAddress">{{ shortAddr(row.chainAddress) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" effect="dark" size="small">{{ STATUS_NAMES[row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <button
                v-if="row.status === 1 && !row.regulator"
                class="ad-btn ad-btn-danger"
                :disabled="revokingId === row.id"
                @click="revoke(row)"
              >{{ revokingId === row.id ? '链上处理…' : '吊销' }}</button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-show="tab === 'logs'" class="ad-panel">
        <div class="ad-panel-head">
          <h3>操作审计</h3>
          <div class="ad-filters">
            <el-select v-model="logFilter.action" placeholder="全部动作" clearable style="width: 160px" @change="resetLogPage">
              <el-option v-for="(name, a) in ACTION_NAMES" :key="a" :label="`${name}(${a})`" :value="a" />
            </el-select>
            <el-input-number v-model="logFilter.userId" placeholder="操作人 ID" :min="1" controls-position="right" style="width: 130px" />
            <button class="ad-btn" @click="resetLogPage">查询</button>
            <button class="ad-btn-ghost" :disabled="verifyLoading" @click="verifyLogs">
              {{ verifyLoading ? '校验中…' : '链上校验' }}
            </button>
            <button class="ad-btn-ghost" @click="verifyChain">完整性校验</button>
          </div>
        </div>

        <el-alert
          v-if="tamperedCount > 0"
          type="error"
          :closable="false"
          show-icon
          class="tamper-alert"
          :title="`检测到 ${tamperedCount} 条审计记录与链上交易不符,数据库疑似被篡改!`"
          description="标红记录的交易哈希在链上不存在或不属于溯源合约,请立即核查数据库 operate_log 表。"
        />

        <el-table v-loading="logsLoading" :data="logs" empty-text="暂无日志" :row-class-name="logRowClass">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="userId" label="操作人ID" width="90" />
          <el-table-column prop="username" label="操作人" width="110" />
          <el-table-column label="动作" width="150">
            <template #default="{ row }">
              <span class="mono ad-action">{{ ACTION_NAMES[row.action] ?? row.action }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
          <el-table-column label="链上交易" width="150">
            <template #default="{ row }">
              <span v-if="row.chainTxHash" class="mono ad-hash" :title="row.chainTxHash">{{ shortHash(row.chainTxHash) }}</span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="链上校验" width="100">
            <template #default="{ row }">
              <span v-if="verifyState(row) === 'ok'" class="verify-ok">✓ 一致</span>
              <span v-else-if="verifyState(row) === 'bad'" class="verify-bad">✗ 篡改嫌疑</span>
              <span v-else-if="verifyState(row) === 'pending'" class="muted">校验中…</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="log-pager"
          background
          layout="total, sizes, prev, pager, next"
          :total="logTotal"
          :page-sizes="[10, 20, 50, 100]"
          v-model:current-page="logPage"
          v-model:page-size="logSize"
          @current-change="loadLogs"
          @size-change="resetLogPage"
        />
      </section>

      <section v-show="tab === 'recall'" class="ad-panel">
        <div class="ad-panel-head">
          <h3>产品召回</h3>
          <button class="ad-btn-ghost" @click="loadProducts">刷新</button>
        </div>
        <el-table v-loading="productsLoading" :data="products" empty-text="暂无产品">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="产品" min-width="140" />
          <el-table-column prop="batchNo" label="批次号" min-width="140">
            <template #default="{ row }"><span class="mono">{{ row.batchNo }}</span></template>
          </el-table-column>
          <el-table-column label="当前环节" width="110">
            <template #default="{ row }">
              <span class="ad-stage" :style="{ color: STAGE_COLORS[row.stage] ?? '#888' }">{{ STAGE_NAMES[row.stage] ?? '未知' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="当前持有" min-width="130">
            <template #default="{ row }">{{ orgNameOf(row.currentHolder) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.recalled" type="danger" effect="dark" size="small">已召回</el-tag>
              <el-tag v-else type="success" effect="dark" size="small">流通中</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <button v-if="!row.recalled" class="ad-btn ad-btn-danger" @click="openRecall(row)">召回</button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </main>

    <el-dialog v-model="approveDialog" title="审批账户(上链发放角色)" width="440px" class="dark-dialog" :close-on-click-modal="false">
      <template v-if="approveTarget">
        <p class="dd-line">机构:<b>{{ approveTarget.orgName }}</b>({{ approveTarget.username }})</p>
        <p class="dd-line muted">审批通过后账户生效,链上自动发放对应角色,耗时约 1-3 秒。</p>
        <el-select v-model="approveRole" style="width: 100%" size="large">
          <el-option v-for="(name, r) in ROLE_NAMES" :key="r" :label="name" :value="Number(r)" />
        </el-select>
      </template>
      <template #footer>
        <el-button @click="approveDialog = false" :disabled="approveLoading">取消</el-button>
        <el-button type="primary" :loading="approveLoading" @click="submitApprove">
          {{ approveLoading ? '上链中…' : '审批通过' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recallDialog" title="召回产品(上链)" width="440px" class="dark-dialog" :close-on-click-modal="false">
      <template v-if="recallTarget">
        <p class="dd-line">产品:<b>{{ recallTarget.name }}</b><span class="mono muted">({{ recallTarget.batchNo }})</span></p>
        <p class="dd-line muted">召回是终态操作:产品立即冻结,链上拒绝一切后续写操作,不可逆。</p>
        <el-input v-model="recallReason" type="textarea" :rows="3" placeholder="召回原因(必填)" />
      </template>
      <template #footer>
        <el-button @click="recallDialog = false" :disabled="recallLoading">取消</el-button>
        <el-button type="danger" :loading="recallLoading" @click="submitRecall">
          {{ recallLoading ? '上链中…' : '确认召回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style>
/* 监管控制台深色主题(仅本页与 dark-dialog) */
.admin-dark {
  --ad-bg: #0e1116;
  --ad-panel: #151a21;
  --ad-border: #242c36;
  --ad-accent: #6effa0;
  --ad-text: #e6edf3;
  --ad-muted: #8b98a5;

  min-height: 100vh;
  background: var(--ad-bg);
  color: var(--ad-text);

  --el-bg-color: var(--ad-panel);
  --el-bg-color-overlay: #1a212a;
  --el-text-color-primary: var(--ad-text);
  --el-text-color-regular: #c3cdd8;
  --el-text-color-secondary: var(--ad-muted);
  --el-border-color: var(--ad-border);
  --el-border-color-light: var(--ad-border);
  --el-border-color-lighter: var(--ad-border);
  --el-fill-color-blank: #11161c;
  --el-fill-color-light: #1a212a;
  --el-fill-color: #1a212a;
  --el-color-primary: #3ddc84;
  --el-color-primary-light-3: #2b5c43;
  --el-color-primary-light-5: #24493a;
  --el-color-primary-light-7: #1d3a2f;
  --el-color-primary-light-8: #182e27;
  --el-color-primary-light-9: #14251f;
  --el-color-primary-dark-2: #6effa0;
  --el-mask-color: rgba(0, 0, 0, 0.6);
}

.admin-dark .ad-header {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 28px;
  height: 60px;
  border-bottom: 1px solid var(--ad-border);
  background: rgba(14, 17, 22, 0.9);
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(8px);
}
.admin-dark .ad-brand { display: flex; align-items: center; gap: 10px; }
.admin-dark .ad-logo {
  width: 28px; height: 28px; border-radius: 8px;
  background: linear-gradient(135deg, #3ddc84, #1e7a4c);
  color: #062012;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px;
  font-weight: 700;
}
.admin-dark .ad-brand b { font-size: 17px; letter-spacing: 0.5px; }
.admin-dark .spacer { flex: 1; }
.admin-dark .ad-user { font-size: 14px; color: var(--ad-accent); }
.admin-dark .log-pager { margin-top: 12px; justify-content: flex-end; }
.admin-dark .ad-link { color: var(--ad-muted); text-decoration: none; font-size: 15px; padding: 5px 12px; border-radius: 6px; }
.admin-dark .ad-link:hover { color: var(--ad-accent); }
.admin-dark .ad-link.router-link-active { color: var(--ad-accent); background: rgba(110, 255, 160, 0.10); font-weight: 600; }

.admin-dark .ad-btn,
.admin-dark .ad-btn-ghost,
.admin-dark .ad-btn-danger {
  border-radius: 6px;
  font-size: 14px;
  padding: 6px 14px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
}
.admin-dark .ad-btn {
  background: var(--ad-accent);
  color: #062012;
  font-weight: 600;
}
.admin-dark .ad-btn:hover { box-shadow: 0 0 14px rgba(110, 255, 160, 0.4); }
.admin-dark .ad-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.admin-dark .ad-btn-ghost {
  background: transparent;
  color: var(--ad-muted);
  border-color: var(--ad-border);
}
.admin-dark .ad-btn-ghost:hover { color: var(--ad-accent); border-color: var(--ad-accent); }
.admin-dark .ad-btn-danger {
  background: transparent;
  color: #ff7b86;
  border-color: #5a2b30;
}
.admin-dark .ad-btn-danger:hover { background: rgba(214, 69, 80, 0.15); }

.admin-dark .ad-main { max-width: 1440px; margin: 0 auto; padding: 24px 28px 48px; }

.admin-dark .ad-tabs { display: flex; gap: 8px; margin-bottom: 20px; }
.admin-dark .ad-tabs button {
  background: transparent;
  border: 1px solid var(--ad-border);
  color: var(--ad-muted);
  border-radius: 8px;
  padding: 8px 18px;
  font-size: 15px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.admin-dark .ad-tabs button.on {
  color: var(--ad-accent);
  border-color: var(--ad-accent);
  background: rgba(110, 255, 160, 0.08);
}
.admin-dark .ad-count {
  background: var(--ad-accent);
  color: #062012;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  padding: 1px 7px;
}

.admin-dark .ad-panel {
  background: var(--ad-panel);
  border: 1px solid var(--ad-border);
  border-radius: 12px;
  padding: 20px 22px;
}
.admin-dark .ad-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}
.admin-dark .ad-panel-head h3 { margin: 0; font-size: 16px; }
.admin-dark .ad-filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.admin-dark .ad-hash { color: var(--ad-accent); font-size: 13px; }
.admin-dark .ad-action { color: #7cc7ff; font-size: 13px; }
.admin-dark .ad-stage { font-weight: 600; }
.admin-dark .muted { color: var(--ad-muted); }

.admin-dark .el-table { --el-table-header-bg-color: #11161c; --el-table-tr-bg-color: transparent; --el-table-row-hover-bg-color: #1a212a; font-size: 15px; }
.admin-dark .el-table .cell { text-align: center; }
.admin-dark .el-table .cell, .admin-dark .ad-hash { white-space: nowrap; }
.admin-dark .el-table .row-tampered { --el-table-tr-bg-color: rgba(214, 69, 80, 0.14); }
.admin-dark .tamper-alert { margin-bottom: 14px; }
.admin-dark .verify-ok { color: var(--ad-accent); font-size: 13px; font-weight: 600; }
.admin-dark .verify-bad { color: #ff7b86; font-size: 13px; font-weight: 700; }

.dark-dialog {
  --el-bg-color: #151a21;
  --el-text-color-primary: #e6edf3;
  --el-text-color-regular: #c3cdd8;
  --el-border-color: #242c36;
  --el-border-color-lighter: #242c36;
  --el-fill-color-blank: #11161c;
  border: 1px solid #242c36;
  border-radius: 12px;
}
.dark-dialog .el-dialog__title { color: #e6edf3; }
.dark-dialog .dd-line { margin: 0 0 12px; font-size: 14px; }
.dark-dialog .dd-line.muted { color: #8b98a5; font-size: 13px; }
</style>
