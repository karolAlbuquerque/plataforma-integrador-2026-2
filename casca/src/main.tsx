import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './App'
import { aplicarPreferencia, preferenciaLocal } from './plataforma/tema'

// Antes do login vale a última preferência deste navegador; depois, a da conta (RF40)
aplicarPreferencia(preferenciaLocal())

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
