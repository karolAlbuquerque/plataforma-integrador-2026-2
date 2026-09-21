import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// A casca é servida na raiz do domínio (Contrato §12.8). Em desenvolvimento, `npm run dev` sobe
// na porta 3000 e manda /api e /modulos para o gateway — pare o container da casca antes.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const gateway = env.VITE_GATEWAY || 'http://localhost:8080'
  return {
    base: '/',
    plugins: [react(), tailwindcss()],
    server: {
      port: 3000,
      strictPort: true,
      proxy: {
        '/api': { target: gateway, changeOrigin: true },
        '/modulos': { target: gateway, changeOrigin: true },
      },
    },
  }
})
