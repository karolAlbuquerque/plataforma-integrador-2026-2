import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// A casca é servida na raiz do domínio (Contrato §12.8). Em desenvolvimento, `npm run dev` sobe
// na porta 3000 e manda /api e /modulos para o gateway — pare o container da casca antes.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const gateway = env.VITE_GATEWAY || 'http://localhost:8080'
  // Sem o Origin: para o gateway, a chamada vem da mesma origem, como em produção. Com ele,
  // http://localhost:3000 seria outra origem, recusada pelo CORS quando CORS_ORIGENS está ligado.
  const semOrigem: ProxyOptions = {
    target: gateway,
    changeOrigin: true,
    configure: (proxy) => proxy.on('proxyReq', (requisicao) => requisicao.removeHeader('origin')),
  }
  return {
    base: '/',
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 3000,
      strictPort: true,
      proxy: {
        '/api': semOrigem,
        '/modulos': semOrigem,
      },
    },
  }
})
