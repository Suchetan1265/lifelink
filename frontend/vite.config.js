import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxying /api keeps the browser same-origin in dev, so there is no CORS
// preflight and tokens are sent as plain headers.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
