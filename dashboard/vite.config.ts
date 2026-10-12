import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Mantine on its own is ~500 kB minified (~150 kB gzipped); everything else is split below.
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        // Big libraries in their own long-cached files; app updates then only re-download app code.
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('@supabase')) return 'supabase'
          if (id.includes('@tabler')) return 'icons'
          if (id.includes('@mantine') || id.includes('@floating-ui')) return 'mantine'
          if (/node_modules[/\\](react|react-dom|react-router|react-router-dom|scheduler)[/\\]/.test(id)) return 'react'
          return undefined
        },
      },
    },
  },
})
