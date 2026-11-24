---
description: 관리자 서버(gravit-admin) 환경 변수·시크릿 목록과 보관 규칙
---

## Secret Convention

관리자 서버는 서버의 `.env`(compose 프로젝트 디렉토리 `/home/serverking/gravit/qa`. 경로 이름은 설계 C-3에서 바꾼다)에서 환경 변수로 값을 받는다. 값은 저장소에 커밋하지 않는다.
값의 원본은 GitHub Secrets다. CD(`cd.yml`)가 배포마다 시크릿으로 서버 `.env`를 새로 쓴다(`.github/scripts/deploy-env.sh`). 서버에서 `.env`를 손으로 고쳐도 다음 배포에서 덮어써지므로, 값은 GitHub Secrets에서 바꾸고 배포한다.

- 비밀값을 이미지 안 설정 파일에 넣지 않는다(gravit-server의 `application-prod.yml` 치환 방식을 쓰지 않는다). 저장소가 공개이고 이미지가 Docker Hub에 올라간다
- 이미지를 만들기 전에 "배포 시크릿 확인" job이 모든 키가 있는지, 값이 영숫자와 `_ . / : @ % + = , ? & ~ -`로만 돼 있는지 본다. 실패하면 키 이름만 출력하고 이미지도 서버도 건드리지 않는다. 비밀번호는 영숫자만 쓴다
- 직전 `.env`는 서버에 `.env.prev`로 한 벌 남는다(권한 600)

### 서버 `.env` 키 (GitHub Secrets에서 주입)

| `.env` 키 | GitHub 시크릿 이름 | 용도 | 비고 |
|---|---|---|---|
| `ADMIN_DB_URL`, `ADMIN_DB_USERNAME`, `ADMIN_DB_PASSWORD` | 같은 이름 | 관리자 DB(QA, 스테이징, 감사 로그 소유 데이터) | 같은 Postgres 인스턴스의 별도 DB(`gravit_qa`) |
| `MAIN_API_HOST` | 같은 이름 | 메인 서버(gravit-server) 주소. 관리자 판정(`/api/v1/admin/me`)과 메인 API 호출이 함께 쓴다 | admin-web이 로그인하는 메인 환경과 같아야 한다(prod `https://grav-it.inuappcenter.kr`) |
| `MAIN_DB_URL`, `MAIN_DB_USERNAME`, `MAIN_DB_PASSWORD` | 같은 이름 | 메인 prod DB 읽기 전용 연결(계정 `gravit_admin_ro`) | 컨테이너에 넘기는 것은 PR-B(설계 6-1)부터 |
| `FIGMA_TOKEN` | 같은 이름 | Figma PAT (파일 내용·이미지 읽기) | 발급 계정이 QA 대상 파일을 볼 수 있어야 한다 |
| `NOTION_TOKEN` | 같은 이름 | Notion Integration | gravit-chatbot과 같은 Integration. QA 상위 페이지에 연결해야 읽힌다 |
| `GITHUB_TOKEN` | `GH_READ_TOKEN` | Fine-grained PAT (public read-only) | GitHub는 `GITHUB_`로 시작하는 시크릿 이름을 받지 않는다. 호출 한도용(인증 시 시간당 5,000회) |
| `LITELLM_API_KEY` | 같은 이름 | litellm-gw 키 | 게이트웨이에 DB가 없어 virtual key를 만들 수 없으므로 기존 master key를 쓴다 (설계 7-3, 02_PREP_GUIDE 1-4) |
| `DOCKER_HUB_USERNAME` | 같은 이름 | compose 보간. 이미지 이름 `{값}/gravit-admin-server` | 이미지 push에도 쓴다. 컨테이너에는 넘기지 않는다 |
| `ADMIN_API_PORT` | 같은 이름 | compose 보간. admin-api 호스트 포트 | 비밀은 아니지만 같은 경로로 넣는다 |
| `ADMIN_API_CPUS`, `ADMIN_API_MEMORY` | 같은 이름 | compose 보간. admin-api 자원 한도 | 비밀은 아니지만 같은 경로로 넣는다 |

`ADMIN_WORKER_ID`(`job.locked_by`)는 compose에서 `admin-api`로 고정한다. `GRAVIT_TEST_API_KEY`(gravit-server 테스트 API 서비스 키)는 #15 처리 후 위 표와 같은 방식으로 추가한다.
외부 연동 base-url과 DB ID 같은 비밀이 아닌 값은 `application.yml`의 `qa.adapter.*`에 둔다.

### CD 전용 (GitHub Secrets, 서버 `.env`에 쓰지 않음)

gravit-server CD와 같은 이름을 쓴다. 저장소 시크릿 또는 이 저장소에 접근을 허용한 조직 시크릿이다.

| 시크릿 | 용도 |
|---|---|
| `DOCKER_HUB_PASSWORD` | 이미지 push |
| `APPCENTER_SERVER_IP`, `APPCENTER_SERVER_PORT`, `APPCENTER_SERVER_USERNAME`, `APPCENTER_SERVER_PASSWORD` | 앱센터 서버 scp, ssh |

### 되돌리기

배포 뒤 `.env` 때문에 문제가 생기면 서버에서 직전 값으로 되돌리고 컨테이너를 다시 만든다. 원인 시크릿을 고치기 전에 다시 배포하면 같은 값이 다시 써진다.

```bash
cd /home/serverking/gravit/qa
cp -p .env.prev .env
docker compose -f docker-compose.yml up -d --no-deps --force-recreate admin-api
```

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
- 새 변수를 추가하면 이 표, `.github/scripts/deploy-env.sh`의 키 목록, `cd.yml`의 env 블록 두 곳("시크릿 확인", "서버 .env 작성")을 같은 작업에서 갱신하라. 시크릿은 머지 전에 먼저 등록한다(없으면 배포가 "배포 시크릿 확인"에서 멈춘다)
