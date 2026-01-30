import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: './',  // Path relativi per Android WebView
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    // Target ES2015 per compatibilità WebView Android
    target: 'es2015',
    // Minificazione controllata
    minify: 'esbuild',
    esbuildOptions: {
      keepNames: true,
    },
    rollupOptions: {
      output: {
        // Nomi file più stabili
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          const info = assetInfo.name.split('.')
          const ext = info[info.length - 1]
          return `assets/[name]-[hash][extname]`
        }
      }
    },
    // CRITICO: Non aggiungere crossorigin agli script per compatibilità WebView
    modulePreload: {
      polyfill: false
    },
    // Disabilita crossorigin per assets
    cssCodeSplit: true,
  },
  // Rimuove crossorigin dagli script generati
  experimental: {
    renderBuiltUrl(filename, { hostType }) {
      return { relative: true }
    }
  },
  server: {
    host: true,
    allowedHosts: ['.trycloudflare.com', '.github.dev', 'localhost']
  }
})
