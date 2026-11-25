---
description: 테스트 코드 작성 규칙 (통합 테스트 중심)
paths:
  - "src/test/java/**"
---

## Test Convention

테스트는 통합 테스트(`@QaSpringBootTest`)로 작성한다. `@ExtendWith(MockitoExtension.class)` 기반 단위 테스트는 새로 만들지 않는다.

### 네이밍 & 설정

- 파일명: `{Class}IntegrationTest.java`
- 클래스 어노테이션: `@QaSpringBootTest`. 아래 구성이 함께 들어온다
  - PostgreSQL: 환경 변수 `ADMIN_TEST_DB_URL`(+`ADMIN_TEST_DB_USERNAME`, `ADMIN_TEST_DB_PASSWORD`)이 있으면 그 DB, 없으면 Testcontainers (`PostgresTestConfig`)
  - 조작 가능한 시계 `TestClock` (`@Primary`, 기준 `2026-10-16T12:00+09:00`). `advance(Duration)`으로 시간을 진행한다
  - `/admin/me` 스텁 서버 `AdminMeStubServer` (`qa.auth.host`가 스텁을 가리킨다)
  - `DatabaseClearExtension`: 매 테스트 전에 public 스키마와 메인 DB 픽스처 스키마(`maindb`) TRUNCATE(시퀀스 초기화), 시계·스텁·인증 캐시 초기화
  - `test` 프로필: 스케줄러 비활성(`qa.worker.enabled=false`). 작업은 테스트에서 `JobRunner.runNext()`로 직접 실행한다
- 의존성 주입: `@Autowired`
- Controller 테스트: `@AutoConfigureMockMvc` + `MockMvc`. 인증은 `Authorization: Bearer …` 헤더를 넣고 스텁 응답으로 제어한다
- 검증 라이브러리는 AssertJ (`assertThat`, `assertSoftly`, `assertThatThrownBy`)
- 테스트 패키지는 `src/main/java`의 도메인 구조를 미러링하라

### 목(mock)과 스텁

- 외부 HTTP 연동은 JDK `HttpServer` 스텁으로 대체한다 (`AdminMeStubServer` 참고). 실제 외부 서비스를 호출하지 마라
- 작업 처리기의 동작을 바꿀 때는 `@MockitoSpyBean`으로 실제 처리기의 `handle`을 바꾼다 (`JobQueueIntegrationTest` 참고)
  - 모든 작업 유형(`SPEC_LOAD`, `QA_PREPARE`)에 실제 처리기가 있으므로 스텁 처리기 빈을 따로 등록하지 마라. 유형당 처리기가 둘이 되어 `JobRunner` 기동이 실패한다
  - 별도 설정 클래스는 `@Import`로 등록한다(`@Nested`와 함께 쓰면 중첩 `@TestConfiguration`이 컨텍스트를 분리시킨다)
- 실패 주입·호출 검증 용도에 한해 `@MockitoBean`, `@MockitoSpyBean`을 쓴다

### 메인 DB 픽스처

메인 DB 조회(`@MainDb JdbcTemplate`) 테스트는 테스트 DB 안의 별도 스키마 `maindb`를 메인 DB로 쓴다(`TestDatabase`). 관리자 DB는 같은 DB의 public이다.

- DDL: `src/test/resources/maindb/`에 메인 테이블 하나당 파일 하나(`V{순서}__{테이블}.sql`). JVM마다 한 번 `maindb`를 지우고 다시 만든 뒤 파일 이름 순서로 실행한다. 다른 테이블을 참조하면 참조되는 파일의 순서 번호를 앞에 둔다
- 파일 머리 주석에 출처 메인 마이그레이션 파일과 gravit-server 커밋을 적는다(`-- 출처: gravit-server {커밋} V1__init.sql, V42__...sql`). 메인에서 그 테이블을 바꾸는 마이그레이션이 생기면 이 파일과 머리말을 함께 고친다
- 조회에 쓰는 컬럼, 제약, 기본값만 옮겨도 된다. 인덱스와 확장(`pg_trgm`)은 조회 결과에 영향이 없으면 뺀다
- 행: 테스트에서 `MainDbFixtureWriter`로 넣는다. 앱의 `@MainDb` 연결은 읽기 전용이라 쓰지 못한다. 행은 `DatabaseClearExtension`이 테스트마다 비운다
- 도메인 테스트에서 메인 테이블을 만들거나 지우지 않는다. 구조는 DDL 파일로만 정한다(예외: 픽스처 방식 자체를 검사하는 `support/MainDbFixtureIntegrationTest`)

### 계약 테스트 (prod 응답 비교)

전환(헤더 조건 삭제) 조건인 prod 메인과 관리자 서버의 GET 응답 비교다(설계 9-2).

- 클래스에 `@ContractLiveTest`를 붙인다(`@Tag("live")`, 환경 변수 셋 중 하나라도 없으면 건너뜀). 이름은 `{Domain}ContractLiveTest`, 위치는 대상 모듈의 `contract/` 패키지
- `LiveContractClient`로 같은 경로를 메인과 관리자 서버에 보내고(관리자 서버에는 `X-Admin-Route: local`), `ContractComparator.compare` 결과가 비었는지 본다. 비교는 상태와 JSON 트리이며 키 순서만 무시한다
- GET만 보낸다. 쓰기 API는 응답 바이트 전달 테스트(전달 계층 통합 테스트)로 확인한다
- 요청 표: 기본값, 첫 페이지, 마지막 페이지 다음, 필터 조합, 없는 ID(404), 잘못된 파라미터(400), `page=0`
- 실행: `ADMIN_CONTRACT_MAIN_BASE`, `ADMIN_CONTRACT_ADMIN_BASE`, `ADMIN_CONTRACT_TOKEN`을 셸에 두고 `./gradlew liveTest --tests '{클래스}'`. 변수 없이 클래스를 골라 돌리면 Gradle이 "No tests found"로 실패한다(클래스 단위로 건너뛰어서다)
- 허용 차이는 설계 9-4 "의도한 차이"뿐이다. 그 경우는 요청 표에서 빼고 이유를 테스트 주석에 적는다

### 순수 JUnit 테스트

Spring 의존 없이 입력만으로 결과가 정해지는 domain, util, support 로직은 순수 JUnit으로 작성해도 된다. 파일명은 `{Class}Test.java`로 한다.

### 메서드 작성

- 메서드명은 한글 서술형 (`성공하면_SUCCEEDED가_되고_단계가_DONE이_된다()`)
- `@Nested` + `@DisplayName`으로 그룹화 (`@DisplayName("작업을 취소할 때")`)
- 본문은 `// given` / `// when` / `// then` 주석으로 구간을 구분하라
  - 준비 단계가 없으면 `// given`을 생략한다
  - 실행과 검증이 한 문장이면 `// when & then`으로 합친다 (`assertThatThrownBy`)

### 예외 검증

`RestApiException`을 던지는 예외 케이스는 반드시 `errorCode`까지 검증하라.

```java
import static gravit.admin.global.exception.domain.CustomErrorCode.JOB_NOT_FOUND;

assertThatThrownBy(() -> jobCommandService.requestCancel(999L))
        .isInstanceOf(RestApiException.class)
        .extracting(e -> ((RestApiException) e).getErrorCode())
        .isEqualTo(JOB_NOT_FOUND);
```

### Fixture

| 항목 | 규칙 |
|---|---|
| 위치 | `src/test/java/gravit/admin/{module}/fixture/` (`qa`는 `{module}/{domain}/fixture/`) |
| 메서드명 | 한글로 시나리오 표현 (`SprintFixture.준비_전_스프린트()`) |
| static 팩토리 fixture | 저장하지 않은 Entity나 DTO를 반환하는 `{Domain}Fixture` 클래스 |
| 저장형 fixture | `@TestComponent` + `@RequiredArgsConstructor`로 Repository를 주입받아 저장까지 하는 fixture |
