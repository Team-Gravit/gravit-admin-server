---
description: 관리자 서버(gravit-admin) 환경 변수·시크릿 목록과 보관 규칙
---

## Secret Convention

관리자 서버의 비밀값 원본은 GitHub Secrets다. 값은 저장소에 커밋하지 않는다. gravit-server CD와 같은 방식으로 넣는다.

- 빌드 때: CD(`cd.yml` "application.yml 시크릿 주입")가 `microsoft/variable-substitution`으로 시크릿을 `src/main/resources/application.yml` 값에 넣고 그대로 jar, 이미지를 만든다. 저장소의 `application.yml`은 바뀌지 않는다(로컬 실행은 `${ADMIN_DB_URL}` 같은 환경 변수 자리를 그대로 쓴다)
- 배포 때: CD가 서버 `.env`(`/home/serverking/gravit/qa`)를 compose 보간 값 4개(비밀 아님)로만 새로 쓴다. 서버에서 `.env`를 손으로 고쳐도 다음 배포에서 덮어써진다
- 값을 바꾸려면 GitHub Secrets를 고치고 다시 배포한다(이미지를 다시 만든다)
- 이미지에 비밀값이 들어가므로 Docker Hub 저장소 `gravit-admin-server`는 비공개여야 한다. CD는 저장소가 공개로 조회되면 이미지를 올리기 전에 멈춘다. 비밀값을 바꿔도 예전 SHA 태그 이미지에는 예전 값이 남는다
- 비밀값을 다루는 빌드 job은 Gradle 캐시에 쓰지 않는다(`cache-read-only: true`)
- CD는 이미지를 만들기 전에 필요한 시크릿이 비었는지 보고, 비었으면 키 이름만 출력하고 멈춘다

### 이미지 안 `application.yml`에 넣는 값

| 설정 키 | GitHub 시크릿 | 용도 | 비고 |
|---|---|---|---|
| `spring.datasource.url`, `username`, `password` | `ADMIN_DB_URL`, `ADMIN_DB_USERNAME`, `ADMIN_DB_PASSWORD` | 관리자 DB(QA, 스테이징, 감사 로그 소유 데이터) | 같은 Postgres 인스턴스의 별도 DB(`gravit_qa`) |
| `qa.auth.host` | `MAIN_API_HOST` | 메인 서버(gravit-server) 주소. 관리자 판정(`/api/v1/admin/me`)과 메인 API 호출(`qa.adapter.gravit.host`가 이 값을 따른다)이 함께 쓴다 | admin-web이 로그인하는 메인 환경과 같아야 한다(prod `https://grav-it.inuappcenter.kr`) |
| `qa.adapter.figma.token` | `FIGMA_TOKEN` | Figma PAT (파일 내용·이미지 읽기) | 발급 계정이 QA 대상 파일을 볼 수 있어야 한다 |
| `qa.adapter.notion.token` | `NOTION_TOKEN` | Notion Integration | gravit-chatbot과 같은 Integration. QA 상위 페이지에 연결해야 읽힌다 |
| `qa.adapter.github.token` | `GH_READ_TOKEN` | Fine-grained PAT (public read-only) | GitHub는 `GITHUB_`로 시작하는 시크릿 이름을 받지 않는다. 호출 한도용(인증 시 시간당 5,000회) |
| `qa.adapter.llm.api-key` | `LITELLM_API_KEY` | litellm-gw 키 | 게이트웨이에 DB가 없어 virtual key를 만들 수 없으므로 기존 master key를 쓴다 (설계 7-3, 02_PREP_GUIDE 1-4) |
| (PR-B에서 추가) `qa.main-db.url`, `username`, `password` | `MAIN_DB_URL`, `MAIN_DB_USERNAME`, `MAIN_DB_PASSWORD` | 메인 prod DB 읽기 전용 연결(계정 `gravit_admin_ro`) | 시크릿은 등록돼 있다. 설정 키가 생기는 PR-B에서 주입 줄과 확인 목록에 넣는다 |

`ADMIN_WORKER_ID`(`job.locked_by`)는 compose에서 `admin-api`로 고정한다. `GRAVIT_TEST_API_KEY`(gravit-server 테스트 API 서비스 키)는 #15 처리 후 위 표와 같은 방식으로 추가한다.
외부 연동 base-url과 DB ID 같은 비밀이 아닌 값은 `application.yml`의 `qa.adapter.*`에 둔다.

### 서버 `.env` (CD가 배포마다 씀, 비밀 아님)

`docker-compose.yml`을 해석할 때만 쓴다. 컨테이너에는 넘기지 않는다.

| `.env` 키 | GitHub 시크릿 | 용도 |
|---|---|---|
| `DOCKER_HUB_USERNAME` | 같은 이름 | 이미지 이름 `{값}/gravit-admin-server`. 이미지 push에도 쓴다 |
| `ADMIN_API_PORT` | 같은 이름 | admin-api 호스트 포트 |
| `ADMIN_API_CPUS`, `ADMIN_API_MEMORY` | 같은 이름 | admin-api 자원 한도 |

### CD 전용 (GitHub Secrets)

gravit-server CD와 같은 이름을 쓴다. 저장소 시크릿 또는 이 저장소에 접근을 허용한 조직 시크릿이다.

| 시크릿 | 용도 |
|---|---|
| `DOCKER_HUB_PASSWORD` | 이미지 push |
| `APPCENTER_SERVER_IP`, `APPCENTER_SERVER_PORT`, `APPCENTER_SERVER_USERNAME`, `APPCENTER_SERVER_PASSWORD` | 앱센터 서버 scp, ssh |

### 되돌리기

비밀값이 이미지 안에 있으므로 서버에서 값만 바꿔 되돌릴 수 없다. 원인 시크릿을 고쳐 CD를 다시 돌리거나, 직전 커밋의 SHA 태그 이미지로 되돌린다. 이 방식 이전(#39 전)의 이미지는 환경 변수로 값을 받으므로 그때의 compose와 `.env`가 함께 있어야 뜬다.

### 스모크 테스트 전용 (서버 `.env`에는 두지 않는다)

`./gradlew liveTest`가 실제 서비스를 호출할 때만 쓴다. 없으면 해당 테스트를 건너뛴다.

| 변수 | 용도 |
|---|---|
| `QA_LIVE_NOTION_SPRINT_DB_ID`, `QA_LIVE_NOTION_SPEC_DB_ID` | Notion 스프린트 DB, 기능 명세 DB ID (+ `NOTION_TOKEN`) |
| `QA_LIVE_FIGMA_FILE_KEY` | QA 페이지가 있는 Figma 파일 키 (+ `FIGMA_TOKEN`) |

### 규칙

- 토큰 원문을 로그·DB·응답에 남기지 마라. 캐시 키가 필요하면 SHA-256 해시를 쓴다 (`AdminVerificationCache`)
- 외부 연동 예외 메시지에는 상태 코드와 경로만 남기고 응답 본문을 넣지 마라. LiteLLM 인증 오류는 마스킹된 키를 되돌려 준다
- 토큰을 담는 `*Props` record는 `toString()`에서 토큰을 빼라
- 새 비밀값을 추가하면 이 표, `cd.yml`의 "배포 시크릿 확인" 목록과 "application.yml 시크릿 주입" 줄을 같은 작업에서 갱신하라. 시크릿은 머지 전에 먼저 등록한다(없으면 배포가 "배포 시크릿 확인"에서 멈춘다)
