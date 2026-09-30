import path from "node:path"
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    host: true,
    port: 5173,
    // Bind-mounted source edited from the Windows host don't reliably raise inotify events inside
    // the Linux container (Docker Desktop's filesystem bridge), so Vite's default watcher can miss
    // them entirely - polling guarantees changes are picked up without needing a container restart.
    watch: {
      usePolling: true,
      interval: 300,
    },
  },
})
