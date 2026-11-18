# gravit-qa-server

CS 학습 서비스 Gravit의 **관리자 서버**.
백오피스 [gravit-admin-client](https://github.com/Team-Gravit/gravit-admin-client)의 관리자 API와 QA 메뉴 API를 제공하고, Notion 기능 명세, Figma 시안, 서비스 저장소 코드를 읽어 QA 체크리스트와 1차 판정을 준비합니다.

<br>

## 기술 스택

<img src="https://skillicons.dev/icons?i=java,spring,postgres,docker,githubactions&perline=5" alt="Java, Spring Boot, PostgreSQL, Docker, GitHub Actions" />

JPA · JdbcTemplate · Flyway · Spring Security · Spring AI · springdoc-openapi · Testcontainers

<br>

## 시작하기

JDK 21이 필요하고, 테스트에는 Docker가 필요합니다.

```bash
cp .env.example .env                                         # DB, 관리자 판정 서버, 외부 연동 토큰
export $(grep -v '^#' .env | xargs)
./gradlew bootRun --args='--spring.profiles.active=api'
```

Swagger UI: http://localhost:8080/swagger-ui.html

```bash
./gradlew build
./gradlew test       # Testcontainers로 Postgres 기동. QA_TEST_DB_URL이 있으면 그 DB 사용
./gradlew liveTest   # 실제 Notion, Figma 호출 스모크(@Tag("live")). 변수가 없으면 건너뜀
```

<br>

## 구조

```
src/main/java/gravit/qa/
├── security/         관리자 토큰 판정(/admin/me), 판정 캐시
├── job/              작업 큐, 재시도, heartbeat 회수
├── sprint/           스프린트, 명세 로드와 QA 준비 처리
├── feature/          기능 명세 사본, 스프린트 대상 기능
├── reflection/       수정 이력 반영 제안, Notion 반영
├── snapshot/         수집 스냅샷
├── checklist/        체크리스트 생성, 셀 판정과 이력
├── designReview/     Figma 시안과 명세 비교
├── autoReview/       저장소 코드를 읽는 1차 판정
├── fixReport/        파트별 수정사항 파일
├── adapter/          외부 연동(Figma, Notion, GitHub, OpenAPI, LLM)
└── global/           설정, 예외, 시간

src/main/resources/
├── db/migration/     Flyway 스키마 마이그레이션
└── prompts/          LLM 프롬프트

.claude/              Claude Code 작업 하네스(규칙, 스펙, 스킬)
```
