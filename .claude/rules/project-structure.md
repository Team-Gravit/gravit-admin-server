---
description: 프로젝트 디렉토리 구조. 새 파일을 생성하거나 패키지 위치를 결정할 때 참조
paths:
  - "src/main/java/**/*.java"
---

## 프로젝트 구조

```
src/main/java/gravit/admin/
├── global/              # 공통 설정, 예외, 어노테이션, 유틸(시간)
├── auth/                # Spring Security 설정, /admin/me 판정 필터·캐시
├── job/                 # 작업 큐(등록·점유·heartbeat·재시도·회수), 작업 처리기 계약
├── adapter/             # 외부 연동: figma, notion, github, openapi, llm
└── qa/                  # QA 흐름
    ├── sprint/          # 스프린트, 연결 상태, 준비 단계·단위 진행, 명세 로드·QA 준비 처리기
    ├── feature/         # 기능 명세 사본, 스프린트 대상 기능(기준 명세), 명세 페이지 해석
    ├── reflection/      # 수정 이력 반영 제안, 승인 시 Notion 반영
    ├── snapshot/        # 수집 스냅샷 (파일 저장, 행)
    ├── checklist/       # 체크 항목, 체크 셀, 셀 이력, 체크리스트 생성
    ├── designReview/    # 디자인 검수 항목, 이력, Figma 시안과 명세 비교
    ├── autoReview/      # 1차 판정: 저장소 코드 선택, 나눠 담기, 판정 (엔티티 없음, 결과는 checklist 셀)
    └── fixReport/       # 파트별 수정사항 파일 (엔티티 없음, 읽기 전용, LLM 없음)
```

관리자 기능 이관 모듈(insight, support, member, content, audit)과 adapter/gravit는 아래 "관리자 서버 모듈 배치"를 따라 해당 단계에서 만든다.

자동 실행(run), 증적(evidence), 리뷰 수집(review), 페르소나(persona) 패키지는 QA 흐름 재설계(D13)로 없앴다.

### 도메인 패키지 내부 구조

도메인이 하나인 모듈은 `{module}/` 바로 아래, `qa`처럼 여럿인 모듈은 `{module}/{domain}/` 아래에 둔다.

```
{module}/ 또는 {module}/{domain}/
├── controller/
│   ├── {Domain}Controller.java
│   └── docs/
│       └── {Domain}ControllerDocs.java   # Swagger 문서 인터페이스
├── service/
│   ├── {Domain}Service.java              # 조회/변경 분리 시 {Domain}QueryService, {Domain}CommandService
│   └── port/                             # 인프라 구현을 가리는 인터페이스, 구현은 infrastructure/ (선택)
├── repository/
│   ├── {Domain}Repository.java           # JPA Repository 인터페이스
│   ├── custom/                           # 커스텀 조회 인터페이스 + JdbcTemplate Impl (선택)
│   └── sql/                              # custom/ Impl이 쓰는 SQL 상수 (선택)
├── domain/
│   └── {Domain}.java                     # Entity, enum
└── dto/
    ├── request/
    ├── response/
    └── internal/                         # 레이어 간 내부 전달용, 쿼리 프로젝션 (선택)
```

필요할 때만 추가하는 선택적 하위 패키지:

- `facade/` - 여러 도메인 Service를 조합할 때 (`@Facade`)
- `support/` - 도메인 보조 로직. `job/support/`에는 작업 실행기(`JobRunner`)와 처리기 계약(`JobHandler`, `JobContext`)을 둔다
- `scheduler/` - 스케줄
- `handler/` - 도메인별 `JobHandler` 구현 (예: `qa/sprint/handler/SpecLoadJobHandler`)
- `infrastructure/` - 외부 연동, `service/port/` 구현
- `config/` - 도메인 전용 설정과 props

### job 도메인 예외

`job`은 조건부 원자 갱신(`UPDATE … WHERE status = 'RUNNING' AND locked_by = ?`)과 `SKIP LOCKED`가 핵심이라
JPA 엔티티 없이 `repository/custom/JobQueueRepository` + `JdbcTemplate`으로만 다룬다. 상태 변경을 JPA로 옮기지 마라.

### 작업 처리기 작성

- 새 작업 처리는 `JobHandler`를 구현한 빈으로 만든다. 유형당 처리기는 하나다
- 단계는 `JobType`에 정의된 이름으로 `context.step("NAME", ...)`을 호출한다. 단계 경계마다 취소 요청을 확인한다
- 재시도해도 소용없는 실패는 `NonRetryableJobException`을 던진다. 그 외 예외는 재시도 대상이다
- 처리기는 같은 입력으로 다시 실행해도 결과가 같아야 한다. 재시도·회수 때 처리기는 처음부터 다시 불리지만, 끝난 단계(`sprint_step`)와 끝난 단위(`prepare_unit`)는 건너뛴다(`SetupStepRunner`)

### 관리자 서버 모듈 배치

근거: `docs/design/ADMIN_SERVER_DESIGN.md` D-2. 아직 없는 모듈은 이관 단계에서 만든다.

```
gravit/admin/
├── global/      # 공통 설정, 예외, 시간, 메인 DB 읽기 전용 데이터소스(MainDbConfig, @MainDb)
├── job/         # 작업 큐 (플랫폼)
├── auth/        # 관리자 판정 필터, /admin/me 판정 캐시 (지금 security)
├── adapter/     # 외부 연동: figma, notion, github, openapi, llm, gravit(메인 서버 API와 /api/v1/admin/** 전달 Controller)
├── qa/          # QA 흐름: sprint, feature, reflection, snapshot, checklist, designReview, autoReview, fixReport
├── insight/     # 대시보드, 통계
├── support/     # 신고, 공지, 문의
├── member/      # 회원
├── content/     # 학습 콘텐츠, 스테이징
└── audit/       # 감사 로그
```

- 모듈 아래 도메인이 하나면 모듈 바로 아래에 `controller/`, `service/` 등을 두고, `qa`처럼 여럿이면 `{module}/{domain}/` 아래에 둔다
- `observability`, `incident`, `ops`는 기능이 생길 때 만든다. 빈 패키지를 미리 만들지 않는다. 새 최상위 패키지는 허용 의존 표와 `ArchitectureTest`에 먼저 올린다(표에 없는 최상위 패키지는 테스트가 실패한다)
- `adapter/`는 외부로 나가는 연동만 둔다. 예외는 `adapter/gravit`의 `/api/v1/admin/**` 전달 Controller 하나다(설계 D-3 (c))

### 모듈 사이 의존

행이 열을 import할 수 있는가. O 허용, X 금지, △ 아래 조건부. `src/test/java/gravit/admin/ArchitectureTest`(ArchUnit)가 강제한다. 표를 바꾸면 테스트의 표와 조건도 함께 고친다.

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

- △1 `adapter → job`: `job.support.NonRetryableJobException` 하나만
- △2 `→ auth`: `auth.principal`(LoginAdmin)과 요청 토큰 보관 타입만. 필터, 캐시는 금지
- △3 `insight → support, member, content`: 각 모듈 `service/*QueryService`의 공개 조회 메서드만. 다른 모듈의 Repository, 테이블 직접 조회는 금지
- △4 `→ audit`: `audit.service.AuditRecorder` 하나만

### 메인 DB 조회

- 조회 방식 기능만 메인 DB를 읽는다. 구현은 `{module}/repository/custom/*RepositoryImpl`에서 `@MainDb JdbcTemplate`만 쓰고 SQL 상수는 `repository/sql/`에 둔다
- 메인 테이블에 JPA 엔티티를 만들지 않는다. 메인 DB에 쓰지 않는다(읽기 전용 계정, `default_transaction_read_only`)
- 메인의 숨은 동작(soft delete 조건, 정렬, 페이지 크기 20, `page < 1` 오류)을 그대로 재현한다. 목록은 설계 6-1
- 테스트 픽스처 DDL 파일 머리에 출처 메인 마이그레이션 파일과 커밋을 적는다

### 메인 API 호출

- `adapter/gravit` 클라이언트만 쓴다. 받은 관리자 토큰을 `Authorization`에 그대로 붙인다
- 쓰기 요청(POST, PUT, PATCH, DELETE)은 자동 재시도하지 않는다. 연결 실패는 502 `GRAVIT_5021`, 응답 타임아웃은 504 `GRAVIT_5041`
- 메인 응답은 상태, `Content-Type`, 본문 바이트를 그대로 돌려준다. 역직렬화 후 다시 직렬화하지 않는다
