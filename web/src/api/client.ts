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

/**
 * JSON 본문으로 POST한다(#8). 서버가 안내 문구({message})와 함께 거절하면 그 문구를 오류 메시지로 쓴다.
 */
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(error?.message ?? `${path} 요청 실패 (${response.status})`)
  }
  return (await response.json()) as T
}
