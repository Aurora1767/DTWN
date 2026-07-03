import { fileURLToPath, URL } from 'node:url'
import { createReadStream, cpSync, existsSync, statSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'
import type { IncomingMessage, ServerResponse } from 'node:http'

import { defineConfig } from 'vite'
import type { Plugin, ViteDevServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueDevTools from 'vite-plugin-vue-devtools'

const cesiumSource = 'node_modules/cesium/Build/Cesium'
const cesiumBaseUrl = '/cesium'
const cesiumAssetDirs = ['Assets', 'ThirdParty', 'Workers', 'Widgets']

function contentType(filePath: string) {
  const typeMap: Record<string, string> = {
    '.css': 'text/css',
    '.gif': 'image/gif',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.js': 'application/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.svg': 'image/svg+xml',
    '.wasm': 'application/wasm',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
  }
  return typeMap[extname(filePath).toLowerCase()] ?? 'application/octet-stream'
}

function cesiumAssetsPlugin(): Plugin {
  return {
    name: 'waternet-cesium-assets',
    configureServer(server: ViteDevServer) {
      server.middlewares.use((req: IncomingMessage, res: ServerResponse, next: () => void) => {
        if (!req.url?.startsWith(`${cesiumBaseUrl}/`)) {
          next()
          return
        }
        const requestPath = decodeURIComponent(req.url.split('?')[0] ?? '')
          .replace(cesiumBaseUrl, '')
          .replace(/^\/+/, '')
        const filePath = normalize(join(cesiumSource, requestPath))
        const sourceRoot = normalize(cesiumSource)
        if (!filePath.startsWith(sourceRoot) || !existsSync(filePath) || !statSync(filePath).isFile()) {
          next()
          return
        }
        res.setHeader('Content-Type', contentType(filePath))
        createReadStream(filePath).pipe(res)
      })
    },
    closeBundle() {
      for (const dir of cesiumAssetDirs) {
        cpSync(join(cesiumSource, dir), join('dist', cesiumBaseUrl.replace(/^\/+/, ''), dir), {
          recursive: true,
        })
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  define: {
    CESIUM_BASE_URL: JSON.stringify(cesiumBaseUrl),
  },
  plugins: [
    vue(),
    vueJsx(),
    vueDevTools(),
    cesiumAssetsPlugin(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
