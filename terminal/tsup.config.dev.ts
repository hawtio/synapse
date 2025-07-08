import { defineConfig } from 'tsup'

export default defineConfig({
  name: 'synapse-terminal',
  entry: ['src/synapse-host.ts'],
  outDir: 'dist',
  target: 'es2022',
  dts: true,
  sourcemap: true,
  loader: {
    '.yaml': 'text',
  },
  platform: 'node',
  onSuccess: 'node --env-file .env.development dist/synapse-host.js',
  publicDir: 'public',
  watch: true,
})
