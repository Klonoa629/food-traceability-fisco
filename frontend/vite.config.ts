// Vite 配置:/api 由开发服务器代理到 WSL 后端(后端未配 CORS,浏览器不直连 8081)
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8081'
    }
  },
  build: {
    rollupOptions: {
      output: {
        // 拆分大依赖,生产环境并行加载更快
        manualChunks: {
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          vendor: ['vue', 'vue-router', 'pinia', 'axios']
        }
      }
    }
  }
})
