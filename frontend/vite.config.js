import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxying /api keeps the browser same-origin in dev, so there is no CORS
// preflight and tokens are sent as plain headers. Set VITE_API_TARGET when the
// backend is not on 8080 -- Oracle XE claims that port on some machines.
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        // The map and the charts are each only used on one screen, so keep
        // them out of the bundle every page has to download.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
          map: ['leaflet', 'react-leaflet'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
});
