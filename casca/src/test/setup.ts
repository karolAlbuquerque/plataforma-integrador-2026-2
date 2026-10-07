import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

window.scrollTo = () => {}
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (consulta: string) => ({
    matches: false,
    media: consulta,
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

afterEach(() => {
  cleanup()
  window.localStorage.clear()
  window.history.replaceState(null, '', '/')
})
