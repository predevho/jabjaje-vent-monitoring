# 화면은 React + TypeScript + Vite로 `web/`에 만들고, 빌드 결과는 Spring Boot가 함께 내보낸다

대시보드는 처음에 정적 HTML 한 장으로 계획했지만, React + TypeScript + Vite로 `web/` 모듈을 만든다. 공고의 "모바일 서비스 런칭"과 이어지고(React Native로 확장 가능), 이후 티켓에서 늘어나는 화면(이상 목록, 게이트웨이 배지, 부스트, A/S 신청과 판정)을 컴포넌트로 정리할 수 있다. TypeScript로 API 응답 형태를 타입으로 적어 두면 서버 DTO가 바뀌었을 때 화면 코드에서 바로 오류가 나므로, AI가 작성한 코드를 검증하는 장치가 하나 더 생긴다. 배포는 하나로 합친다. Docker 빌드에서 React를 빌드해 Spring Boot의 정적 리소스로 넣으므로 주소가 하나이고 CORS 설정이 필요 없으며, `docker compose up` 한 번 원칙을 지킨다. 개발할 때는 Vite 개발 서버가 `/api` 요청을 Spring으로 넘긴다.

## Considered Options

- **정적 HTML + JavaScript**: 설정이 가장 적지만 화면이 늘수록 한 파일이 커지고 타입 검증이 없다. 기각.
- **Thymeleaf**: 서버에서 HTML을 만들지만 그래프와 자동 갱신은 결국 JavaScript가 필요하고, 화면용 코드가 API와 따로 생긴다. 기각.
- **React를 별도 컨테이너(nginx)로 배포**: 실제 서비스에서 흔하지만 주소가 둘이 되어 CORS나 프록시 설정이 필요하다. MVP에는 과하다. 기각.

## Consequences

- 백엔드 포지션이므로 화면은 기능 확인용으로 단순하게 유지한다. 일정이 밀리면 화면을 표 하나 수준으로 줄인다(축소 순서 1).
- 서버 Docker 이미지 빌드에 Node 단계가 추가된다.
