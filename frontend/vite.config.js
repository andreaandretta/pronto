import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: './',  // Path relativi per Android WebView
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    // Genera asset con path relativi
    rollupOptions: {
      output: {
        // Nomi file più stabili per evitare problemi di cache
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]'
      }
    }
  },
  server: {
    host: true,
    allowedHosts: ['.trycloudflare.com', '.github.dev', 'localhost']
  }
})
