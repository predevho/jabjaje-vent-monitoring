/**
 * Vite 설정.
 *
 * 개발할 때 `/api` 요청을 Spring Boot 서버(8080)로 넘긴다. 배포할 때는 빌드 결과를 Spring Boot가 함께 내보내므로
 * 주소가 하나다 (ADR-0010).
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
