# 관리자 서버 전환 설계 - 경계와 계약

> 이슈 #28. gravit-qa-server를 그래빗 관리자 서버로 바꾸고 gravit-server admin 패키지 기능 43개를 도메인 단위로 옮기기 위한 설계다. 구현은 하지 않는다.
> 근거 경로는 레포 기준 상대 경로이며 앞에 `[qa]`(gravit-qa-server), `[main]`(gravit-server), `[web]`(admin-web)를 붙여 레포를 구분한다.
> "사람 결정 필요"로 표시한 항목은 이 문서에서 확정하지 않는다. 추천안과 근거만 적는다.
> 이미 확정된 결정 P-1, P-2, P-4, P-6, P-7, 데이터 소유권 기준 분리(소유, API, 조회), 모듈러 모놀리스, Ravvit 범위 밖은 바꾸지 않는다.

## 0. 기준 브랜치와 커밋

| 이름 | GitHub | 로컬 경로 | 기준 브랜치 | 커밋 | 확인 시점 상태 |
|---|---|---|---|---|---|
| qa-server | `Team-Gravit/gravit-qa-server` (PUBLIC) | `~/Project/server/gravit-qa-server` | main | `45c5060c2f255d52ef9f443bf037c7d2e1b91d5b` (2026-11-15) | 원격과 같음, 커밋하지 않은 변경 없음. 이 문서의 브랜치 `docs/28-admin-server-design`은 이 커밋에서 분기 |
| gravit-server | `Team-Gravit/gravit-server` | `~/Project/server/gravit-server` | dev | `0d3f9a3b2be02ee6c84a6388410deaf0e1f348d5` (2026-11-17) | 원격과 같음, 변경 없음. 읽기 전용 |
| admin-web | `Team-Gravit/gravit-admin-client` (`gravit-admin-web`은 GitHub 리다이렉트) | `~/Project/client/gravit-admin-client` | main | `16cd54e4063bb1c68ebc64982ed7780e34d3c998` (2026-11-15) | 원격과 같음, 변경 없음. 읽기 전용 |

읽기 전용 두 레포는 `git fetch`만 했고 작업 트리를 바꾸지 않았다. 세 레포 모두 로컬 HEAD가 기준 브랜치 원격과 같아 작업 트리를 그대로 읽었다.

## 1. 결정 요약

> 2026-11-18 사용자 결정 반영: D-1 이름, D-3 (c), D-4(ii)(iii), D-5 네트워크, D-9 적용 시점, D-10 환경. 관리자 웹 운영은 prod 메인을 본다(M-2 해소).

| ID | 결정 | 선택안 | 사람 결정 필요 | 되돌리는 방법 |
|---|---|---|---|---|
| D-1 | 서비스 정체성 | **확정: 이름 `gravit-admin-server`** (파생 이름은 3-5). 시점은 추천 C: C-1 구조 이동 PR에서 패키지와 앱 이름, C-2 0단계 웹 변경 전에 새 도메인을 같은 컨테이너에 별칭으로 추가, C-3 이관을 마친 뒤 점검 시간 한 번에 레포, 이미지, 컨테이너, 배포 경로 | 시점만 예 | C-1 되돌림 PR. C-2 웹 rewrite를 옛 도메인으로 돌리고 별칭 삭제. C-3 옛 디렉터리와 옛 이미지 SHA 태그로 재기동 |
| D-2 | 모듈 구조와 경계 강제 | 단일 Gradle 모듈에서 최상위 패키지를 모듈로 쓴다(global, job, auth, adapter, qa, insight, support, member, content, audit). ArchUnit 테스트로 허용 의존 표를 강제 | 아니오 | ArchUnit 테스트와 테스트 의존성 삭제. 패키지 이동은 되돌림 PR |
| D-3 | 웹에서 관리자 서버로 가는 경로 | **확정: (c).** 0단계에 웹 `apiClient` base를 한 번 관리자 서버(Vercel rewrite `/admin-server/api/v1/admin/:path*`)로 바꾸고, 관리자 서버의 `/api/v1/admin/**` 전달 계층이 아직 옮기지 않은 기능과 API 방식 16개를 메인으로 넘긴다. 도메인 전환은 관리자 서버 배포로 한다(검증 중에는 헤더 조건, 5-7). 처음 선택은 (a)였다(5-4, 5-6) | 아니오 | 도메인 하나: 그 Controller에 헤더 조건을 다시 걸거나 전환 커밋을 revert해 관리자 서버 배포. 전체: 웹 `apiClient` base를 메인으로 되돌려 웹 배포. 둘 다 메인 코드 삭제 전까지 |
| D-4(i) | 조회 기능의 메인 DB 접근 | 메인 DB 읽기 전용 계정 + 두 번째 데이터소스(JdbcTemplate, 풀 최대 3). GRANT SELECT는 도메인을 옮길 때마다 테이블 단위로 추가 | 아니오 (계정 생성은 운영 작업) | 그 모듈의 조회 구현을 메인 API 호출로 바꾸거나 D-3 롤백. 계정은 REVOKE 후 DROP ROLE |
| D-4(ii) | 소유 테이블 위치 | **확정: 관리자 DB로 옮긴다.** 관리자 DB는 지금 QA DB를 그대로 쓰고 prod DB에 두지 않는다. 스테이징(V4, V21)은 5단계, 감사 로그(V22)는 6단계. 메인 테이블은 보류 기간 뒤 새 마이그레이션으로 DROP | 아니오 (generator 적재 대상을 바꿀 수 있는지는 확인 필요, M-3) | 메인 DROP 전: 관리자 DB 변경분을 역복사하고 웹을 메인으로 되돌림. DROP 뒤: 백업 덤프 복원 |
| D-4(iii) | STG-07 승격 흐름 | **확정: 승격 전 데이터는 관리자 DB가 관리하고, 승격할 때 메인(prod 서버)에 둔 학습 콘텐츠 생성 API를 호출한다.** 관리자 서버가 구조를 검증한 뒤 라벨을 멱등 키로 레슨, 문제, 옵션, 정답을 한 번에 만든다 | 아니오 | D-4(ii)와 같음 |
| D-5 | 메인 서버 API 계약 | 메인에 남는 16개 엔드포인트를 기존 `/api/v1/admin/**` 경로 그대로 호출한다. 쓰기는 자동 재시도하지 않는다. 연결 2초, 응답 10초. 메인 응답은 상태와 본문 바이트를 그대로 웹에 전달. **확정: 네트워크는 공개 도메인** | 아니오 | 호스트 설정값 하나 변경 |
| D-6 | 인증 전달 | 받은 `Authorization` 헤더 값을 그대로 메인에 붙인다. 판정 캐시는 유지하되 만료를 토큰 `exp`로 줄이고, 메인이 401이나 403을 주면 그 토큰 캐시를 지우고, 관리자 서버 소유 데이터의 쓰기 요청은 캐시를 건너뛴다. 사람이 요청하지 않은 호출의 인증은 미결 | 아니오 | 필터 분기와 설정값이라 되돌림 PR |
| D-7 | 응답 형식 호환과 검증 | 기준은 메인의 실제 응답(DTO + Jackson 기본값). 새 구현을 헤더 조건(`X-Admin-Route=local`)을 단 채 배포해 웹 요청은 계속 메인으로 넘기고, 헤더를 붙인 GET 요청을 prod 메인과 관리자 서버에 보내 JSON 트리를 비교하는 `liveTest` 계약 테스트를 전환의 통과 조건으로 둔다(D-10으로 dev 인스턴스가 없어졌다) | 아니오 | 테스트 삭제 |
| D-8 | 도메인 이관 작업 정의 | 메인 준비, 관리자 서버 구현, 전환(헤더 조건 삭제 배포), 메인 코드 삭제 4단계와 단계별 완료 기준. 메인 삭제는 전환 뒤 보류 기간이 지나야 한다. 보류 기간 = 관리자 서버가 그 도메인을 직접 처리한 뒤에도 메인 옛 코드를 지우지 않고 두는 기간. 이 동안은 관리자 서버에서 그 도메인을 다시 메인으로 넘기면 롤백이 끝난다(10-3) | 보류 기간 길이만 예 (추천 2주) | 메인 삭제 전에는 D-3 롤백. 삭제 뒤에는 삭제 PR revert와 메인 배포 |
| D-9 | 하네스 규칙 개정안 | `.claude/CLAUDE.md`, `README.md`, `.claude/rules/project-structure.md` 전, 후 문안. 깨진 참조는 이 문서, 정책 디렉터리, Swagger로 바꾼다. **확정: 이번 이슈(#28)에서 적용한다.** 하네스 개선 범위를 정한 뒤 함께 반영 | 개선 범위만 예 | 문서 되돌림 |
| D-10 | 배포 단위 | **확정: 단일 인스턴스.** 지금 컨테이너 하나(`api` 프로필)를 prod 메인 대상으로 쓰고 dev 인스턴스는 두지 않는다. CD는 main 머지 기준으로 바꾼다. 관리자 DB는 D-4(ii)대로 지금 QA DB | 아니오 (관리자 웹 운영이 prod 메인을 보는 것 확인, M-2 해소) | 판정, 호출 대상 호스트를 되돌리고 CD 트리거를 되돌림 |

## 2. 이관 대상 기능 43개

ID는 이 문서에서 그대로 쓴다. 엔드포인트는 `[main] src/main/java/gravit/code/admin/controller/` 각 Controller의 선언 순서대로 대응시켰고, 조회와 수정의 구분은 작업 지시의 ID 분류와 일치한다.
경로는 `/api/v1/admin` 뒤만 적는다. 단계는 P-1 순서(1 대시보드와 통계, 2 신고, 3 공지와 문의, 4 회원, 5 학습 콘텐츠와 스테이징, 6 감사 로그)다.

| ID | 메서드, 경로 | 메인 Controller.메서드 | 방식 | 모듈 | 단계 | 읽는 테이블(조회) 또는 부작용(API) |
|---|---|---|---|---|---|---|
| AUTH-01 | GET `/me` | AdminMeController.getMe | API | auth | 0 (전달 계층이 맡는다) | 없음. 메인 `users` 조회 |
| DASH-01 | GET `/dashboard/summary` | AdminDashboardController.getSummary | 조회 | insight | 1 | `users`, `staging_label`, `report` |
| DASH-02 | GET `/dashboard/active-users/daily?days=` | AdminDashboardController.getDailyActiveUsers | 조회 | insight | 1 | `user_daily_activity` |
| DASH-03 | GET `/dashboard/active-users/monthly?months=` | AdminDashboardController.getMonthlyActiveUsers | 조회 | insight | 1 | `user_daily_activity` |
| RPT-01 | GET `/reports?page=&reportType=&isResolved=` | AdminReportController.getReports | 조회 | support | 2 | `report` |
| RPT-02 | GET `/reports/{reportId}` | AdminReportController.getReport | 조회 | support | 2 | `report` |
| RPT-03 | PATCH `/reports/{reportId}/status` | AdminReportController.updateStatus | API | support | 2 | `report.is_resolved` 변경. 이벤트, 감사 없음 |
| NTC-01 | GET `/notices?page=` | AdminNoticeController.getNotices | 조회 | support | 3 | `notice` (`deleted_at IS NULL`) |
| NTC-02 | GET `/notices/{noticeId}` | AdminNoticeController.getNotice | 조회 | support | 3 | `notice` |
| NTC-03 | POST `/notices` | AdminNoticeController.createNotice | API | support | 3 | 공지 생성. `PUBLISHED`로 만들 때만 `NoticeCreatedEvent` → 커밋 후 푸시 |
| NTC-04 | PATCH `/notices/{noticeId}` | AdminNoticeController.updateNotice | API | support | 3 | 상태가 바뀌면 감사 `NOTICE_STATUS_CHANGE`. 푸시 없음 |
| NTC-05 | DELETE `/notices/{noticeId}` | AdminNoticeController.deleteNotice | API | support | 3 | soft delete, 감사 `NOTICE_DELETE` |
| INQ-01 | GET `/inquiries?status=&page=` | AdminInquiryController.getInquiries | 조회 | support | 3 | `inquiry`, `users` |
| INQ-02 | GET `/inquiries/{inquiryId}` | AdminInquiryController.getInquiry | 조회 | support | 3 | `inquiry`, `inquiry_answer`, `users` |
| INQ-03 | POST `/inquiries/{inquiryId}/answer` | AdminInquiryController.answerInquiry | API | support | 3 | 답변 생성, 문의 `RESOLVED`, `InquiryAnsweredEvent` → 커밋 후 푸시 |
| INQ-04 | PUT `/inquiries/{inquiryId}/answer` | AdminInquiryController.updateAnswer | API | support | 3 | 답변 수정. 푸시 없음 |
| INQ-05 | DELETE `/inquiries/{inquiryId}/answer` | AdminInquiryController.deleteAnswer | API | support | 3 | 답변 삭제, 문의 다시 열기 |
| USER-01 | GET `/users?page=&search=&status=&role=` | AdminUserController.getUsers | 조회 | member | 4 | `users` |
| USER-02 | GET `/users/{userId}` | AdminUserController.getUser | 조회 | member | 4 | `users` |
| USER-03 | PATCH `/users/{userId}/status` | AdminUserController.updateStatus | API | member | 4 | 상태 변경(복구 시 새 handle), 감사 `USER_STATUS_CHANGE` |
| USER-04 | PATCH `/users/{userId}/role` | AdminUserController.updateRole | API | member | 4 | 권한 변경, 감사 `USER_ROLE_CHANGE` |
| CON-01 | GET `/chapters?page=` | AdminChapterController.getChapters | 조회 | content | 5 | `chapter` |
| CON-02 | GET `/chapters/{chapterId}` | AdminChapterController.getChapter | 조회 | content | 5 | `chapter`, `unit` |
| CON-03 | GET `/chapters/{chapterId}/stats` | AdminChapterController.getChapterStats | 조회 | content | 5 | `chapter`, `unit`, `lesson`, `lesson_submission`, `users` |
| CON-04 | PATCH `/chapters/{chapterId}` | AdminChapterController.updateChapter | API | content | 5 | 챕터 수정 |
| CON-05 | GET `/chapters/{chapterId}/units?page=` | AdminChapterController.getUnits | 조회 | content | 5 | `unit` |
| CON-06 | GET `/units/{unitId}` | AdminUnitController.getUnit | 조회 | content | 5 | `unit`, `lesson` |
| CON-07 | PATCH `/units/{unitId}` | AdminUnitController.updateUnit | API | content | 5 | 유닛 수정 |
| CON-08 | GET `/units/{unitId}/lessons?page=` | AdminUnitController.getLessons | 조회 | content | 5 | `lesson` |
| CON-09 | GET `/lessons/{lessonId}` | AdminLessonController.getLesson | 조회 | content | 5 | `lesson`, `problem` |
| CON-10 | PATCH `/lessons/{lessonId}` | AdminLessonController.updateLesson | API | content | 5 | 레슨 수정 |
| CON-11 | GET `/lessons/{lessonId}/problems?page=` | AdminLessonController.getProblems | 조회 | content | 5 | `problem` |
| CON-12 | GET `/problems/{problemId}` | AdminProblemController.getProblem | 조회 | content | 5 | `problem`, `option`, `answer` |
| CON-13 | PATCH `/problems/{problemId}/objective` | AdminProblemController.updateObjective | API | content | 5 | 객관식 문제, 옵션 수정 |
| CON-14 | PATCH `/problems/{problemId}/subjective` | AdminProblemController.updateSubjective | API | content | 5 | 주관식 문제, 정답 수정 |
| STG-01 | GET `/staging/labels?page=&status=` | AdminStagingController.getLabels | 소유 | content | 5 | 관리자 DB(추천안) |
| STG-02 | GET `/staging/labels/{label}` | AdminStagingController.getLabelDetail | 소유 | content | 5 | 관리자 DB(추천안) |
| STG-03 | PATCH `/staging/lessons/{lessonId}` | AdminStagingController.updateLesson | 소유 | content | 5 | 관리자 DB(추천안) |
| STG-04 | PATCH `/staging/problems/{problemId}` | AdminStagingController.updateProblem | 소유 | content | 5 | 관리자 DB(추천안) |
| STG-05 | PATCH `/staging/options/{optionId}` | AdminStagingController.updateOption | 소유 | content | 5 | 관리자 DB(추천안) |
| STG-06 | PATCH `/staging/answers/{answerId}` | AdminStagingController.updateAnswer | 소유 | content | 5 | 관리자 DB(추천안) |
| STG-07 | PATCH `/staging/labels/{label}/status` | AdminStagingController.promote | API | content | 5 | 메인 `lesson`, `problem`, `option`, `answer` INSERT, 감사 `STAGING_PROMOTE` |
| AUD-01 | (엔드포인트 없음) 감사 로그 기록 | `[main] src/main/java/gravit/code/admin/support/AuditLogRecorder.java` | 소유 | audit | 6 | USER-03, USER-04, NTC-04, NTC-05, STG-07이 같은 트랜잭션에서 기록 |

방식별 개수: 조회 20, API 16(STG-07 포함), 소유 7(STG-01~06, AUD-01). 합계 43. 메인에 남는 엔드포인트는 API 방식 16개다(D-5).
읽는 테이블은 각 Service가 주입받는 Repository로 추렸다(예: `[main] src/main/java/gravit/code/admin/service/AdminChapterService.java`는 Chapter, Unit, AdminStats, AdminUser Repository). 도메인 이관 1단계(D-8)에서 컬럼 단위로 다시 확정한다.

---

## 3. D-1 서비스 정체성

### 3-1. 현황과 근거

| 대상 | 현재 값 | 근거 |
|---|---|---|
| 레포 | `Team-Gravit/gravit-qa-server`, PUBLIC | `gh repo view` |
| Java 패키지 | `gravit.qa` | `[qa] src/main/java/gravit/qa/` |
| 앱 이름 | `gravit-qa` | `[qa] src/main/resources/application.yml:3` |
| Gradle 프로젝트, JAR | `gravit-qa-server`, `gravit-qa-server-0.0.1-SNAPSHOT.jar` | `[qa] settings.gradle:1`, `[qa] Dockerfile:3` |
| compose 서비스, 컨테이너 | `qa-api`, `gravit-qa-api` | `[qa] docker-compose-qa.yml:2-3` |
| 작업 워커 ID | `QA_WORKER_ID=qa-api` (`job.locked_by`에 남는 값) | `[qa] docker-compose-qa.yml:10` |
| 도메인 | `grav-it-qa.inuappcenter.kr` (서버 Caddy, HTTPS) | `[web] vercel.json:12-13`, `[web] vite.config.ts:31`, `[web] docs/conventions/decisions.md` D9-4 |
| 이미지 | `{DOCKER_HUB_USERNAME}/gravit-qa-server:latest`, 커밋 SHA 태그 | `[qa] .github/workflows/cd.yml:26,54`, `[qa] docker-compose-qa.yml:4` |
| 서버 배포 경로, compose 프로젝트 | `/home/serverking/gravit/qa`, 프로젝트 이름 `qa` | `[qa] .github/workflows/cd.yml:76,90`, `[qa] docs/deploy-runbook.md` 0단계 |
| 네트워크, 볼륨 | `gravit-qa`(이름 고정 없음, 실제 `qa_gravit-qa`), `llm-net`(external), 볼륨 `gravit-qa-evidence`(이름 고정) | `[qa] docker-compose-qa.yml:38-46` |
| 로컬 폴더 | `~/Project/server/gravit-qa-server` | - |

이름이 QA로 남은 채 회원, 공지, 콘텐츠 기능이 들어오면 로그, 컨테이너 목록, 대화에서 "QA 서버가 회원 상태를 바꾼다"는 혼동이 생긴다. 반대로 이름 변경은 운영 작업이 끼어 되돌리기 어렵다.

### 3-2. 이름을 바꿀 때 영향받는 곳

| 대상 | 영향 | 비고 |
|---|---|---|
| GitHub 레포 이름 | git remote, `gh -R` 명령, API 호출 | GitHub가 옛 이름을 리다이렉트한다(admin-web이 실제 사례: `gravit-admin-web` → `gravit-admin-client`). 저장소 시크릿과 Actions는 그대로 따라간다. runbook의 `gh secret set -R Team-Gravit/gravit-qa-server`, `gh workflow run ... -R`을 고친다 |
| Java 패키지 | 모든 소스, 테스트의 package, import | `.claude/rules/*`의 `paths: src/main/java/**` 예시, `common.md`의 `import static gravit.qa.global...` 예시, 스킬 문서의 `src/main/java/gravit/qa/{domain}` 예시도 함께 |
| 앱 이름 | 로그, Actuator 메타데이터 | 기능 영향 없음 |
| Gradle 프로젝트 이름 | JAR 이름 | `[qa] Dockerfile:3`의 `JAR_NAME`을 같은 PR에서 바꿔야 이미지 빌드가 깨지지 않는다 |
| compose 서비스, 컨테이너 이름 | CD 스크립트(`pull qa-api`, `docker inspect ... gravit-qa-api`), runbook 명령 | `[qa] .github/workflows/cd.yml:113-125` |
| 작업 워커 ID | `job.locked_by` | 실행 중인 작업이 있을 때 바꾸면 옛 ID의 작업은 heartbeat 회수로 다시 잡힌다. 작업이 없을 때 바꾼다 |
| 도메인 | DNS 레코드, Caddy 사이트 블록과 인증서, `[web] vercel.json` rewrite, `[web] vite.config.ts` 기본 `VITE_QA_ORIGIN`, `[web] docs/conventions/decisions.md` D9-4, runbook 4, 6, 7단계 | 새 도메인을 별칭으로 먼저 추가하면 중단 없이 옮길 수 있다 |
| 이미지 이름 | `[qa] .github/workflows/cd.yml` `IMAGE`, compose `image`, runbook 8단계 되돌리기 | 되돌리기는 SHA 태그를 쓰므로 옛 이미지 저장소를 보존 기간 동안 남긴다. 새 Docker Hub 저장소의 공개 여부를 확인한다 |
| 서버 배포 경로 | CD의 scp 대상과 `cd` 경로, runbook, 서버 `.env` 위치(권한 600 유지) | 디렉터리 이름이 compose 프로젝트 이름이라 네트워크가 `qa_gravit-qa`에서 새 이름으로 바뀐다. `container_name`이 고정이라 새 프로젝트로 올리기 전에 옛 프로젝트를 내려야 한다(짧은 중단) |
| 볼륨 | `gravit-qa-evidence` | 이름이 고정돼 경로가 바뀌어도 그대로 쓴다. 이름까지 바꾸려면 데이터 복사가 필요하니 바꾸지 않는다 |
| 서버 `.env` 키 | `QA_DB_URL`, `QA_AUTH_HOST` 등 | 키 이름은 바꾸지 않아도 동작한다. 바꾸면 compose와 `application.yml`을 같이 바꾼다 |
| 중앙 모니터링 | 현재 영향 없음 | qa-server는 Actuator `health`만 노출하고 Prometheus 레지스트리가 없다(`[qa] src/main/resources/application.yml:25-29`, `[qa] build.gradle`). 중앙 스택은 메인의 고정 네트워크 이름만 참조한다(`[main] docker-compose-prod.yml:91-96`). 외부 가용성 감시 등록 여부는 확인 필요 |
| 웹 문서 | `[web] docs/conventions/decisions.md` D9, `QA_SPEC.md`의 서버 이름 | 웹 저장소 담당이 고친다 |
| 로컬 폴더 | IDE 프로젝트(`.idea`), Claude Code 프로젝트 기억(`~/.claude/projects/` 아래 폴더 경로 키) | 폴더 이름을 바꾸면 기억 폴더가 새로 생긴다. 옛 폴더를 옮겨야 이어진다 |
| 외부 서비스 표시 이름 | Notion Integration 이름, LiteLLM 키 이름 | 동작 영향 없음. 확인 필요 |

### 3-3. 대안

- A. 그대로 둔다
- B. 이관 시작 전에 전부 한 번에 바꾼다
- C. 단계별로 바꾼다
  - C-1: D-2 구조 이동 PR(동작 변경 없는 패키지 이동)과 같은 PR에서 Java 패키지와 앱 이름을 바꾼다
  - C-2: 0단계 웹 변경 전에 새 도메인을 같은 Caddy 대상(`QA_API_PORT`)에 별칭으로 추가하고, 새 관리자 rewrite는 처음부터 새 도메인을 쓴다. QA rewrite는 아무 때나 옮기고 옛 도메인은 그 뒤에 지운다
  - C-3: 6단계를 마친 뒤(또는 이관이 멈춘 조용한 시점) 점검 시간 한 번에 레포, 이미지, 컨테이너, 워커 ID, 배포 경로를 바꾼다

### 3-4. 비교

| 기준 | A | B | C |
|---|---|---|---|
| 운영 중단 | 없음 | 배포 경로 변경 때 짧은 중단 1회 | C-3에서 1회 |
| 이관 작업과 충돌 | 없음 | 이관 시작이 늦어지고, 큰 변경과 첫 도메인 이관이 겹친다 | C-1은 어차피 하는 구조 이동에 얹고, C-3은 이관이 끝난 뒤라 겹치지 않는다 |
| 웹 변경 | 없음 | rewrite 대상 변경 1회 | C-2 덕분에 새 관리자 rewrite가 처음부터 새 도메인을 써 추가 변경이 없다 |
| 이름 혼동 | 계속 남음 | 바로 해소 | 코드 이름은 C-1에서, 운영 이름은 C-3에서 해소 |
| 되돌리기 | 해당 없음 | 여러 곳을 동시에 되돌려야 한다 | 단계마다 범위가 작다 |

### 3-5. 선택 - 이름 확정, 시점은 사람 결정 필요

이름은 `gravit-admin-server`로 확정했다(2026-11-18). 나머지 이름은 여기서 기계적으로 따온다(제안):

| 대상 | 지금 | 새 이름(제안) |
|---|---|---|
| 레포, Gradle 프로젝트 | `gravit-qa-server` | `gravit-admin-server` |
| Java 패키지 | `gravit.qa` | `gravit.admin` |
| 앱 이름 | `gravit-qa` | `gravit-admin` |
| compose 서비스, 컨테이너, 워커 ID | `qa-api`, `gravit-qa-api`, `qa-api` | `admin-api`, `gravit-admin-api`, `admin-api` |
| 이미지 | `{DOCKER_HUB_USERNAME}/gravit-qa-server` | `{DOCKER_HUB_USERNAME}/gravit-admin-server` |
| 서버 배포 경로 | `/home/serverking/gravit/qa` | `/home/serverking/gravit/admin` |
| 도메인 | `grav-it-qa.inuappcenter.kr` | `grav-it-admin.inuappcenter.kr` |
| QA 모듈 | 최상위 QA 패키지들 | `gravit.admin.qa` (D-2) |

시점은 C를 추천한다. C-1을 구조 이동과 묶는 이유는 같은 파일 전체를 두 번 옮기는 diff를 한 번으로 줄이기 위해서다. C-3을 마지막에 두는 이유는 서버 작업과 짧은 중단이 끼는 일을 한 번에 끝내기 위해서다.
구조 이동(C-1) 전까지 이 문서의 경로 예시는 지금 패키지 `gravit.qa`로 적는다.

### 3-6. 되돌리는 방법

- C-1: 패키지 이동 PR을 revert한다. 동작 변경이 없어 데이터 영향이 없다
- C-2: 웹 rewrite 대상을 옛 도메인으로 돌린 뒤 Caddy 별칭을 지운다
- C-3: 옛 디렉터리의 compose로 옛 이미지 SHA 태그를 다시 올린다(runbook 8단계와 같은 방식). 옛 레포 이름은 GitHub에서 다시 바꿀 수 있지만 그 사이 생긴 리다이렉트는 사라진다

---

## 4. D-2 모듈 구조와 경계 강제

### 4-1. 현황과 근거

지금 최상위 패키지와 패키지 사이 import(같은 패키지 제외, `[qa] src/main/java/gravit/qa/` 기준으로 셌다):

| 패키지 (파일 수) | 의존하는 패키지 |
|---|---|
| global (14) | 없음 |
| job (25) | global |
| security (10) | global |
| snapshot (7) | global |
| adapter (67) | job (`adapter/exception/LlmOutputInvalidException.java:3`, `NonRetryableExternalApiException.java:4`가 `job.support.NonRetryableJobException`을 쓴다) |
| feature (18) | adapter, global |
| checklist (45) | adapter, feature, global, job, security, sprint |
| designReview (23) | adapter, checklist, feature, global, job, security, sprint |
| reflection (25) | adapter, feature, global, job, security, sprint |
| autoReview (21) | adapter, checklist, feature, global, job, sprint |
| fixReport (14) | checklist, designReview, feature, global, job, sprint |
| sprint (50) | adapter, autoReview, checklist, designReview, feature, global, job, reflection, security, snapshot |

- `sprint`와 `checklist`, `designReview`, `reflection`, `autoReview`는 서로를 참조하는 순환이 있다. QA 흐름 하나를 나눠 담은 패키지라서다
- 도메인 패키지는 Controller에서 `security.principal.LoginAdmin`만 가져다 쓴다
- `.claude/rules/project-structure.md`는 최상위 패키지를 곧 도메인으로 보고, 그 아래에 `controller/`, `service/`, `repository/`, `domain/`, `dto/`를 둔다

### 4-2. 모듈 배치 (확정)

```
src/main/java/gravit/qa/          # D-1 C-1에서 기준 패키지 이름만 바뀐다
├── global/      공통 설정, 예외, 시간. 메인 DB 읽기 전용 데이터소스 설정(MainDbConfig)
├── job/         작업 큐. 여러 모듈이 쓰는 플랫폼으로 둔다
├── auth/        관리자 판정 필터, /admin/me 판정 캐시(기존 security), AUTH-01
├── adapter/     외부 REST 연동: figma, notion, github, openapi, llm + gravit(메인 서버 API, 신규)
├── qa/          QA 흐름: sprint, feature, reflection, snapshot, checklist, designReview, autoReview, fixReport
├── insight/     DASH-01~03
├── support/     RPT, NTC, INQ
├── member/      USER-01~04
├── content/     CON, STG
└── audit/       AUD-01
```

| 기존 패키지 | 새 위치 | 비고 |
|---|---|---|
| global | `global` | 그대로 |
| security | `auth` | 이름만 바꾼다. `AdminMeClient`는 판정 전용 상태 매핑(401, 403, 503)이 있어 `auth/infrastructure`에 남긴다 |
| job | `job` | 그대로. 플랫폼 모듈 |
| adapter | `adapter` | 그대로 + `adapter/gravit` 추가 |
| sprint, feature, reflection, snapshot, checklist, designReview, autoReview, fixReport | `qa/sprint`, `qa/feature`, `qa/reflection`, `qa/snapshot`, `qa/checklist`, `qa/designReview`, `qa/autoReview`, `qa/fixReport` | 모듈 안 순환은 지금 그대로 허용한다 |

- `observability`, `incident`, `ops`: 이번 43개 기능 중 해당 기능이 없다. 빈 패키지를 미리 만들지 않고, 기능이 생길 때 도메인 모듈 규칙을 그대로 따른다
- `support`에 RPT를 넣은 이유: 신고는 문제 단위라 `content`와도 가깝지만, RPT는 2단계이고 `content`는 5단계다. RPT를 `content`에 넣으면 `content` 모듈을 2단계에 먼저 열어야 하고, 신고와 문의는 모두 사용자 제출물을 운영자가 처리하는 같은 흐름이다
- 메인 DB 조회 구현은 각 모듈의 `repository/custom/*RepositoryImpl`에 두고 `@MainDb` 한정자가 붙은 `JdbcTemplate`만 쓴다. 메인 테이블에 대한 JPA 엔티티는 만들지 않는다(QA 데이터소스의 `ddl-auto: validate`와 섞이지 않게, 메인 엔티티를 복제하지 않게)
- 메인 서버 API 호출은 `adapter/gravit`의 클라이언트로만 한다. D-3 (c)의 `/api/v1/admin/**` 전달 Controller도 `adapter/gravit`에 둔다(5-7)

### 4-3. `.claude/rules/project-structure.md`와 충돌하는 부분

| # | 기존 규칙 | 새 구조 | 처리 |
|---|---|---|---|
| 1 | 최상위 패키지 = 도메인, 그 아래 `controller/` 등 | `qa` 모듈은 `qa/{domain}/controller/`처럼 한 단계 깊다. 작은 모듈(`insight`)은 모듈 = 도메인 | 규칙에 "모듈 아래 도메인이 하나면 모듈 바로 아래, 여럿이면 `{module}/{domain}/`"을 추가 (D-9) |
| 2 | `security/` 패키지 설명 | `auth/` | 이름 변경 |
| 3 | `adapter/`에 figma, notion, github, openapi, llm | `gravit` 추가 | 목록 추가 |
| 4 | 처리기 위치 예시 `sprint/handler/SpecLoadJobHandler` | `qa/sprint/handler/` | 예시 경로 변경 |
| 5 | 데이터소스 규칙 없음 | 메인 DB 읽기 전용 조회 위치와 금지 사항(JPA 엔티티 금지, 쓰기 금지) | 새 절 추가 |
| 6 | 모듈 사이 의존 규칙 없음 | 4-5의 허용 의존 표 | 새 절 추가 |
| 7 | `adapter/`는 외부로 나가는 연동만 | `adapter/gravit`에 메인 전달용 수신 Controller 하나(D-3 (c)) | 예외로 명시 |

`.claude/CLAUDE.md`의 "1차 판정은 저장소 코드를 읽기만 한다 … gravit-server, gravit-web, gravit-aos에 쓰지 않는다"는 저장소 코드에 관한 규칙이다. 관리자 서버가 메인 API로 데이터를 바꾸는 것과 충돌하지 않지만, 읽는 사람이 헷갈리지 않게 D-9에서 문장을 나눈다.

### 4-4. 경계 강제 수단 비교

| 수단 | 강제 시점 | 도입 비용 | 지금 구조와 맞는가 | 되돌리기 |
|---|---|---|---|---|
| 리뷰만 | 사람이 볼 때 | 없음 | 맞음 | 해당 없음 |
| ArchUnit (테스트 의존성 `archunit-junit5`) | `./gradlew test` | 테스트 클래스 하나. 런타임 구성 요소 없음 | 맞음. 패키지 이름 기반 허용 목록으로 4-5 표를 그대로 옮길 수 있다 | 테스트 삭제 |
| Spring Modulith | 테스트(`ApplicationModules.verify()`) | 의존성, 모듈 루트 밖 하위 패키지를 내부로 보는 규칙 때문에 `auth.principal`, `adapter.*` 같은 공유 타입에 `@NamedInterface`나 `package-info.java`가 필요하다. 이벤트 저장소 같은 추가 개념이 따라온다 | 부분적으로. 지금 하위 패키지 구조(`controller/`, `service/`)를 모두 내부로 본다 | 의존성과 어노테이션 삭제 |
| Gradle 멀티모듈 | 컴파일 | `settings.gradle`, 모듈별 `build.gradle`, 테스트 공용 픽스처 분리, bootJar 구성. `qa` 안의 순환을 먼저 풀어야 한다 | 아니오. 도메인 하나씩 옮기는 동안 빌드 구조를 계속 바꿔야 한다 | 큰 되돌림 |

### 4-5. 선택과 허용 의존 표

ArchUnit을 고른다. 이관 기간에는 모듈이 하나씩 늘어나므로 규칙을 표 한 줄씩 늘리는 방식이 가장 싸고, 런타임에 아무것도 더하지 않아 P-4(새 기술 스택 금지)의 취지와도 맞는다. 컴파일 시점 강제가 필요해지면 그때 Gradle 멀티모듈을 다시 검토한다.

허용 의존(행이 열을 import할 수 있는가). O 허용, X 금지, △ 아래 조건부:

| from \ to | global | job | auth | adapter | qa | insight | support | member | content | audit |
|---|---|---|---|---|---|---|---|---|---|---|
| global | - | X | X | X | X | X | X | X | X | X |
| job | O | - | X | X | X | X | X | X | X | X |
| auth | O | X | - | X | X | X | X | X | X | X |
| adapter | O | △1 | X | - | X | X | X | X | X | X |
| qa | O | O | △2 | O | - | X | X | X | X | X |
| insight | O | O | △2 | O | X | - | △3 | △3 | △3 | X |
| support | O | O | △2 | O | X | X | - | X | X | △4 |
| member | O | O | △2 | O | X | X | X | - | X | △4 |
| content | O | O | △2 | O | X | X | X | X | - | △4 |
| audit | O | O | △2 | X | X | X | X | X | X | - |

- △1 `adapter → job`: `job.support.NonRetryableJobException` 하나만. 지금 있는 의존이다
- △2 `→ auth`: `auth.principal`(LoginAdmin)과 D-6의 요청 토큰 보관 타입만. 필터, 캐시는 금지
- △3 `insight → support, member, content`: 각 모듈의 `service/*QueryService` 공개 조회 메서드만. 대시보드 요약이 미처리 신고 수, 회원 수, 대기 라벨 수를 세기 때문이다. 다른 모듈의 Repository, 테이블 직접 조회는 금지
- △4 `→ audit`: `audit.service.AuditRecorder` 하나만(6단계부터). 감사 대상 행위를 하는 모듈이 기록을 맡긴다
- 도메인 모듈 사이 그 밖의 의존은 금지. `adapter`는 어떤 도메인 모듈도 참조하지 않는다

---

## 5. D-3 웹에서 관리자 서버로 가는 경로와 도메인 단위 전환

### 5-1. 현황과 근거

- 관리자 API는 절대 주소다. `API_V1 = ${env.VITE_API_HOST}/api/v1`, `apiClient` base `${API_V1}/admin`, 재발급 `authApiClient`(같은 호스트, `withCredentials: true`) - `[web] src/shared/api/client.ts:9-17`
- 401이면 `reissueAccessToken()`으로 한 번 재발급 후 1회 재시도, 재발급 실패면 강제 로그아웃. 재발급은 `reissueQueue`로 한 번만 날아간다 - `[web] src/shared/api/client.ts:41-85`, `[web] src/shared/api/reissueQueue.ts`
- refresh 토큰 쿠키는 Domain 속성 없이(호스트 전용) `Path=/api/v1/auth`, HttpOnly, Secure다 - `[main] src/main/java/gravit/code/auth/support/RefreshTokenCookieFactory.java:21-32`. 그래서 재발급 요청은 메인 호스트로 가야 한다
- QA는 별도 인스턴스 `qaApiClient`(base `VITE_QA_API_BASE`, 기본 `/qa-api`)가 같은 재발급 절차를 공유하고, 503은 재발급하지 않는다 - `[web] src/shared/api/qaClient.ts:7-10,55-57,74-101`
- Vercel rewrite는 `/ravvit-api`, `/qa-api`, SPA catch-all 순서다 - `[web] vercel.json`. 로컬은 vite proxy가 같은 경로를 재현한다 - `[web] vite.config.ts:17-35`
- MSW 관리자 핸들러는 호스트와 무관한 와일드카드 `*/api/v1/admin/...`를 쓴다 - `[web] src/mocks/handlers.ts:27-532`. QA 핸들러는 `VITE_QA_API_BASE`를 쓴다
- 메인 CORS는 관리자 웹 출처와 `http://localhost:5173`을 허용한다 - `[main] src/main/java/gravit/code/security/config/SecurityConfig.java:30-38`. qa-server는 CORS 설정이 없다(rewrite로 같은 출처가 되기 때문) - `[qa] src/main/java/gravit/qa/security/config/SecurityConfig.java`
- `apiClient`를 쓰는 웹 파일은 `src/features/{auth,chapters,dashboard,inquiries,lessons,notices,problems,reports,staging,units,users}/api.ts` 11개다
- qa-server는 dev push마다 컨테이너 하나를 `--force-recreate`로 다시 올린다. 그동안 그 컨테이너가 받는 요청은 실패한다 - `[qa] .github/workflows/cd.yml:3-5,113-125`

### 5-2. 대안

- (a) 웹에 관리자 서버 전용 axios 인스턴스(`adminServerClient`)를 하나 두고, 이관한 도메인의 `features/*/api.ts`만 그 인스턴스로 바꾼다
- (b) `apiClient` base를 상대 경로(`/api/v1/admin`)로 바꾸고 Vercel rewrite를 도메인 경로별로 메인과 관리자 서버로 나눈다
- (c) 웹의 관리자 API를 한 번에 관리자 서버로 돌리고, 관리자 서버가 아직 이관하지 않은 도메인 요청을 메인으로 넘긴다

### 5-3. 비교

| 기준 | (a) | (b) | (c) |
|---|---|---|---|
| 웹 변경 횟수 | 준비 1회(인스턴스, rewrite, proxy) + 도메인마다 `api.ts` import 1~2줄 = 7회 | 준비 1회 + 도메인마다 `vercel.json`과 `vite.config.ts` 두 곳 = 7회 | 1회 |
| 도메인 단위 롤백 | 그 도메인 import를 되돌려 웹 배포(빌드 수 분). 다른 웹 변경이 없으면 Vercel 직전 배포로 즉시 | rewrite 규칙을 되돌려 웹 배포. 소요는 (a)와 같다 | 관리자 서버 설정에서 그 도메인을 메인 전달로 돌리고 재기동(헬스 확인까지 수 분, 그동안 관리자 기능 전체 중단) |
| 요청 경유 | 이관 도메인: 웹 → Vercel → 관리자(API 방식은 → 메인). 미이관: 지금과 같다(웹 → 메인) | 모든 관리자 요청이 Vercel을 거친다. 미이관도 1단계 늘어난다 | 미이관: 웹 → Vercel → 관리자 → 메인. 이관 기간 내내 늘어난다 |
| 관리자 서버 장애, 배포 중 영향 | 이관한 도메인과 QA만 | 이관한 도메인과 QA만 | 관리자 기능 전체. dev push마다 생기는 재기동도 전체에 영향 |
| 재발급 흐름 | 새 인스턴스가 `reissueAccessToken()`을 공유한다(qaClient 선례). `authApiClient`는 그대로 | `apiClient`만 상대 경로, `authApiClient`는 반드시 절대 주소로 남겨야 한다(쿠키가 메인 호스트 전용) | `apiClient` base만 바꾸고 `authApiClient`는 그대로 |
| 로컬 개발 | vite proxy 1개 추가. MSW는 rewrite 경로에 `/api/v1/admin` 꼬리를 남기면 와일드카드가 그대로 맞는다 | `vercel.json` 규칙을 vite proxy에 복제해야 하고 두 곳이 어긋날 수 있다. MSW 그대로 | vite proxy 1개. MSW 그대로. 실서버로 붙으면 로컬에서도 관리자 서버가 떠 있어야 한다 |
| 관리자 서버에 새로 생기는 코드 | API 방식 16개를 메인으로 넘기는 전달 코드 | (a)와 같음 | `/api/v1/admin/**` 범용 전달 계층 하나. 이관한 도메인은 더 구체적인 Controller 매핑이 먼저 잡혀 자동으로 빠진다. API 방식 16개도 이 계층을 그대로 쓰므로 이관이 끝나도 남는다(처음 판단의 "임시 코드"는 틀렸다. 5-6) |

### 5-4. 처음 선택 - (a) (5-6 재검토 뒤 (c)로 바뀜)

이관 기간 동안 관리자 서버 장애와 배포 재기동이 아직 옮기지 않은 기능까지 멈추지 않는 것을 가장 크게 봤다. (c)는 웹 변경이 1회지만 첫날부터 관리자 서버가 관리자 기능 전체의 단일 장애 지점이 되고, 임시 범용 전달 계층을 만들고 검증해야 한다. (b)는 미이관 요청까지 Vercel을 거치게 하고 라우팅 표를 두 곳에 둔다.
웹 변경 횟수는 (a)가 많지만 한 번이 `api.ts` import 교체라 작고, 웹에 이미 같은 구조(QA 전용 인스턴스 + 공유 재발급)가 있다.

웹에 들어갈 구성(웹 저장소 작업, 0단계에 한 번):

| 파일 | 내용 |
|---|---|
| `[web] vercel.json` | SPA catch-all 앞에 `{ "source": "/admin-server/api/v1/admin/:path*", "destination": "https://<관리자 도메인>/api/v1/admin/:path*" }` |
| `[web] src/env.ts` | `VITE_ADMIN_SERVER_BASE`, 기본값 `/admin-server/api/v1/admin`(필수 아님, `VITE_QA_API_BASE`와 같은 방식) |
| `[web] src/shared/api/client.ts` | `apiClient`의 요청, 응답 인터셉터를 함수로 빼서 두 인스턴스가 같이 쓰게 한다(재발급 절차 복제 금지, D8-3) |
| `[web] src/shared/api/adminServerClient.ts` | base `VITE_ADMIN_SERVER_BASE`, 위 인터셉터 부착 |
| `[web] vite.config.ts` | `/admin-server` proxy → `VITE_ADMIN_SERVER_ORIGIN`(기본 관리자 도메인), `/admin-server` 접두 제거. 테스트 env에 기본값 |
| `[web] src/mocks/handlers.ts` | 바꾸지 않는다. rewrite 경로가 `/api/v1/admin` 꼬리를 유지해 `*/api/v1/admin/...`가 그대로 맞는다 |

도메인 전환은 그 도메인의 `features/*/api.ts`에서 `apiClient`를 `adminServerClient`로 바꾸는 것뿐이다. 관리자 서버는 rewrite 덕분에 같은 출처로 받으므로 CORS를 추가하지 않는다.
(c)는 웹 배포가 이관의 병목이 되면 다시 검토한다.

### 5-5. 되돌리는 방법 ((a) 기준, 참고)

- 도메인 하나: 그 도메인 `api.ts`의 import를 `apiClient`로 되돌리고 웹을 배포한다. 메인의 옛 코드가 남아 있는 동안(D-8 4단계 전)만 가능하다
- 전체: `adminServerClient`를 쓰는 곳을 모두 되돌린 뒤 rewrite와 인스턴스를 지운다
- (c)의 되돌리는 방법은 5-7

### 5-6. 재검토 (2026-11-18, 사용자 질문)

질문: 관리자 웹의 호출을 모두 관리자 서버로 돌리고, 관리자 서버가 메인 API를 불러 받은 응답을 돌려주는 방식은 안 되는가. 이것은 (c)다. 안 되는 방식은 아니고, 처음 비교에서 두 가지가 바뀌었다.

1. 정정: (c)의 범용 전달 계층을 "이관이 끝나면 지울 임시 코드"로 봤지만, API 방식 16개(D-5)는 끝까지 메인으로 넘겨야 하므로 같은 계층이 계속 쓰인다. (a)도 이 16개의 전달 코드가 필요하다. 코드량의 차이는 거의 없다
2. 새 조건: D-10에서 인스턴스를 하나로 하고 main 머지마다 배포하기로 했다. 이관을 마치면 (a)든 (c)든 관리자 기능 전체가 이 인스턴스 하나를 지난다. 두 방식의 차이는 "이관 기간 동안" 관리자 서버 장애와 재배포 재기동이 미이관 기능까지 멈추는지뿐이다

| 기준 | (a) | (c) |
|---|---|---|
| 웹 변경 | 준비 1 + 도메인 6 | 1 |
| 도메인 전환, 롤백 | 웹 배포 | 관리자 서버 배포(이관 Controller 추가, 삭제). 롤백은 그 커밋 revert 후 배포 |
| 이관 기간 장애 범위 | 이관한 도메인 + QA | 관리자 기능 전체(이관을 마친 뒤 모습을 처음부터 갖는다) |
| 재배포 재기동 영향 | 이관한 도메인 + QA | 관리자 기능 전체. CD는 컨테이너 하나를 `--force-recreate`로 다시 올리고 healthy를 최대 180초 기다린다(`[qa] .github/workflows/cd.yml:113-125`) |
| 전달 계층의 첫 검증 | AUTH-01, RPT-03부터 조금씩 | 첫날부터 43개 전부. 전달 계층 결함은 전체에 바로 드러난다 |
| 웹 쪽 MSW, 재발급 | 영향 없음 | 영향 없음(`apiClient` base만 바꾸고 `authApiClient`는 메인 그대로) |

(c)를 고르는 조건은 "이관 기간 동안 관리자 서버 재배포, 장애 때 관리자 기능 전체가 멈추는 것을 받아들인다"이다. 내부 운영 도구라 이 조건을 받아들이면 (c)가 웹 변경과 전환 절차가 더 단순하다.
사용자가 이 조건을 받아들여 (c)로 확정했다(2026-11-18).

### 5-7. (c) 구성

웹(0단계에 한 번):

| 파일 | 내용 |
|---|---|
| `[web] vercel.json` | SPA catch-all 앞에 `{ "source": "/admin-server/api/v1/admin/:path*", "destination": "https://<관리자 도메인>/api/v1/admin/:path*" }` |
| `[web] src/shared/api/client.ts` | `apiClient` base만 `/admin-server/api/v1/admin`로(환경 변수로 빼면 `VITE_ADMIN_SERVER_BASE`, 기본값 같은 값). `authApiClient`는 메인 절대 주소 그대로(재발급 쿠키가 메인 호스트 전용) |
| `[web] vite.config.ts` | `/admin-server` proxy → 관리자 도메인(로컬 관리자 서버면 덮어쓰기), 접두 제거 |
| `[web] src/mocks/handlers.ts` | 변경 없음. `*/api/v1/admin/...` 와일드카드가 새 경로에도 맞는다 |

관리자 서버:

- 전달 계층: `/api/v1/admin/**` 전부를 받는 Controller 하나가 메서드, 경로, 쿼리, 본문, `Authorization`, `Content-Type`을 메인에 그대로 보내고 응답 상태, `Content-Type`, 본문 바이트를 그대로 돌려준다(D-5 7-4, 7-5). 위치는 `adapter/gravit`(메인 경계 전용이라 adapter 안의 유일한 수신 지점으로 둔다, 4-3에 추가)
- 이관한 도메인은 더 구체적인 Controller 매핑이 먼저 잡혀 전달 계층을 거치지 않는다. 같은 경로의 다른 메서드(예: NTC-02 GET만 옮기고 NTC-04 PATCH는 메인)는 전달 계층으로 가야 하므로 이 동작을 0단계 테스트로 확인한다
- 검증 중 헤더 조건: 새 도메인 Controller는 처음에 `headers = "X-Admin-Route=local"` 조건을 달고 배포한다. 이 헤더가 있는 요청만 새 구현으로 가고, 웹 요청(헤더 없음)은 전달 계층으로 메인에 간다. 계약 테스트는 헤더를 붙여 관리자 서버 새 구현과 prod 메인을 비교한다. 전환은 헤더 조건을 지우는 배포다
- 전달 요청도 관리자 서버 필터의 판정(캐시)을 거친 뒤 메인이 다시 판정한다(D-6). 판정 실패는 관리자 서버가 답하고 메인 401, 403은 그대로 넘기며 캐시를 지운다

되돌리는 방법:

| 시점 | 방법 | 소요 |
|---|---|---|
| 0단계 웹 변경 직후(전달 계층 문제) | Vercel에서 직전 배포로 롤백하거나 `apiClient` base를 메인으로 되돌려 웹 배포 | 즉시 ~ 웹 빌드 수 분 |
| 도메인 전환 뒤, 메인 옛 코드 삭제 전 | 그 도메인 Controller에 헤더 조건을 다시 걸거나 전환 커밋 revert 후 관리자 서버 배포. 요청은 다시 메인으로 간다 | 관리자 서버 배포(재기동 동안 관리자 기능 전체 중단) |
| 메인 옛 코드 삭제 뒤 | 메인 삭제 PR revert → 메인 릴리스 → 위 방법 | 메인 릴리스 주기 |

---

## 6. D-4 데이터 접근

### 6-1. (i) 조회 방식 기능의 메인 DB 접근

#### 현황과 근거

- 메인 admin Service는 메인 도메인 Repository를 직접 쓴다. 예: `AdminDashboardService`는 `AdminUserRepository`, `StagingLabelRepository`, `AdminReportRepository`, `AdminActiveUserRepository` - `[main] src/main/java/gravit/code/admin/service/AdminDashboardService.java:34-37`
- 메인 운영 프로필에는 Hikari 설정이 없어 기본 최대 풀 10이다 - `[main] src/main/resources/application-prod.yml:1-6`. 성능 측정 프로필만 60 - `[main] src/main/resources/application-perf.yml:13-17`
- 메인 dev와 prod는 DB URL만 다르고 계정 시크릿 이름이 같다 - `[main] .github/workflows/cd-dev.yml:30-36`, `[main] .github/workflows/cd-prod.yml:46-52`. 같은 인스턴스인지는 확인 필요
- QA DB는 메인 dev가 쓰는 Postgres 인스턴스에 있다 - `[qa] docs/deploy-runbook.md` 1단계. qa-server 데이터소스는 하나이고 JPA `ddl-auto: validate`다 - `[qa] src/main/resources/application.yml:4-12`. 풀 설정이 없어 기본 10이다
- Postgres `max_connections`와 현재 사용량은 확인 필요

#### 대안

1. 메인 DB 읽기 전용 계정 + 관리자 서버 두 번째 데이터소스(JdbcTemplate)
2. 메인의 기존 관리자 조회 API(`GET /api/v1/admin/**`) 호출
3. 메인에 내부 조회 API를 새로 만들어 호출
(읽기 복제본은 새 인프라라 P-4에 따라 넣지 않는다)

#### 비교

| 기준 | 1 | 2 | 3 |
|---|---|---|---|
| 메인 옛 코드 삭제 | 가능 | 불가(조회 API가 메인에 남는다. 사실상 API 방식) | 불가(새 코드가 생긴다) |
| 메인 스키마 결합 | 테이블, 컬럼에 결합. 계약 테스트와 메인 쪽 규칙으로 막는다 | 없음 | 없음 |
| 메인 DB 커넥션 | 인스턴스에 최대 3개 추가. 메인 Hikari 풀(10)과는 별개 풀이라 메인 풀 사용량은 그만큼 준다(같은 조회가 옮겨 가므로) | 메인 풀을 그대로 쓴다 | 메인 풀을 그대로 쓴다 |
| 메인 장애 영향 | 메인 앱이 죽어도 DB가 살아 있으면 조회는 된다 | 메인 앱이 죽으면 조회도 실패 | 같음 |
| 구현량 | 관리자 서버에 SQL과 DTO | 관리자 서버는 전달만 | 메인과 관리자 양쪽 |

#### 선택 - 1

조회 방식으로 분류한 이유(메인 코드를 지우고 관리자 서버로 옮기는 것)를 지킬 수 있는 대안은 1뿐이다. 스키마 결합은 D-7 계약 테스트와 "관리자 조회 대상 테이블을 바꾸는 메인 PR은 관리자 서버 확인"이라는 메인 쪽 규칙(메인 레포 작업, 미결 M-6)으로 관리한다.

구성:

- 계정: `gravit_admin_ro` (이름은 제안). 운영자가 아래를 실행한다. 비밀번호는 `\password`로만 넣는다(`[qa] docs/deploy-runbook.md` 1단계와 같은 규칙)

  ```sql
  CREATE ROLE gravit_admin_ro LOGIN CONNECTION LIMIT 5;
  \password gravit_admin_ro
  ALTER ROLE gravit_admin_ro SET default_transaction_read_only = on;
  ALTER ROLE gravit_admin_ro SET statement_timeout = '5s';
  GRANT CONNECT ON DATABASE <메인 DB> TO gravit_admin_ro;
  GRANT USAGE ON SCHEMA public TO gravit_admin_ro;
  GRANT SELECT ON users, staging_label, report, user_daily_activity TO gravit_admin_ro;
  ```

  GRANT는 도메인 단계마다 그 단계의 테이블만 추가한다(1단계는 위 4개, 2단계 `report`는 이미 있음, 3단계 `notice`, `inquiry`, `inquiry_answer`, 5단계 `chapter`, `unit`, `lesson`, `problem`, `option`, `answer`, `lesson_submission`)
- 관리자 서버: `global/config/MainDbConfig`(DataSource, `@MainDb JdbcTemplate`), `MainDbProps`(`qa.main-db.url`, `username`, `password`, `maximum-pool-size: 3`, `minimum-idle: 1`, `read-only: true`). JPA는 기존 QA 데이터소스에만 묶어 둔다
- 기존 QA 데이터소스도 최대 풀을 명시한다(추천 5). 같은 인스턴스에 관리자 데이터가 늘어나면 기본 10이 인스턴스 연결 예산을 넉넉히 쓴다

옮길 때 그대로 재현해야 하는 메인의 숨은 동작(1단계 점검 항목):

- 공지 엔티티의 `@SQLRestriction("deleted_at IS NULL")` - `[main] src/main/java/gravit/code/notice/domain/Notice.java:40-41`. SQL에 직접 넣어야 한다
- 공지 관리자 목록 정렬 `pinned desc, publishedAt desc nulls last, createdAt desc` - `[main] src/main/java/gravit/code/notice/repository/NoticeRepository.java:37-42`. id로 동점을 가르지 않아 같은 시각 행은 페이지 경계에서 순서가 흔들릴 수 있다. 계약 테스트는 이 경우를 따로 다룬다
- 페이지 크기 20, `page < 1`이면 400 `PAGE_4001` - `[main] src/main/java/gravit/code/admin/support/AdminPages.java`
- 목록별 정렬: 회원 `id DESC`, 신고 `id DESC`, 문의 `id DESC`, 스테이징 `createdAt DESC, id DESC`
- 회원 검색어는 공백이면 null, 앞뒤 공백 제거, email, nickname, handle `ILIKE` - `[main] src/main/java/gravit/code/admin/service/AdminUserService.java:39`, `[main] src/main/java/gravit/code/admin/repository/AdminUserRepository.java:16-41`

#### 되돌리는 방법

그 모듈의 조회 구현만 메인 API 호출로 바꾸거나(대안 2), D-3 롤백으로 웹을 메인으로 되돌린다. 계정은 `REVOKE`, `DROP ROLE`로 지운다.

### 6-2. (ii) 소유 방식 테이블의 위치

#### 현황과 근거

- 스테이징 테이블 4개는 id를 받아 쓰고(identity 아님) 라벨 문자열로 묶인다 - `[main] src/main/resources/db/migration/V4__add_learning_content_staging_tables.sql`, `V21__add_staging_label.sql`
- 스테이징의 첫 적재는 메인 밖의 generator가 메인 DB에 직접 INSERT한다. `status`, `created_at`은 DB 기본값에 기댄다 - `[main] src/main/resources/db/migration/V21__add_staging_label.sql:1-3`. generator 저장소는 이번 읽기 범위 밖이라 확인 필요
- 감사 로그는 메인 Service가 업무 변경과 같은 트랜잭션에서 기록한다 - `[main] src/main/java/gravit/code/admin/support/AuditLogRecorder.java`, 호출 `AdminUserService.java:75,91`, `AdminNoticeService.java:97,113`, `AdminStagingPromoteService.java:120`. 테이블 - `[main] src/main/resources/db/migration/V22__add_audit_log.sql`
- 감사 대상 행위 5가지는 모두 API 방식 기능(USER-03, USER-04, NTC-04, NTC-05, STG-07)이라 실제 변경은 메인에서 일어난다
- 대시보드 요약이 대기 라벨 수를 센다 - `[main] src/main/java/gravit/code/admin/service/AdminDashboardService.java:44`
- 메인 Flyway: 기동 시 적용(`[main] src/main/resources/application-prod.yml:53-60`), 빌드 때 H2 메모리 DB에서 파일 마이그레이션의 `flywayValidate`(`[main] build.gradle:103-123`). 적용된 V4, V21, V22 파일은 메인에서 지울 수 없다(지우면 기동 검증이 "적용됐지만 로컬에 없음"으로 실패)

#### 대안

- A. 관리자 DB로 옮긴다
- B. 메인 DB에 두고 쓰기 주체만 관리자 서버로 바꾼다(관리자 서버에 그 테이블만 쓸 수 있는 계정)
- C. 스테이징은 A, 감사 로그는 메인에서 계속 기록(감사 로그 소유를 포기)

#### 비교

| 기준 | A | B | C |
|---|---|---|---|
| 소유의 실질 | 스키마, 데이터, 쓰기 모두 관리자 서버 | 데이터 쓰기만. 스키마 변경은 메인 PR과 배포가 필요 | 스테이징은 A, 감사는 확정 결정("소유")과 어긋남 |
| Flyway | 관리자 쪽 새 마이그레이션. 메인은 보류 뒤 DROP 마이그레이션 추가(V4, V21, V22 파일은 남김) | 메인이 계속 소유 | 섞임 |
| generator | 적재 대상 DB를 바꿔야 한다(외부 변경) | 그대로 | A와 같음 |
| STG-07 원자성 | DB가 나뉜다. 메인 레슨 생성 + 관리자 라벨 완료를 멱등 키로 묶어야 한다 | 지금처럼 메인 한 트랜잭션 | A와 같음 |
| 감사 원자성 | 메인 변경과 감사 기록이 다른 DB. 시도 기록 → 결과 갱신으로 보완 | 메인이 기록하면 원자적이지만 "쓰기 주체를 관리자로"와 어긋남 | 원자적 |
| 계정, 연결 | 관리자 DB만 | 메인 DB 쓰기 계정이 하나 더 생긴다 | A와 같음 |
| 이관 뒤 메인에 남는 코드 | 레슨 일괄 생성 API 하나 | 승격 Service, 스테이징 엔티티 전부 | A와 같음 |

#### 선택 - A (확정, 2026-11-18)

A로 확정했다. 소유로 분류한 취지(관리자 서버가 데이터를 소유한다)를 스키마까지 지키고, 이관이 끝나면 메인에 스테이징 코드가 남지 않는다. 처음 사람 결정으로 남긴 세 가지는 이렇게 정리됐다.

1. generator 적재 대상 변경: A를 고른 만큼 바꾸는 쪽으로 진행한다. 실제로 바꿀 수 있는지는 generator 저장소를 확인해야 한다(M-3). 바꿀 수 없으면 그때 B를 다시 본다
2. 관리자 DB 위치: 관리자 서버만 참조하는 데이터라 prod DB에 둘 필요가 없다는 사용자 판단에 따라 지금 QA DB(`gravit_qa`, 메인 dev가 쓰는 Postgres 인스턴스, `[qa] docs/deploy-runbook.md` 1단계)를 그대로 관리자 DB로 쓴다. 승격 전 콘텐츠와 prod 행위의 감사 로그가 이 DB에 쌓이므로 그 인스턴스의 백업 여부를 확인한다(M-11)
3. 감사 기록 원자성: A를 고르면서 받아들였다. 아래의 시도 기록 → 결과 갱신으로 보완한다

A일 때 감사 로그 설계(6단계):

- 관리자 DB `audit_log`에 결과 칸(`result`: `PENDING`, `SUCCEEDED`, `FAILED`, `http_status`)을 더한다
- 감사 대상 API를 메인에 보내기 전에 `PENDING`으로 기록하고, 응답을 받으면 결과를 갱신한다. 관리자 서버가 그 사이에 멈추면 `PENDING`으로 남아 "시도했고 결과 미확인"이 기록된다. 기록이 아예 빠지는 것보다 낫다
- 변경 전 값(회원 상태, 권한, 공지 상태)은 메인 읽기 전용 연결로 호출 직전에 읽는다
- 6단계 전까지는 메인이 지금처럼 같은 트랜잭션에서 기록한다. 6단계에서 메인의 `AuditLogRecorder` 호출을 지우고 기존 행을 관리자 DB로 복사한다

A일 때 스테이징 이전 절차(5단계):

1. 관리자 DB 마이그레이션으로 스테이징 테이블 4 + 라벨 테이블을 만든다. PK는 `[qa] .claude/rules/migration.md` 규칙대로 `GENERATED BY DEFAULT AS IDENTITY`로 두되(명시적 id INSERT 허용), 복사 뒤 시퀀스를 `setval(max(id))`로 맞춘다. 라벨 FK, CHECK, 기본값(`status`, `created_at`)은 V4, V21과 같게 둔다
2. 관리자 서버에 STG-01~07을 구현해 배포한다. 웹은 아직 메인을 본다
3. 쓰기 멈춤: generator를 멈추고 스테이징 편집 중지를 공지한다. 행 수가 작아(라벨당 레슨 1, 문제 6, 옵션과 정답 약 10) 멈춤 시간은 짧다
4. 복사: 메인에서 `COPY ... TO STDOUT`, 관리자 DB로 `COPY ... FROM STDIN`. 라벨별 행 수와 정렬한 행의 해시를 양쪽에서 비교한다
5. 웹 STG 전환(D-3)
6. 메인에서 STG-01~06 쓰기 엔드포인트를 지운 배포를 한다(옛 번들을 연 브라우저가 메인 사본을 고치지 못하게)
7. generator 적재 대상을 관리자 DB로 바꾸고 다시 켠다
8. 보류 기간 뒤 백업 덤프를 남기고 메인에 `V5x__drop_learning_content_staging.sql`을 추가한다(H2 PostgreSQL 모드에서도 도는 단순한 DROP만)

B일 때는 관리자 서버에 `staging_*`, `staging_label`, `audit_log`만 SELECT, INSERT, UPDATE할 수 있는 메인 DB 계정을 따로 두고, 메인은 승격과 감사 기록을 지금처럼 한다.

#### 되돌리는 방법

- A, 메인 DROP 전: 전환 뒤 관리자 DB에서 바뀐 라벨을 메인으로 역복사(같은 COPY 절차의 반대 방향)하고 웹을 메인으로 되돌린 뒤 generator 대상을 되돌린다
- A, 메인 DROP 뒤: 백업 덤프로 메인 테이블을 복원하고 위와 같이 한다
- B: 쓰기 계정을 지우고 웹을 메인으로 되돌린다

### 6-3. (iii) STG-07 승격 흐름

지금은 메인 한 트랜잭션에서 라벨 확인 → 구조 검증(문제 6, 객관식 4와 주관식 2, 객관식 옵션 4와 정답 1, 주관식 정답 1) → 레슨, 문제, 옵션, 정답 INSERT → 라벨 완료 → 감사 기록을 한다 - `[main] src/main/java/gravit/code/admin/service/AdminStagingPromoteService.java:63-176`.

| 스테이징 위치 | 웹 → 관리자 | 관리자 → 메인 | 메인이 하는 일 | 실패 처리 |
|---|---|---|---|---|
| A 관리자 DB | `PATCH /staging/labels/{label}/status {status:"COMPLETED"}` (경로 그대로) | 관리자가 라벨을 읽고 같은 구조 검증을 한 뒤 메인 신규 API `POST /api/v1/admin/lessons/promotions` 호출. 본문 `{promotionKey: label, unitId, title, problems:[{problemType, instruction, content, options:[{content, explanation, isAnswer}], answer:{content, explanation}}]}` | 한 트랜잭션에서 레슨, 문제, 옵션, 정답 INSERT. `promotionKey` UNIQUE(메인 새 테이블 또는 칸)로 같은 키 재요청은 새로 만들지 않고 기존 `lessonId`를 돌려준다 | 메인 응답이 없거나 실패하면 라벨은 `PENDING`으로 남는다. 사람이 다시 누르면 멱등 키 덕분에 중복 레슨이 생기지 않고, 성공 응답을 받은 뒤 관리자가 라벨을 `COMPLETED`로 바꾸고 감사를 기록한다 |
| B 메인 DB | 같음 | 기존 `PATCH /api/v1/admin/staging/labels/{label}/status`를 그대로 전달 | 지금과 같다 | 이미 완료된 라벨은 409 `STAGING_4091`이라 재요청해도 중복이 없다 |

A는 메인에 새 엔드포인트와 마이그레이션(멱등 키)이 필요하다. 이 엔드포인트는 메인 쪽 "레슨 일괄 생성"이라 승격 말고도 쓸 수 있는 메인 도메인 API다.

확정(2026-11-18): A의 흐름으로 간다. 승격 전 데이터는 관리자 DB가 관리하고, 승격할 때 메인(prod 서버)에 둔 학습 콘텐츠 생성 API를 호출한다.
생성 범위는 지금 스테이징 모델을 따른다. 라벨 하나는 이미 있는 유닛(`staging_label.unit_id`, `lesson_staging.unit_id`) 아래의 레슨 1개, 문제 6개와 그 옵션, 정답이다(V4, V21). 챕터와 유닛은 승격으로 만들지 않는다. 챕터, 유닛까지 만들려면 generator 출력과 스테이징 스키마에 챕터, 유닛 단위가 생겨야 하므로 별도 범위로 다룬다.

---

## 7. D-5 메인 서버 API 계약

### 7-1. 메인에 남는 엔드포인트

| ID | 메서드, 경로 (`/api/v1/admin` 뒤) | 부작용 | 같은 요청을 두 번 보내면 | 자동 재시도 |
|---|---|---|---|---|
| AUTH-01 | GET `/me` | 없음 | 같은 결과 | 연결 실패일 때만 1회 |
| USER-03 | PATCH `/users/{userId}/status` | 감사 기록, 복구 시 새 handle | 상태는 같고 감사 행이 하나 더, 복구면 handle이 다시 바뀐다 | 없음 |
| USER-04 | PATCH `/users/{userId}/role` | 감사 기록 | 감사 행이 하나 더 | 없음 |
| CON-04 | PATCH `/chapters/{chapterId}` | 없음 | 같은 결과 | 없음 |
| CON-07 | PATCH `/units/{unitId}` | 없음 | 같은 결과 | 없음 |
| CON-10 | PATCH `/lessons/{lessonId}` | 없음 | 같은 결과 | 없음 |
| CON-13 | PATCH `/problems/{problemId}/objective` | 없음 | 같은 결과 | 없음 |
| CON-14 | PATCH `/problems/{problemId}/subjective` | 없음 | 같은 결과 | 없음 |
| STG-07 | A: POST `/lessons/promotions` (신규), B: PATCH `/staging/labels/{label}/status` | 레슨 생성, 감사 | A: 멱등 키로 같은 결과, B: 409 | 없음 |
| NTC-03 | POST `/notices` | `PUBLISHED`면 푸시 | 공지와 푸시가 하나 더 생긴다 | 없음 |
| NTC-04 | PATCH `/notices/{noticeId}` | 상태 변경 시 감사 | 같은 상태면 감사 없음 | 없음 |
| NTC-05 | DELETE `/notices/{noticeId}` | soft delete, 감사 | 404 `NOTICE_4041` | 없음 |
| RPT-03 | PATCH `/reports/{reportId}/status` | 없음 | 같은 결과 | 없음 |
| INQ-03 | POST `/inquiries/{inquiryId}/answer` | 문의 해결, 푸시 | 409 `INQUIRY_4092` (`inquiry_answer.inquiry_id` UNIQUE, `[main] src/main/resources/db/migration/V23__add_inquiry.sql:20-25`) | 없음 |
| INQ-04 | PUT `/inquiries/{inquiryId}/answer` | 없음 | 같은 결과 | 없음 |
| INQ-05 | DELETE `/inquiries/{inquiryId}/answer` | 문의 다시 열기 | 404 `INQUIRY_4042` | 없음 |

푸시는 메인 `NotificationEventListener`가 커밋 뒤(`AFTER_COMMIT`)에 보낸다 - `[main] src/main/java/gravit/code/notification/listener/NotificationEventListener.java:30-66`. 관리자 서버가 메인 API를 부르면 이 부작용은 메인에 그대로 남는다.

### 7-2. 경로 체계

| 기준 | 1. 기존 `/api/v1/admin/**` 그대로 호출 | 2. 메인에 `/internal/v1/admin/**` 신설 |
|---|---|---|
| 메인 변경 | 없음(STG-07 A의 신규 하나만) | Controller, 보안 설정 복제 |
| 응답 동일성 | 웹이 쓰던 것과 같은 엔드포인트라 자동으로 같다 | 새 엔드포인트를 따로 맞춰야 한다 |
| 인증 | P-7 그대로(메인 기존 판정) | 같음 |
| 메인 공개 경로 노출 | 지금처럼 남는다 | 기존 경로를 지우면 줄어든다 |

1을 고른다. 이관이 끝난 뒤 메인 공개 도메인에서 관리자 쓰기 엔드포인트를 막을지는 미결 M-5로 둔다(지금도 같은 상태라 새 위험은 아니다).

### 7-3. 네트워크 경로 - 공개 도메인 (확정, 2026-11-18)

현황: qa-server는 `gravit-qa`(실제 `qa_gravit-qa`)와 `llm-net`에 있다 - `[qa] docker-compose-qa.yml:22-24`. 메인 prod는 `prod_gravit-prod`, `llm-net` - `[main] docker-compose-prod.yml:21-23,91-99`, 메인 dev는 `dev_gravit-dev`, `llm-net` - `[main] docker-compose-dev.yml:21-23,87-95`. 판정 호출은 지금 공개 도메인을 쓴다(`QA_AUTH_HOST=https://grav-it-dev.inuappcenter.kr`, `[qa] .env.example`).

| 기준 | 공개 도메인(`https://grav-it.inuappcenter.kr`) | `llm-net`으로 컨테이너 직접(`http://gravit-server-prod:8080`) | `prod_gravit-prod` 합류 | 전용 네트워크 신설 |
|---|---|---|---|---|
| 운영 작업 | 없음 | 없음(이미 둘 다 붙어 있다) | compose 변경 | 양쪽 compose 변경, 메인 레포 PR |
| 노출 범위 | 그대로 | 그대로 | 관리자 서버가 prod Redis, Loki에 닿는다 | 최소 |
| 결합 | DNS, Caddy | `llm-net`의 목적(LLM 게이트웨이)과 다른 용도로 기대게 되고 메인 컨테이너 이름에 묶인다 | 메인 내부 네트워크 이름에 묶인다 | 새 네트워크 이름 |
| 통신 | TLS, Caddy를 거친다 | 평문, 같은 호스트 안 | 평문 | 평문 |

차이가 작아 사람 결정으로 뒀고, 추천대로 공개 도메인으로 확정했다. 운영 작업과 메인 레포 변경이 없고, 판정 호출과 같은 호스트 설정 하나(`qa.auth.host`, 이름은 나중에 `admin.main-host`로 바꿀 수 있다)를 함께 쓴다. 나중에 내부 경로로 바꾸는 것은 설정값 변경이다.

### 7-4. 타임아웃과 재시도

- 연결 2초, 응답 10초. 웹 axios 타임아웃이 30초라(`[web] src/shared/api/client.ts:10`) 관리자 서버가 먼저 끊고 제 오류를 돌려줄 수 있다. 판정 호출은 지금 값(5초, `[qa] src/main/resources/application.yml:36-39`)을 유지한다
- 쓰기(POST, PUT, PATCH, DELETE)는 자동 재시도하지 않는다. 메인에 요청이 닿았는지 모르는 상태에서 다시 보내면 NTC-03은 공지와 푸시가 두 번 생긴다
- 조회형 API(AUTH-01)만 연결 실패(요청이 나가지 않은 경우)일 때 1회 재시도한다
- 쓰기의 응답 타임아웃은 "처리 여부를 모른다"로 돌려준다(아래 504). 사람이 목록을 다시 불러 확인한다
- 멱등 키 헤더는 메인이 지원하지 않아 지금은 쓰지 않는다. NTC-03 중복은 웹의 제출 중 버튼 잠금에 기댄다(미결 M-4)

### 7-5. 메인 응답 전달

- 메인 응답의 상태 코드, `Content-Type`, 본문 바이트를 그대로 웹에 돌려준다. 역직렬화 후 다시 직렬화하지 않는다(날짜, null, 필드 순서가 바뀌지 않게)
- 요청 헤더는 `Authorization`, `Content-Type`, `Accept`만 넘긴다. 쿠키, `Host`는 넘기지 않는다
- 메인 오류 본문(`{error, message}`)도 그대로 넘긴다. 401, 403이면 D-6에 따라 판정 캐시를 지운 뒤 넘긴다
- 메인에 닿지 못한 경우는 관리자 서버 오류 코드를 쓴다

| 상황 | 상태 | `error` | `message` |
|---|---|---|---|
| 연결 실패 | 502 | `GRAVIT_5021` | 메인 서버에 연결하지 못했습니다. 잠시 뒤 다시 시도하세요. |
| 응답 타임아웃 | 504 | `GRAVIT_5041` | 메인 서버 응답이 늦어 처리 여부를 확인하지 못했습니다. 목록을 다시 불러와 확인하세요. |

503은 쓰지 않는다. 웹이 503을 "인증 서버 장애"로 다루기 때문이다(D8-3, `[web] src/shared/api/qaClient.ts:55-57`). 웹 관리자 클라이언트는 502, 504를 재발급 사유로 보지 않고 서버 `message`를 그대로 보여 준다 - `[web] src/shared/api/errorHandler.ts:20-31`.

---

## 8. D-6 인증 전달 (P-7 구체화)

### 8-1. 현황과 근거

- qa-server 필터: `Bearer` 토큰 추출 → `AdminVerifier`(캐시 확인, 없으면 `/api/v1/admin/me` 호출 후 성공만 캐시) → `ROLE_ADMIN` 인증. 자격 증명 칸은 비워 둔다 - `[qa] src/main/java/gravit/qa/security/filter/AdminTokenAuthenticationFilter.java:39-75`, `[qa] src/main/java/gravit/qa/security/support/AdminVerifier.java`
- 캐시: 토큰 SHA-256 키, 성공만, TTL 300초, 최대 1만 건 - `[qa] src/main/java/gravit/qa/security/support/AdminVerificationCache.java`, `[qa] src/main/java/gravit/qa/security/config/AuthProps.java`
- 판정 응답 매핑: 200 통과, 401 `AUTH_4012`, 403 `AUTH_4031`, 그 밖과 호출 실패 503 `AUTH_5031` - `[qa] src/main/java/gravit/qa/security/infrastructure/AdminMeClient.java:55-74`, `[qa] src/main/java/gravit/qa/global/exception/domain/CustomErrorCode.java:27-31`
- 메인은 요청마다 JWT 서명을 확인하고 DB에서 사용자를 읽어 권한을 정한다. 권한 회수가 메인에는 즉시 반영된다 - `[main] src/main/java/gravit/code/security/filter/JwtAuthFilter.java:76-97`, `[main] src/main/java/gravit/code/auth/service/AuthTokenProvider.java`(`parseUser`), `[main] src/main/java/gravit/code/auth/token/JwtProvider.java:99-106`(`getAuthentication`)
- access 토큰 2시간, 로그아웃은 refresh 토큰만 지운다 - `[main] src/main/resources/application-prod.yml:137-143`
- 메인은 관리자 ID를 토큰 주체에서 얻는다(`@AuthenticationPrincipal LoginUser`) - `[main] src/main/java/gravit/code/admin/controller/AdminNoticeController.java:48,57,67` 등. 토큰을 그대로 넘기면 공지 작성자, 감사 `admin_id`가 지금과 같게 남는다

### 8-2. 헤더 전달 방식

| 대안 | 내용 | 평가 |
|---|---|---|
| 1. 요청 토큰 그대로 전달 | 필터가 받은 토큰을 요청 범위에 보관하고, `adapter/gravit` 클라이언트가 같은 `Authorization: Bearer <토큰>`을 붙인다 | P-7 그대로. 메인 판정, 관리자 ID가 지금과 같다 |
| 2. 서버 간 자격 증명 + 관리자 ID 헤더 | 관리자 서버가 자기 자격으로 부르고 관리자 ID를 헤더로 알린다 | 메인 인증 방식을 바꿔야 해 P-7과 어긋난다 |

1을 고른다. 토큰은 인증 객체의 자격 증명 칸(지금 `null`)에 담아 요청이 끝나면 사라지게 하고, 로그와 예외 메시지에 남기지 않는다. 사람이 요청하지 않은 호출(스케줄러, 작업 처리기)에는 전달할 토큰이 없다. 이 경우의 인증은 미결 M-1이다.

### 8-3. 상태코드 매핑 (admin-web D8-3과 대조)

| 상황 | 관리자 서버 응답 | 웹 동작 | D8-3 |
|---|---|---|---|
| 헤더 없음, 형식 오류 | 401 `AUTH_4011` | 재발급 후 1회 재시도 | 401 일치 |
| 판정 401(만료, 무효) | 401 `AUTH_4012` | 재발급 후 1회 재시도 | 일치 |
| 판정 403(관리자 아님) | 403 `AUTH_4031` | 종착 | 일치 |
| 판정 서버 오류, 타임아웃 | 503 `AUTH_5031` | 재발급하지 않고 오류 표시(로그아웃 없음) | 일치 |
| 캐시 통과 뒤 메인 API가 401 | 메인 본문 그대로 401(`JWT_4012` 등), 캐시 삭제 | 재발급 → 재시도 → 캐시 없음 → 판정 → 통과 | 401 일치 |
| 캐시 통과 뒤 메인 API가 403 | 메인 본문 그대로 403(`A_D_403`), 캐시 삭제 | 종착 | 일치 |
| 메인 연결 실패, 타임아웃 | 502 `GRAVIT_5021`, 504 `GRAVIT_5041` | 오류 표시 | D8-3에 없는 상태. 401로 뭉개지 않아 재발급 루프가 생기지 않는다 |

같은 상태라도 `error` 코드는 서버마다 다르다(401: 관리자 `AUTH_4012`, 메인 `JWT_4012`. 403: `AUTH_4031`, `A_D_403`). 웹은 상태로만 분기하고 `message` 문자열을 보여 주므로 동작에는 영향이 없다 - `[web] src/shared/api/client.ts:75`, `[web] src/shared/api/errorHandler.ts:20-31`.

### 8-4. 300초 판정 캐시와의 관계

문제 두 가지:

1. 캐시에 넣은 뒤 토큰이 만료돼도 300초까지 관리자 서버가 받아 준다. 메인으로 넘기는 요청은 메인이 401을 주지만, 조회와 소유 기능은 관리자 서버 안에서 끝나 만료된 토큰이 통한다
2. 권한을 회수(ADMIN → USER)해도 300초 동안 관리자 서버 안에서 끝나는 기능은 계속 쓸 수 있다. 메인으로 넘기는 요청은 메인이 바로 403을 준다

| 대안 | 1 해소 | 2 해소 | 비용 |
|---|---|---|---|
| a. 지금대로 | 아니오 | 아니오 | 없음 |
| b. TTL 단축(예: 60초) | 일부 | 일부 | 판정 호출 5배 |
| c. 만료 상한: 캐시 만료 = min(지금 + 300초, 토큰 `exp`) | 예 | 아니오 | 토큰 본문의 `exp`만 읽는다(서명 확인 없이 쓰지만 만료를 줄이는 데만 써서 위조해도 300초를 넘길 수 없다) |
| d. 메인 401, 403이면 그 토큰 캐시 삭제 | 일부(다음 요청부터) | 일부(메인을 한 번 거친 뒤부터) | 작음 |
| e. 관리자 서버 소유 데이터의 쓰기 요청은 캐시를 건너뛰고 매번 판정 | - | 쓰기는 예 | 쓰기마다 판정 1회(쓰기는 드물다) |
| f. 메인이 권한 변경을 관리자 서버에 알림 | 아니오 | 예 | 메인 변경, 새 결합 |

c + d + e를 고른다. 남는 위험은 "권한 회수 뒤 최대 300초 동안 관리자 서버 안에서 끝나는 조회(대시보드, 목록, QA 조회)가 된다"이고, 이를 받아들인 위험으로 적는다. 로그아웃해도 access 토큰은 메인에서도 만료까지 유효하므로 새로 생기는 차이는 아니다.

---

## 9. D-7 응답 형식 호환과 검증 방법

### 9-1. 계약의 기준

| 문서 | 상태 | 쓰임 |
|---|---|---|
| `[web] docs/spec/03_gravit_admin_api_spec.md` | v1.1(2026-06-02). 오류 본문, 페이지 필드, 인증 경로, 시각 형식이 실제와 다르다(14절). 문의, DAU/MAU, `/me`가 없다 | 기능 의도 참고 |
| `[web] src/features/*/schemas.ts` (Zod) | 웹이 실제로 받아들이는 최소 조건. 모르는 키는 버리고(`z.object` 기본), 시각은 `z.string()` | 웹이 깨지는지 판단 |
| `[main] src/main/java/gravit/code/admin/dto/response/*` + Jackson 기본값 | 웹이 지금 받고 있는 실제 응답 | P-6의 기준 |

P-6("응답 형식 유지")의 기준은 메인의 실제 응답으로 둔다. Zod만 통과하면 된다고 보면 웹이 버리는 필드(`totalElements`)나 시각 형식이 바뀌어도 모른다. 셋 사이 불일치는 14절에 모았다.

### 9-2. 같은 응답인지 검증하는 방법

| 대안 | 잡는 것 | 못 잡는 것 | 비용 |
|---|---|---|---|
| 1. 실서버 비교: 같은 GET 요청을 prod 메인과 관리자 서버에 보내 상태, `error`, JSON 트리 비교 | 실제 데이터, 숨은 동작, 직렬화, 오류 형식 | 실제 데이터에 없는 경우 | `liveTest` 하나와 요청 표 |
| 2. 메인 응답을 파일로 떠 두고 비교 | 고정된 예시 | 데이터 변화, 시간 의존(DAU) | 파일 관리 |
| 3. Testcontainers에 메인 스키마 픽스처를 깔고 Repository 통합 테스트 | SQL 정확성, 경계값 | 메인 스키마가 바뀐 것 | 픽스처 DDL |
| 4. 웹 Zod 파싱 | 웹이 깨지는지 | 필드 추가, 형식 변화 | 수동 |

1 + 3을 고른다. D-10에서 dev 인스턴스를 두지 않기로 해, 1은 관리자 서버에 새 엔드포인트를 배포하되 웹은 아직 메인을 보게 둔 상태에서 prod를 대상으로 돌린다. 두 서버가 같은 prod 데이터를 읽으므로 비교가 오히려 정확하다. prod에 쓰지 않도록 비교 대상은 GET(조회, 소유 조회)만이고, API 방식 쓰기는 응답 바이트 전달 테스트(단위)로 확인한다.
1은 `[qa] build.gradle`의 `liveTest`(`@Tag("live")`) 방식을 그대로 쓴다. 환경 변수(`ADMIN_CONTRACT_MAIN_BASE`, `ADMIN_CONTRACT_ADMIN_BASE`, `ADMIN_CONTRACT_TOKEN`)가 없으면 건너뛴다. 엔드포인트마다 요청 표를 둔다: 기본값, 첫 페이지, 마지막 페이지 다음, 필터 조합, 없는 ID(404), 잘못된 파라미터(400), `page=0`.
비교는 키 순서만 무시하고 값, 타입, null, 문자열 형식은 그대로 본다. 허용 차이는 9-4의 표에 적은 것만이다. 이 테스트 통과가 전환(헤더 조건 삭제)의 조건이다(D-8).
3의 픽스처 DDL 파일 머리에는 출처 메인 마이그레이션 파일과 커밋을 적는다.

### 9-3. 공통 응답 형식 규칙

- 페이지: `{page, totalPages, hasNext, totalElements, contents}` - `[main] src/main/java/gravit/code/global/dto/response/PageResponse.java`. `page`는 1부터, 크기 20 고정, `totalPages = ceil(total / 20)`(행이 없으면 0), `hasNext = page < totalPages`, 범위를 넘은 페이지는 `contents: []`. JdbcTemplate으로 옮길 때 Spring `Page` 계산을 그대로 따라야 한다
- 시각: 메인 DTO는 `LocalDateTime`, `LocalDate`다. 오프셋 없이 KST 벽시계 값이 나간다(예 `2026-11-18T10:15:30.123456`, 소수부는 0이 아닐 때만). 관리자 DTO도 같은 타입을 쓰고, 컬럼은 `ResultSet.getObject(col, LocalDateTime.class)`로 읽는다. QA API는 `OffsetDateTime`(+09:00)을 쓰고(`[qa] src/main/java/gravit/qa/job/dto/response/JobResponse.java` 등) 웹 QA 계약도 그 형식이다(D9-2). 같은 서버 안에서 두 형식이 공존하므로 이관 모듈에서는 `OffsetDateTime`을 쓰지 않는다. `spring.jackson.time-zone: Asia/Seoul`(`[qa] src/main/resources/application.yml:20`)은 `LocalDateTime`에 영향이 없다
- 월: DASH-03의 `month`는 `yyyy-MM` 문자열이다
- null: 두 서버 모두 Jackson 기본값이라 null을 생략하지 않는다. 문제 상세는 객관식이면 `answer: null`, 주관식이면 `options: null`이 나간다(`[main] src/main/java/gravit/code/admin/dto/response/ProblemDetailResponse.java:29-58`, 스테이징도 같다 `StagingProblemResponse.java:27-53`). 관리자 DTO도 키를 빼지 않는다
- boolean 이름: `@JsonProperty("isResolved")`, `@JsonProperty("isAnswer")`를 그대로 붙인다
- enum: 이름 문자열. 문의 목록, 상세의 `type`, `status`는 메인에서 String이다
- 숫자: id, 개수는 `long`. `averageProgress`는 `int`(0~100)
- 검증 메시지 언어: Hibernate Validator 기본 메시지는 JVM 로케일을 따른다. 두 이미지가 같은 `eclipse-temurin:21-jre`라(`[main] Dockerfile:1`, `[qa] Dockerfile:1`) 같게 나올 것으로 보지만 계약 테스트로 확인한다

### 9-4. 오류 응답 차이

두 서버의 오류 본문 모양(`ErrorResponse<T>(String error, T message)`)은 같지만 예외 처리 결과가 다르다 - `[main] src/main/java/gravit/code/global/exception/handler/GlobalExceptionHandler.java`, `[qa] src/main/java/gravit/qa/global/exception/handler/GlobalExceptionHandler.java`.

| 경우 | 메인 | qa-server | 이관 엔드포인트 처리 |
|---|---|---|---|
| `RestApiException` | 코드별 상태, `{error: "USER_4041" 등, message: 문자열}` | 같은 방식 | 메인 코드와 메시지를 그대로 옮긴다(`PAGE_4001`, `USER_4041`, `REPORT_4041`, `NOTICE_4041`, `INQUIRY_4041`, `STAGING_*` 등) |
| `@Valid` 본문 검증 | 400 `GLOBAL_4001`, `["[ 필드 ][ 메시지 ][ 값 ]"]` | 같음 | 그대로 |
| `@Validated` 파라미터 검증(예 `days=0`) | 400 `GLOBAL_4001`, `["[ 마지막 경로 이름 ][ 메시지 ][ 값 ]"]` | 400 `GLOBAL_4001`, `["[ 전체 경로 ][ 메시지 ]"]` | 메인 형식으로 맞춘다 |
| 파라미터 타입 오류(`page=abc`, 없는 enum) | 400 `GLOBAL_4001` "유효성 검사 실패" | 400 `GLOBAL_4001` "잘못된 요청 파라미터입니다." | 메인 메시지로 맞춘다 |
| 본문 JSON 파싱 실패 | 500 `GLOBAL_5001`(처리기 없음) | 400 `GLOBAL_4001` | qa-server 쪽 유지(의도한 차이. 메인 500은 결함) |
| 없는 경로 | 500 `GLOBAL_5001` | 404 `GLOBAL_4041` | qa-server 쪽 유지(의도한 차이) |
| DB 예외 | 400 `DB_4001` | 500 `GLOBAL_5002` | qa-server 쪽 유지(의도한 차이) |
| 인증 401 | `JWT_4011`~`JWT_4017` | `AUTH_4011`, `AUTH_4012` | 판정은 관리자 코드, 메인 전달은 메인 코드(8-3) |
| 권한 403 | `A_D_403` | `AUTH_4031` | 같음 |

메인 형식으로 맞추는 것은 이관 모듈(`insight`, `support`, `member`, `content`, `audit`) 범위의 `@RestControllerAdvice`(`global/exception/handler/AdminApiExceptionHandler`, 우선순위 높게)로 하고, QA API의 오류 형식은 바꾸지 않는다. "의도한 차이" 세 줄은 웹이 상태별 기본 문구만 다르게 보여 주는 정도의 차이이고(`[web] src/shared/api/errorHandler.ts:6-14`), 계약 테스트의 허용 차이 표에 올린다.

---

## 10. D-8 도메인 이관 작업 정의

### 10-1. 0단계 - 도메인 공통 준비 (한 번)

- [ ] D-1 C-1, D-2 구조 이동 PR(동작 변경 없음, 패키지 이동과 ArchUnit 테스트)
- [ ] `adapter/gravit` 메인 API 클라이언트(D-5 타임아웃, 헤더, 응답 전달, 502/504)와 `/api/v1/admin/**` 전달 Controller(D-3 (c)), 테스트(메서드, 쿼리, 본문, 오류 본문, 204, 같은 경로 다른 메서드)
- [ ] D-6 필터 변경(토큰 보관, `exp` 상한, 401/403 캐시 삭제, 소유 데이터 쓰기 시 캐시 건너뛰기)
- [ ] `global/config/MainDbConfig`, `@MainDb`, `AdminApiExceptionHandler`
- [ ] 계약 테스트 틀(`liveTest`), 메인 스키마 픽스처 방식
- [ ] 운영: 읽기 전용 계정, 서버 `.env` 키, (C-2) 새 도메인 별칭(D-10 작업 목록)
- [ ] D-10: 판정, 호출 대상(`QA_AUTH_HOST`)을 prod 메인으로, CD 트리거를 dev push에서 main 머지로(브랜치 전략 변경은 D-9 하네스 개선과 함께)
- [ ] 웹 변경 전 확인: 로컬 웹(vite proxy → 운영 관리자 서버)으로 43개 기능의 주요 화면을 한 바퀴 돈다. 이 시점에는 모든 요청이 전달 계층으로 메인에 간다
- [ ] 웹: `apiClient` base, rewrite, vite proxy(5-7 표). 이 배포부터 관리자 기능 전체가 관리자 서버를 지난다

### 10-2. 도메인 하나의 단계와 완료 기준

| 단계 | 완료 기준 |
|---|---|
| 1. 메인 쪽 준비 | - [ ] 대상 ID의 Controller, Docs(검증 어노테이션), Service, Repository, DTO, 테스트 파일 목록<br>- [ ] 조회: 읽는 테이블, 컬럼, 정렬, 숨은 동작(6-1 목록) 확정, GRANT 추가 요청<br>- [ ] API: 메인 엔드포인트 유지 확인, 부작용과 재요청 결과(7-1) 확인<br>- [ ] 다른 도메인이 같이 쓰는 Repository 메서드 표시(지우면 안 되는 것)<br>- [ ] 메인 PR이 필요하면(STG-07 A) 메인에 먼저 머지, 배포 |
| 2. 관리자 서버 구현 | - [ ] 같은 경로, 같은 HTTP 메서드, 같은 기본값과 검증 범위<br>- [ ] 응답 DTO는 같은 JSON(9-3), 오류 코드와 메시지 복사(9-4)<br>- [ ] 단위, 통합 테스트(픽스처 DDL) 통과, ArchUnit 통과<br>- [ ] Controller에 헤더 조건(`X-Admin-Route=local`)을 단 채 배포(웹 요청은 계속 메인으로 넘어감)<br>- [ ] 헤더를 붙인 prod GET 계약 테스트 통과(허용 차이 외 0건) |
| 3. 전환 | - [ ] 헤더 조건을 지우는 PR(관리자 서버) 머지, 배포. 웹 변경 없음<br>- [ ] 운영 웹에서 화면 확인: 목록, 상세, 빈 상태, 오류 토스트, 401 재발급<br>- [ ] 관리자 서버 로그에서 해당 경로가 전달 계층이 아닌 새 구현으로 처리되는지, 메인 로그에서 해당 GET 요청이 사라졌는지 확인 |
| 4. 메인 옛 코드 삭제 | - [ ] 보류 기간(추천 2주, 사람 결정) 동안 롤백 없음<br>- [ ] 조회 기능의 Controller, Docs, Service, Repository, DTO, 테스트 삭제 PR(메인). API 기능은 남긴다<br>- [ ] 다른 도메인이 쓰는 메서드는 남긴다 |

### 10-3. 전환 절차와 롤백 절차 (D-3 (c) 기준)

전환: 관리자 서버에 헤더 조건을 단 새 구현 배포 → 헤더를 붙인 prod GET 계약 테스트 통과 → 헤더 조건을 지우는 배포 → 확인 → 보류 → 메인 삭제.

보류 기간의 뜻: 관리자 서버가 그 도메인을 직접 처리하기 시작한 뒤에도 메인의 옛 Controller, Service를 바로 지우지 않고 남겨 두는 기간이다. 이 동안 문제가 생기면 관리자 서버에서 그 도메인을 다시 메인으로 넘기기만 하면 되고 메인은 손대지 않는다. 옛 코드를 지운 뒤에는 메인 코드를 되살려 메인 릴리스까지 해야 해서 롤백이 가장 느려진다. 2주를 추천하는 이유는 문의 답변, 공지 삭제, 승격처럼 드물게 쓰는 기능도 실제로 한 번 이상 쓰일 시간을 주기 위해서다.

롤백:

| 시점 | 방법 | 소요 |
|---|---|---|
| 전환 뒤, 메인 옛 코드 삭제 전 | 그 도메인 Controller에 헤더 조건을 다시 걸거나 전환 커밋 revert 후 관리자 서버 배포. 요청은 전달 계층으로 메인에 간다 | 관리자 서버 배포(재기동 동안 관리자 기능 전체 중단) |
| 관리자 서버 전체 문제(전달 계층 포함) | 웹 `apiClient` base를 메인으로 되돌려 웹 배포(또는 Vercel 직전 배포) | 즉시 ~ 웹 빌드 수 분 |
| 메인 옛 코드 삭제 뒤 | 삭제 PR revert → 메인 검증 → 메인 릴리스 → 위 첫 줄 | 메인 릴리스 주기(가장 느림. 그래서 보류 기간을 둔다) |
| 소유 데이터 도메인(5단계) | 6-2의 되돌리는 방법(역복사)이 먼저 필요 | 데이터 양에 따름 |

### 10-4. 예시 - 1단계 대시보드, 통계(DASH-01~03)

AUTH-01은 API 방식이라 0단계 전달 계층이 처음부터 맡는다. 따로 옮길 코드가 없다.

**메인 (gravit-server dev `0d3f9a3`)**

| 파일 | 역할 | 4단계 처리 |
|---|---|---|
| `src/main/java/gravit/code/admin/controller/AdminDashboardController.java` | 3개 GET | 삭제 |
| `src/main/java/gravit/code/admin/controller/docs/AdminDashboardControllerDocs.java` | Swagger, `days` `@Min(1) @Max(365)`, `months` `@Min(1) @Max(36)`(검증이 여기 있다) | 삭제 |
| `src/main/java/gravit/code/admin/service/AdminDashboardService.java` | 요약, DAU/MAU 빈 날 0 채우기, `inProgress` | 삭제 |
| `src/main/java/gravit/code/admin/repository/AdminActiveUserRepository.java` | DAU `COUNT(user_id)`, MAU `COUNT(DISTINCT user_id)` | 삭제 |
| `src/main/java/gravit/code/admin/repository/AdminReportRepository.java` | `countByIsResolvedFalse()`(대시보드만 사용) | 메서드만 삭제(인터페이스는 RPT가 쓴다) |
| `src/main/java/gravit/code/admin/repository/StagingLabelRepository.java` | `countByStatus()`(대시보드만 사용) | 메서드만 삭제(인터페이스는 STG가 쓴다) |
| `src/main/java/gravit/code/admin/repository/AdminUserRepository.java` | `countActiveUsers()` | 남긴다. CON-03(`AdminChapterService.java:61`)이 쓴다 |
| `src/main/java/gravit/code/admin/dto/response/DashboardSummaryResponse.java` | `{totalUsers, pendingLabelsCount, unresolvedReportsCount}` | 삭제 |
| `src/main/java/gravit/code/admin/dto/response/DailyActiveUserTrendResponse.java`, `DailyActiveUserPointResponse.java` | `{points:[{date, dau}]}` | 삭제 |
| `src/main/java/gravit/code/admin/dto/response/MonthlyActiveUserTrendResponse.java`, `MonthlyActiveUserPointResponse.java` | `{points:[{month, mau, inProgress}]}` | 삭제 |
| `src/main/java/gravit/code/admin/dto/internal/DailyActiveUserCountDto.java`, `MonthlyActiveUserCountDto.java` | 쿼리 투영 | 삭제 |
| `src/test/java/gravit/code/admin/controller/AdminDashboardControllerIntegrationTest.java`, `src/test/java/gravit/code/admin/service/AdminDashboardServiceIntegrationTest.java` | 테스트 | 삭제. 관리자 서버 테스트가 같은 시나리오를 옮겨 받는다 |
| `src/main/java/gravit/code/user/domain/UserDailyActivity.java`와 활동 기록 코드 | 메인 사용자 활동 기록(쓰기) | 남긴다 |
| `src/main/java/gravit/code/admin/repository/AdminStatsRepository.java` | 챕터 통계(CON-03) | 1단계 대상 아님. 5단계 |

재현할 동작:

- `totalUsers`: `users.status <> 'DELETED'` (`[main] src/main/java/gravit/code/admin/repository/AdminUserRepository.java:52-57`)
- `pendingLabelsCount`: `staging_label.status = 'PENDING'`. 5단계에서 스테이징이 관리자 DB로 가면(D-4(ii) A) 이 값의 출처를 `content` 모듈 조회 서비스로 바꾼다(△3)
- `unresolvedReportsCount`: `report.is_resolved = false`
- DAU: 오늘(KST `Clock`)부터 `days - 1`일 전까지, 날짜별 `COUNT(user_id)`, 빈 날 0
- MAU: 이번 달부터 `months - 1`달 전 1일 ~ 오늘, 달별 `COUNT(DISTINCT user_id)`, 빈 달 0, 이번 달만 `inProgress: true`
- 범위 위반은 400 `GLOBAL_4001` + 메인 형식 메시지 배열(9-4)

**관리자 서버 (qa-server, D-1 C-1 전이면 기준 패키지 `gravit.qa`)**

| 파일 | 내용 |
|---|---|
| `src/main/java/gravit/qa/insight/controller/DashboardController.java` | `@RequestMapping("/api/v1/admin/dashboard")`, 3개 GET, 기본값 30, 12. 검증 중에는 `headers = "X-Admin-Route=local"`, 전환 PR에서 삭제 |
| `src/main/java/gravit/qa/insight/controller/docs/DashboardControllerDocs.java` | `@Min`, `@Max`를 메인과 같게 |
| `src/main/java/gravit/qa/insight/service/DashboardService.java` | 메인 Service와 같은 계산, `Clock` 주입 |
| `src/main/java/gravit/qa/insight/repository/custom/DashboardStatsRepository.java`, `DashboardStatsRepositoryImpl.java` | `@MainDb JdbcTemplate` 조회 |
| `src/main/java/gravit/qa/insight/repository/sql/DashboardStatsSql.java` | SQL 상수 4개(회원 수, 대기 라벨 수, 미처리 신고 수, DAU, MAU) |
| `src/main/java/gravit/qa/insight/dto/response/DashboardSummaryResponse.java`, `DailyActiveUserTrendResponse.java`, `DailyActiveUserPointResponse.java`, `MonthlyActiveUserTrendResponse.java`, `MonthlyActiveUserPointResponse.java` | 메인과 같은 필드, 타입 |
| `src/main/java/gravit/qa/insight/dto/internal/DailyActiveUserCountDto.java`, `MonthlyActiveUserCountDto.java` | 쿼리 결과 |
| `src/main/java/gravit/qa/global/config/MainDbConfig.java`, `MainDbProps.java`, `src/main/java/gravit/qa/global/annotation/MainDb.java` | 0단계에서 만든 것 사용 |
| `src/main/resources/application.yml`, `.env.example`, `docker-compose-qa.yml` | `qa.main-db.*`, `MAIN_DB_URL`, `MAIN_DB_USERNAME`, `MAIN_DB_PASSWORD` (0단계) |
| `src/test/java/gravit/qa/insight/service/DashboardServiceTest.java` | 고정 `Clock`: 빈 날 채우기, 월 경계, `inProgress` |
| `src/test/java/gravit/qa/insight/repository/DashboardStatsRepositoryIntegrationTest.java` + `src/test/resources/maindb/dashboard.sql` | 픽스처 DDL(출처 메인 V1, V21, V42) |
| `src/test/java/gravit/qa/insight/controller/DashboardControllerTest.java` | `days=0`, `days=366`, `months=37`의 400 형식 |
| `src/test/java/gravit/qa/insight/contract/DashboardContractLiveTest.java` | `@Tag("live")`, `X-Admin-Route: local` 헤더를 붙여 prod 메인과 비교. 요청 표: 기본값, `days=1`, `days=365`, `months=1`, `months=36`, 범위 위반 |

**웹 (admin-web)**: 1단계에서 바꿀 파일이 없다. 0단계에서 `apiClient` base를 바꾼 뒤로 `src/features/dashboard/api.ts`의 호출은 그대로 관리자 서버로 간다. `src/mocks/handlers.ts`도 그대로다.

롤백: `DashboardController`에 헤더 조건을 다시 걸어 배포하면 대시보드 요청이 전달 계층으로 메인에 간다. 메인 삭제 PR 전까지 가능하다.

---

## 11. D-9 하네스 규칙 개정안

이번 이슈(#28)에서 적용한다(2026-11-18 사용자 결정). 사용자가 하네스 전반의 개선을 함께 보자고 해, 개선 범위를 정한 뒤 아래 개정안과 함께 반영한다. 아래 "전"은 지금 문구, "후"는 제안 문구다. 구조 이동(C-1) 전에 적용하므로 경로 예시는 구조 이동 PR에서 한 번 더 고친다.
D-10 결정으로 CLAUDE.md "실행 단위"와 README "배포"의 CD 설명(dev push 배포)도 main 머지 배포로 바뀐다.

### 11-1. `.claude/CLAUDE.md`

| 위치 | 전 | 후 |
|---|---|---|
| 첫 단락 | Gravit(CS 학습 서비스)의 QA 플랫폼 백엔드. 백오피스(gravit-admin-web) QA 메뉴의 API를 제공하고, … 2차는 사람이 수동으로 검수한다. | Gravit(CS 학습 서비스)의 관리자 서버. 백오피스(gravit-admin-web)의 관리자 API(`/api/v1/admin/**`)와 QA 메뉴 API를 제공한다. gravit-server admin 기능을 도메인 단위로 옮겨 오는 중이며, 진행 상황은 `docs/design/ADMIN_SERVER_DESIGN.md` 2절 표를 따른다. QA 흐름(명세 반영, 체크리스트, 디자인 검수, 1차 판정)은 `qa` 모듈이 맡는다. |
| 설계 원본 줄 | 설계 원본: `docs/design/03_DESIGN.md`, API 계약: `docs/api/openapi.yaml` | 관리자 서버 설계: `docs/design/ADMIN_SERVER_DESIGN.md`. QA 정책: `.claude/spec/qa-policy/`. API 계약: 실행 중 Swagger(`/v3/api-docs`)와 `*ControllerDocs`. 옮겨 오는 관리자 API는 메인의 실제 응답이 기준(설계 D-7) |
| 기술 스택 | Spring Security: 자체 JWT 검증 없음. 관리자 토큰을 gravit-server `GET /api/v1/admin/me`에 되던져 판정 | 같은 줄 + "메인 API 호출은 받은 토큰을 그대로 전달한다(P-7). 메인 DB는 읽기 전용 두 번째 데이터소스(`@MainDb`)로만 읽는다" |
| 실행 단위 표 | `api` 한 줄 | 그대로(D-10 단일 인스턴스). "prod 메인을 대상으로 하고 main 머지로 배포한다" 문장 추가 |
| 새 절 "관리자 서버 원칙" | 없음 | - 데이터 소유 기준: 소유(QA, 스테이징, 감사 로그)는 관리자 DB, API(메인 도메인 쓰기, 알림 같은 부작용)는 메인 API, 조회는 메인 DB 읽기 전용<br>- 메인 DB에 쓰지 않는다. 메인 테이블에 JPA 엔티티를 만들지 않는다<br>- 옮기는 관리자 API는 경로, 응답 형식을 바꾸지 않는다(P-6). 전환(헤더 조건 삭제) 전에 계약 테스트를 통과한다. 아직 옮기지 않은 관리자 API는 전달 계층이 메인으로 넘긴다<br>- 메인 API 쓰기는 자동 재시도하지 않는다<br>- 모듈 사이 의존은 `ArchitectureTest`의 허용 표를 따른다 |
| QA 플랫폼 원칙 6번째 줄 | 1차 판정은 저장소 코드를 읽기만 한다. 저장소 테스트를 실행하지 않고, gravit-server, gravit-web, gravit-aos에 쓰지 않는다 | 1차 판정은 저장소(GitHub) 코드를 읽기만 한다. 저장소 테스트를 실행하지 않고 세 저장소에 커밋, 푸시하지 않는다. (메인 데이터 변경은 관리자 서버 원칙의 메인 API로만 한다) |
| 규칙 참조 | 프로젝트 구조 → `project-structure.md` | 같은 줄 + "모듈 배치와 허용 의존 → `project-structure.md`" |

### 11-2. `README.md`

| 위치 | 전 | 후 |
|---|---|---|
| 첫 줄 | Gravit QA 플랫폼(서비스명 gravit-qa) 백엔드. 백오피스(gravit-admin-web) QA 메뉴의 API와 QA 준비 작업(…)을 담당한다. | Gravit 관리자 서버(서비스명 gravit-qa, 이름 변경은 설계 D-1). 백오피스의 관리자 API와 QA 메뉴 API, QA 준비 작업을 담당한다. |
| 링크 목록 | 설계 `docs/design/03_DESIGN.md`, API 계약 `docs/api/openapi.yaml` | 설계 `docs/design/ADMIN_SERVER_DESIGN.md`, API 계약 Swagger `/swagger-ui.html`, QA 흐름 정책, 개발 하네스 |
| 현재 범위 표 | QA 흐름 재설계 항목만 | "관리자 기능 이관" 줄 추가: 도메인 6단계와 진행 상태 |
| 배포 절 | `docker-compose-qa.yml` 하나로 같은 이미지를 `api` / `e2e` 프로필 두 컨테이너로 띄운다. 두 컨테이너 모두 … / qa-e2e는 C7 전까지 올리지 않는다 | `docker-compose-qa.yml`로 `api` 프로필 컨테이너 하나(`qa-api`)를 띄운다. 자원 한도는 필수다. (e2e 컨테이너는 D13으로 없앴다. 지금 README만 남아 있는 옛 문장이다) |
| 로컬 실행 | 필요한 변수 안내 없음 | `MAIN_DB_URL`, `MAIN_DB_USERNAME`, `MAIN_DB_PASSWORD` 추가(0단계 뒤) |

### 11-3. `.claude/rules/project-structure.md`

| 위치 | 전 | 후 |
|---|---|---|
| 트리 | `global/`, `security/`, `job/`, `sprint/`, `feature/`, `reflection/`, `snapshot/`, `checklist/`, `designReview/`, `autoReview/`, `fixReport/`, `adapter/` (최상위 12개) | 4-2의 트리(`global`, `job`, `auth`, `adapter`, `qa/{sprint, feature, reflection, snapshot, checklist, designReview, autoReview, fixReport}`, `insight`, `support`, `member`, `content`, `audit`) |
| 도메인 패키지 내부 구조 머리 | `{domain}/` | `{module}/` (도메인이 하나인 모듈) 또는 `{module}/{domain}/` (`qa`처럼 여럿인 모듈) |
| 처리기 예시 | `sprint/handler/SpecLoadJobHandler` | `qa/sprint/handler/SpecLoadJobHandler` |
| adapter 설명 | 외부 연동: figma, notion, github, openapi, llm | 외부 연동: figma, notion, github, openapi, llm, gravit(메인 서버 API) |
| 새 절 "모듈 사이 의존" | 없음 | 4-5 허용 의존 표, 조건 △1~△4, "`ArchitectureTest`가 강제한다" |
| 새 절 "메인 DB 조회" | 없음 | 조회는 `{module}/repository/custom/*RepositoryImpl`에서 `@MainDb JdbcTemplate`만. SQL은 `repository/sql/`. 메인 테이블 JPA 엔티티, 쓰기 금지. 테스트 픽스처 DDL 머리에 출처 마이그레이션과 커밋 |
| 새 절 "메인 API 호출" | 없음 | `adapter/gravit` 클라이언트만 쓴다. 쓰기 자동 재시도 금지. 응답은 상태와 본문을 그대로 전달 |

### 11-4. 깨진 참조 처리

현황: `docs/design/03_DESIGN.md`, `docs/api/openapi.yaml`은 커밋 `dd8d90f`(#7 "docs 추적 해제")에서 추적을 끊었고 `.gitignore:15-16`이 `/docs/*`를 무시한다(`deploy-runbook.md`만 예외). 로컬에도 없다. 참조하는 곳: `.claude/CLAUDE.md:7`, `README.md:5-6`, `.claude/spec/qa-policy/README.md:3,17`, `.claude/spec/qa-policy/prepare-job.md:3`, 지난 계획서 `.claude/resources/plans/PLAN-9.md`, `PLAN-11.md`, `PLAN-15.md`.

| 대안 | 내용 | 평가 |
|---|---|---|
| 1. 이력에서 복원 | `git show 384a66e:docs/design/03_DESIGN.md` 등을 다시 추적 | 내용이 D13 이전이라 오래됐다. 공개 저장소에 다시 올리는 판단도 필요 |
| 2. 참조를 바꾼다 | CLAUDE.md, README의 링크를 이 문서, 정책 디렉터리, Swagger로 바꾸고, 정책 문서의 "원본은 03_DESIGN" 문장은 "옛 설계(추적 해제, 커밋 `384a66e`)"로 바꾼다 | 지금 실제로 쓰는 문서를 가리킨다 |
| 3. 그대로 두고 "로컬 전용" 표시 | 파일이 로컬에도 없어 의미가 없다 | - |

2를 추천한다. 지난 계획서(PLAN-9, 11, 15)는 당시 기록이라 고치지 않는다. `openapi.yaml`은 실행 중 Swagger로 대신하고, 고정본이 필요해지면 CI에서 `/v3/api-docs`를 떠 산출물로 남기는 방식을 그때 정한다.
이 문서 자체도 `/docs/*` 무시 규칙에 걸려 `git add -f`로 이 파일만 추적했다. `.gitignore`에 `docs/design/ADMIN_SERVER_DESIGN.md` 예외를 둘지는 이 개정안과 함께 사람이 정한다(이번 PR은 설정을 바꾸지 않는다).

---

## 12. D-10 배포 단위

### 12-1. 컨테이너 구성

| 대안 | 내용 | 평가 |
|---|---|---|
| 1. 하나 유지 | `api` 프로필 컨테이너 하나가 관리자 API, QA API, QA 작업 처리를 함께 | 지금 구성과 CD 그대로 |
| 2. 둘로 분리 | 같은 이미지를 관리자 API용과 QA 작업용으로 나눈다 | QA 준비 작업(LLM 대기 시간이 긴 작업)이 관리자 API와 자원을 나눠 쓰지 않는다. 메모리와 운영 대상이 두 배 |

1을 유지한다. 지금 판단할 근거(측정)는 P-2에 따라 만들지 않으므로, 분리는 문제가 관찰될 때 다시 본다. 같은 이미지라 나중에 프로필, `qa.worker.job-types` 설정만으로 나눌 수 있다.

### 12-2. 환경 - 단일 인스턴스 (확정, 2026-11-18)

사용자 결정: 인스턴스는 하나로 간다. dev 인스턴스를 둔 이유가 분명하지 않고, 관리자가 쓰는 기능이라 main에 바로 머지해 배포해도 된다는 판단이다. 아래 비교에서 E2를 추천했지만 E1(인스턴스 하나를 prod로)로 확정했다.

E1로 생기는 변경:

- 판정, 호출 대상 `QA_AUTH_HOST`를 prod 메인(`https://grav-it.inuappcenter.kr`)으로 바꾼다. 이 값은 관리자 웹이 로그인하는 메인 환경과 같아야 한다. 지금 값은 dev다(`[qa] .env.example`). 관리자 웹 운영 배포가 prod 메인을 본다고 확인했으므로(M-2 해소) prod로 바꾸면 QA 메뉴와 관리자 기능이 같은 토큰으로 판정된다
- 메인 DB 읽기 전용 계정은 prod 메인 DB에 만든다
- 관리자 DB는 D-4(ii)대로 지금 QA DB를 그대로 쓴다
- CD 트리거를 dev push에서 main 머지로 바꾼다(`[qa] .github/workflows/cd.yml:3-5`). `ci.yml`의 PR 대상, `.claude/spec/git-convention.md`의 브랜치 전략(main → dev → 개발 브랜치), open-issue, open-pr 스킬의 dev 기본값도 함께 바뀐다(하네스 개선 범위)
- 계약 테스트는 prod GET 비교로 바뀐다(D-7)
- dev 환경 검증이 없어지므로 로컬 실행(Testcontainers, 로컬 메인)과 배포 전 계약 테스트가 그 자리를 맡는다

처음 비교(참고):

현황:

- 관리자 서버(qa-api)는 dev push로만 배포되고 판정 대상은 메인 dev다 - `[qa] .github/workflows/cd.yml:3-5`, `[qa] .env.example`("현재 dev, 전환 후 prod"), `[qa] docs/deploy-runbook.md` 필요한 값 표
- 메인은 dev(push)와 prod(릴리스 태그) 배포가 따로다 - `[main] .github/workflows/cd-dev.yml`, `cd-prod.yml:20-27`
- 관리자 웹 운영 배포가 어느 메인 환경(`VITE_API_HOST`)을 보는지는 저장소에 없다. 확인 필요
- 옮겨 오는 기능(회원 상태, 공지, 콘텐츠)은 prod 데이터를 다룬다. QA 흐름은 Notion에 쓰므로 두 인스턴스에서 동시에 돌면 안 된다(`NotionWriteGuard`)

| 대안 | 내용 | 평가 |
|---|---|---|
| E1. 인스턴스 하나를 prod로 돌린다 | 지금 인스턴스의 대상을 prod 메인, prod 쪽 DB로 바꾼다 | 관리자 기능을 dev에서 검증할 곳이 없다. 계약 테스트(D-7)를 돌릴 dev 관리자 서버가 사라진다 |
| E2. prod 인스턴스 신설 + 지금 인스턴스는 dev | prod: 릴리스 태그 CD, prod 메인, prod 쪽 관리자 DB. dev: 지금처럼 dev push CD, dev 메인 | 메인과 같은 구도. 계약 테스트는 dev에서. QA 작업 처리는 한쪽에서만 켠다(`qa.worker.job-types`) |
| E3. 환경마다 관리자 DB는 나누되 QA는 dev에만 | E2 + prod 인스턴스는 QA 작업을 끈다 | QA 메뉴가 dev 관리자 서버를 보게 되므로 웹 QA rewrite와 로그인 환경이 맞는지 확인해야 한다 |

처음 추천은 E2였다. 사용자 결정으로 E1을 쓰고, QA 작업과 QA 데이터는 그 인스턴스 하나에 그대로 둔다.

### 12-3. 자원 한도, 네트워크

- 자원 한도: 지금처럼 `deploy.resources.limits` 필수(`QA_API_CPUS`, `QA_API_MEMORY`). 읽기 전용 풀 3개와 HTTP 클라이언트가 더해질 뿐이라 값 변경을 미리 정하지 않는다
- 네트워크: D-5에서 공개 도메인을 고르면 새 네트워크가 없다. 지금 `gravit-qa`, `llm-net` 그대로. 내부 경로를 고르면 `llm-net`(이미 합류) 또는 전용 네트워크

### 12-4. 추가할 환경 변수, 시크릿

| 이름 | 넣는 곳 | 비밀 | 단계 |
|---|---|---|---|
| `MAIN_DB_URL` | 서버 `.env` | 아니오 | 0 |
| `MAIN_DB_USERNAME` | 서버 `.env` | 아니오 | 0 |
| `MAIN_DB_PASSWORD` | 서버 `.env` | 예 | 0 |
| `QA_AUTH_HOST` | 서버 `.env` | 아니오 | 이미 있음. 값을 prod 메인으로 바꾼다. 메인 API 호출도 같은 값을 쓴다 |

CD는 서버 `.env`를 읽지도 쓰지도 않는 규칙을 유지한다(`[qa] .github/workflows/cd.yml:92-97`).

### 12-5. 사람이 서버에서 해야 할 운영 작업

| # | 작업 | 단계 | 비고 |
|---|---|---|---|
| 1 | prod 메인 DB 인스턴스의 `max_connections`와 현재 연결 수 확인(`SHOW max_connections;`, `SELECT count(*) FROM pg_stat_activity;`) | 0 | 읽기 전용 풀 추가 여유 판단 |
| 2 | prod 메인 DB에 읽기 전용 계정 생성, 계정 설정, 1단계 GRANT(6-1 SQL) | 0 | 비밀번호는 `\password` |
| 3 | `pg_hba.conf`가 관리자 컨테이너 주소에서 이 계정 접속을 허용하는지 확인 | 0 | runbook 1단계의 접속 확인과 같은 방식 |
| 4 | 서버 `.env`에 `MAIN_DB_*` 추가(runbook 2단계의 `add_env`) | 0 | 값은 출력하지 않는다 |
| 5 | (C-2) 새 도메인 DNS, Caddy 사이트를 `QA_API_PORT`로 | 0 | 옛 도메인 유지 |
| 6 | 단계마다 GRANT 추가 | 3, 5 | 6-1 목록 |
| 7 | 서버 `.env`의 `QA_AUTH_HOST`를 prod 메인으로 변경 | 0 | QA 메뉴도 prod 관리자 토큰으로 판정된다 |
| 8 | generator 중지, 데이터 복사와 검증, 시퀀스 맞추기, generator 대상 변경 | 5 | 6-2 절차 |
| 9 | 감사 로그 기존 행 복사 | 6 | |
| 10 | 보류 뒤 메인 스테이징, 감사 테이블 백업 덤프 | 5, 6 | DROP 전 |
| 11 | (C-3) 레포, 이미지, 컨테이너, 배포 경로 이름 변경 | 이관 뒤 | 3-2 표 |
| 12 | 관리자 DB(`gravit_qa`)가 있는 Postgres 인스턴스의 백업 확인 | 0 | M-11 |

---

## 13. 미결 사항

| # | 내용 | 관련 |
|---|---|---|
| M-1 | 사람이 요청하지 않은 호출(스케줄러, 작업 처리기)이 메인 API를 불러야 할 때의 인증. 지금 이관 대상 43개에는 없다 | D-6 |
| M-2 | 해소(2026-11-18): 관리자 웹 운영 배포는 prod 메인을 본다. `QA_AUTH_HOST`를 prod로 바꾸면 QA 메뉴와 관리자 기능이 같은 토큰으로 맞는다. dev, prod의 Postgres 인스턴스가 같은지는 여전히 확인 필요(읽기 전용 계정을 만들 때 확인) | D-4, D-10 |
| M-3 | generator 저장소와 적재 방식(대상 DB 변경 가능 여부) | D-4(ii) |
| M-4 | NTC-03 중복 제출 방지를 웹 버튼 잠금 외에 서버에서 할지(메인 멱등 키 지원 여부) | D-5 |
| M-5 | 이관이 끝난 뒤 메인 공개 도메인의 관리자 쓰기 엔드포인트를 막을지 | D-5 |
| M-6 | 관리자 조회 대상 테이블을 바꾸는 메인 PR에 관리자 서버 확인을 거는 메인 쪽 규칙(메인 레포 작업) | D-4(i) |
| M-7 | 감사 로그 조회 API는 spec 12-3에서 범위 밖. 관리자 서버로 옮긴 뒤 필요해지면 `audit` 모듈에 둔다 | D-4(ii) |
| M-8 | 공지 목록 정렬에 id 동점 처리가 없어 같은 시각 행의 페이지 경계 순서가 흔들릴 수 있다. 메인과 같이 고칠지 | D-7 |
| M-9 | 외부 가용성 감시에 QA 도메인이 등록돼 있는지 | D-1 |
| M-10 | `.gitignore`에 이 문서 예외를 둘지 | D-9 |
| M-11 | 관리자 DB(`gravit_qa`)가 있는 인스턴스(메인 dev와 같은 인스턴스)의 백업 정책. 승격 전 콘텐츠와 prod 행위 감사 로그가 이곳에 쌓인다 | D-4(ii) |
| M-12 | 해소(2026-11-18): D-3은 (c) | D-3 |

## 14. 기준 문서 사이 불일치 목록

spec = `[web] docs/spec/03_gravit_admin_api_spec.md`, Zod = `[web] src/features/*/schemas.ts`, 메인 = `[main] src/main/java/gravit/code/` DTO와 동작.

| # | 항목 | spec | Zod | 메인 | 영향 |
|---|---|---|---|---|---|
| 1 | 페이지 응답 필드 | `{page, totalPages, hasNextPage, content}` (2-4). `[web] docs/conventions/api-contract.md`도 같음 | `{page, totalPages, hasNext, contents}` | `{page, totalPages, hasNext, totalElements, contents}` (`global/dto/response/PageResponse.java`) | Zod가 `totalElements`를 버린다. `[web] src/shared/api/types.ts`의 `PaginatedResponse`에도 없다. 관리자 서버는 메인대로 내보낸다 |
| 2 | 오류 본문 | `{code, message}`, code는 `USER_NOT_FOUND` 같은 이름 (2-5) | 관리자 Zod 없음. `types.ts` `ErrorResponse`는 `{error, message: string \| string[]}` | `{error, message}`, `USER_4041`, `NOTICE_4091`, `STAGING_4091` 형식. 검증 오류는 `message`가 배열 | 웹은 상태와 `message` 문자열만 쓴다. spec 코드 이름으로 분기하는 코드를 새로 만들면 깨진다 |
| 3 | 시각 형식 | UTC ISO 8601, `Z` 접미 (5-1 예시, 12-1). `api-contract.md`와 `decisions.md` 기타 확정값도 UTC | `z.string()` | `LocalDateTime` KST 벽시계, 오프셋 없음(`admin/domain/audit/AuditLog.java:63`의 KST 등) | 웹 표시가 브라우저 시간대에 기대고 있다. 관리자 서버가 spec대로 `Z`를 붙이면 화면 시각이 9시간 어긋난다. 메인 형식을 유지한다 |
| 4 | 인증 엔드포인트 | Base `/api/v1/admin` 아래 `/auth/login`, `/auth/refresh`, refresh 토큰은 본문 (2-2, 3-1~3-3) | `auth/schemas.ts`는 `/api/v1/oauth/*` 응답 | `/api/v1/oauth/*`, `/api/v1/auth/reissue`, `/api/v1/auth/logout`. refresh는 HttpOnly 쿠키(`auth/support/RefreshTokenCookieFactory.java`) | spec이 D10 이전 그대로. `api-contract.md`의 "04 stale 조항" 표도 `/api/v1/admin/auth/refresh`를 기준으로 적고 있다 |
| 5 | USER 권한 토큰의 상태 | 2-2 표는 403, v1.1 변경 요약은 "role=USER 차단 401 확정" | - | 403 `A_D_403` (`security/exception/CustomAccessDeniedHandler.java`) | spec 안에서 서로 다르다. 메인은 403, 웹 D8-3도 403 |
| 6 | 문서에 없는 엔드포인트 | `/me`, DAU/MAU, 문의(INQ-01~05) 없음 | 있음 | 있음 | 이 세 묶음은 웹 `decisions.md` D4, D7과 Zod, 메인 DTO가 사실상 계약이다 |
| 7 | 스테이징 주관식 정답 | 단일 객체(D1) | `stagingSubjectiveProblemSchema.answer`는 null 불가 | 정답 행이 없으면 `answer: null` (`admin/dto/response/StagingProblemResponse.java:41-53`, `admin/service/AdminStagingService.java`의 `toProblemResponse`). `answer_staging.problem_id`는 null 허용(V4) | generator가 정답을 빠뜨린 라벨이 있으면 라벨 상세 전체가 Zod 파싱에 실패한다 |
| 8 | 주관식 정답 모양 | 7-12, 7-14, 8-2, 8-6에 `answers` 배열 | 단일 객체 | 단일 객체 | 이미 알려진 차이(`decisions.md` D1이 spec을 stale로 표시) |
| 9 | 공지 수정 응답 | 9-4: 200 성공(본문 언급 없음) | 웹은 본문을 쓰지 않는다. MSW는 빈 200 | 200 + `NoticeDetailResponse` 본문 (`AdminNoticeController.java:55-62`) | 관리자 서버는 메인대로 본문을 그대로 전달한다 |
| 10 | 회원 상태 변경 응답 | 5-3: "변경된 유저 객체 반환 권장" | - | 200 빈 본문 | 영향 없음. 메인대로 |
| 11 | 승격 검증 | 8-7: "백엔드는 추가 검증 없이 처리" | - | 구조 검증 400 `STAGING_4002`, `COMPLETED` 외 값 400 `STAGING_4001` (`AdminStagingPromoteService.java:69-71,123-176`) | 관리자 서버 STG-07(A)은 메인 검증을 그대로 옮긴다 |
| 12 | 감사 대상 | 12-3: 회원 상태는 SUSPENDED, DELETED 변경만, 승격은 `action`만 | - | 모든 상태 변경(ACTIVE 복구 포함) 기록, 승격은 `after_value = COMPLETED` (`AdminUserService.java:75`, `AdminStagingPromoteService.java:120`) | 6단계에서 관리자 서버는 메인 동작을 따른다 |
| 13 | 공지 목록 정렬 | 9-1에 정렬 언급 없음 | - | `pinned desc, publishedAt desc nulls last, createdAt desc` (`notice/repository/NoticeRepository.java:37-42`) | 옮길 때 그대로 재현해야 한다 |
| 14 | 문의 `type`, `status` | 없음 | enum(`BUG_REPORT` 등, `PENDING`, `RESOLVED`) | String(`InquiryListItemResponse`, `InquiryDetailResponse`) | 메인 enum에 값이 늘면 웹 파싱이 실패한다. 형식은 같다 |
| 15 | `decisions.md` 안의 토큰 전달 | - | - | - | "기타 확정값"은 refresh를 "body 전달"로 적고, D10은 HttpOnly 쿠키로 바꿨다. 웹 문서 안의 불일치 |

## 15. 사실과 달랐던 전제

| # | 전제 | 확인한 사실 | 근거 |
|---|---|---|---|
| 1 | admin-web은 `Team-Gravit/gravit-admin-web` | 지금 GitHub 이름은 `Team-Gravit/gravit-admin-client`이고 `gravit-admin-web`은 리다이렉트된다. 같은 레포로 보고 진행했고, 사용자가 직접 이름을 바꿨다고 확인했다(2026-11-18). CLAUDE.md, README의 `gravit-admin-web` 표기는 D-9에서 고친다 | `git remote -v`, `gh repo view Team-Gravit/gravit-admin-web` |
| 2 | 대시보드 이관 파일에 `AdminStatsRepository` 포함 | `AdminStatsRepository`는 챕터 통계(CON-03, `AdminChapterService.java:35,62`)가 쓴다. 대시보드는 `AdminUserRepository`, `StagingLabelRepository`, `AdminReportRepository`, `AdminActiveUserRepository`를 쓴다 | `AdminDashboardService.java:34-37` |
| 3 | 두 서버의 오류 응답 형식이 같다 | 본문 모양은 같지만 예외별 상태, 코드, 메시지가 다르다(9-4 표) | 두 `GlobalExceptionHandler` |
| 4 | 공지 작성에서 이벤트 발행 | 작성할 때 상태가 `PUBLISHED`일 때만 발행한다. `DRAFT`로 만든 뒤 수정으로 게시하면 푸시가 없다 | `AdminNoticeService.java:71-73,78-101` |
| 5 | 산출물 경로 `docs/design/ADMIN_SERVER_DESIGN.md` | `.gitignore`가 `/docs/*`를 무시해 그대로는 커밋되지 않는다. 설정은 바꾸지 않고 이 파일만 `git add -f`로 추적했다 | `.gitignore:15-16`, 커밋 `dd8d90f` |
| 6 | qa-server는 `gravit-qa` 네트워크에 있다 | compose 키는 `gravit-qa`지만 이름 고정이 없어 실제 네트워크는 프로젝트 접두가 붙은 `qa_gravit-qa`다 | `[qa] docker-compose-qa.yml:42-46` |
| 7 | (보충) 메인 운영 Hikari 기본 10 | 맞다. 설정이 없어 기본값이고, 성능 측정 프로필만 60이다 | `application-prod.yml`, `application-perf.yml:17` |
| 8 | (보충) `03_DESIGN.md`, `openapi.yaml`이 레포에 없다 | 맞다. 지운 것이 아니라 #7에서 추적을 끊었고 이력(`384a66e`)에 남아 있다. 로컬에도 없다 | `git log --all -- docs/design/03_DESIGN.md` |
| 9 | (보충) README의 배포 설명 | `api`, `e2e` 두 컨테이너라고 적혀 있지만 compose에는 `qa-api` 하나뿐이다(D13) | `README.md:43-46`, `docker-compose-qa.yml` |
