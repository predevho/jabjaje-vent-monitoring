/**
 * 서버 API 호출 공통 함수.
 *
 * 같은 주소의 `/api`를 부르므로 개발(Vite 프록시)과 배포(Spring Boot) 모두 그대로 동작한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-23 (#2)
 */
export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) {
    throw new Error(`${path} 요청 실패 (${response.status})`)
  }
  return (await response.json()) as T
}
