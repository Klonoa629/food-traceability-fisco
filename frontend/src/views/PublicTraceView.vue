<!-- 免登录公开溯源查询页:消费者按批次号查验 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { publicTrace } from '../api'
import { STAGE_COLORS, STAGE_NAMES, formatTime, shortAddr, shortHash } from '../constants/maps'
import type { ProductVO } from '../types'

const route = useRoute()

const batchNo = ref('')
const product = ref<ProductVO | null>(null)
const loading = ref(false)
const searched = ref(false)

const timeline = computed(() =>
  [...(product.value?.records ?? [])].sort((a, b) => a.timestamp - b.timestamp)
)

async function query() {
  const kw = batchNo.value.trim()
  if (!kw) {
    ElMessage.warning('请输入批次号')
    return
  }
  loading.value = true
  searched.value = true
  product.value = null
  try {
    product.value = await publicTrace(kw)
  } catch {
    // 404/400 提示由拦截器统一弹出
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const q = route.query.batchNo as string
  if (q) {
    batchNo.value = q
    query()
  }
})
</script>

<template>
  <div class="trace-page">
    <header class="tp-header">
      <router-link class="tp-brand" to="/trace">
        <span class="leaf">禾</span>
        <span>溯禾 · 溯源查询</span>
      </router-link>
      <router-link class="tp-login" to="/login">机构登录</router-link>
    </header>

    <main class="tp-main">
      <div class="tp-hero">
        <h2>食品溯源查询</h2>
        <p class="muted">输入产品批次号,查看链上完整流转记录 · 数据存证于 FISCO BCOS 联盟链,不可篡改</p>
        <div class="tp-search">
          <el-input
            v-model="batchNo"
            size="large"
            placeholder="请输入批次号,如 BS202932-S1"
            clearable
            @keyup.enter="query"
          />
          <el-button type="primary" size="large" :loading="loading" @click="query">查 询</el-button>
        </div>
      </div>

      <template v-if="product">
        <div class="tp-product ft-card" :class="{ 'is-recalled': product.recalled }">
          <div class="tp-badges">
            <span class="stage-badge" :style="{ background: STAGE_COLORS[product.stage] ?? '#888' }">
              {{ STAGE_NAMES[product.stage] ?? '未知' }}
            </span>
            <span v-if="product.recalled" class="recalled-tag">已召回</span>
          </div>
          <h3>{{ product.name }}</h3>
          <p class="tp-batch mono">批次号 {{ product.batchNo }}</p>
          <el-alert
            v-if="product.recalled"
            type="error"
            :closable="false"
            show-icon
            title="该产品已被监管召回,请勿购买或食用"
          />
        </div>

        <div class="tp-timeline ft-card">
          <h4>流转记录</h4>
          <div class="tl">
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
                  <span>操作方 <b class="mono">{{ shortAddr(r.operator) }}</b></span>
                  <span>地点 <b>{{ r.location || '-' }}</b></span>
                  <span v-if="r.dataHash" class="mono muted" :title="r.dataHash">链上存证 {{ shortHash(r.dataHash) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <el-empty v-else-if="searched && !loading" description="未找到该产品,请核对批次号" />
    </main>
  </div>
</template>

<style scoped>
.trace-page {
  min-height: 100vh;
  background:
    linear-gradient(rgba(247, 249, 244, 0.92), rgba(247, 249, 244, 0.97)),
    url('../assets/login-bg.jpg') center / cover no-repeat fixed,
    var(--ft-bg);
}
.tp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  height: 60px;
  background: rgba(255, 255, 255, 0.85);
  border-bottom: 1px solid var(--ft-border);
  backdrop-filter: blur(8px);
  position: sticky;
  top: 0;
  z-index: 10;
}
.tp-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 700;
  color: var(--ft-primary-deep);
  text-decoration: none;
}
.tp-brand .leaf {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--ft-primary), var(--ft-primary-deep));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
}
.tp-login { color: var(--ft-text-secondary); text-decoration: none; font-size: 14px; }
.tp-login:hover { color: var(--ft-primary-deep); }

.tp-main { max-width: 760px; margin: 0 auto; padding: 40px 24px 60px; }
.tp-hero { text-align: center; margin-bottom: 28px; }
.tp-hero h2 { margin: 0 0 8px; font-size: 26px; color: var(--ft-primary-deep); }
.tp-hero p { margin: 0 0 20px; font-size: 14px; }
.tp-search { display: flex; gap: 10px; max-width: 520px; margin: 0 auto; }
.tp-search .el-button { flex-shrink: 0; }

.tp-product {
  padding: 22px 26px;
  margin-bottom: 18px;
  border-left: 4px solid var(--ft-primary);
}
.tp-product.is-recalled { border-left-color: var(--ft-danger); }
.tp-badges { display: flex; gap: 8px; margin-bottom: 10px; }
.tp-product h3 { margin: 0 0 4px; font-size: 20px; }
.tp-batch { margin: 0 0 12px; font-size: 13px; color: var(--ft-text-secondary); }

.tp-timeline { padding: 22px 26px; }
.tp-timeline h4 { margin: 0 0 16px; font-size: 16px; }

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
.tl-stage { font-weight: 700; font-size: 15px; }
.tl-time { font-size: 13px; }
.tl-desc { margin: 8px 0 10px; font-size: 14px; }
.tl-meta { display: flex; flex-wrap: wrap; gap: 16px; font-size: 13px; color: var(--ft-text-secondary); }
.tl-meta b { color: var(--ft-text); font-weight: 600; }
</style>
