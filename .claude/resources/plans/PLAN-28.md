# [PLAN-28] 관리자 서버 전환을 위한 경계와 계약 설계

> 이슈: #28
> 브랜치: docs/28-admin-server-design

## 목표
gravit-qa-server를 그래빗 관리자 서버로 바꾸고 gravit-server admin 기능 43개를 도메인 단위로 옮기기 전에, 경계(모듈, 데이터 소유)와 계약(웹 경로, 메인 API, 인증 전달, 응답 형식)을 확정하는 설계 문서를 만든다.
구현은 하지 않는다. 되돌리기 어려운 항목은 추천안만 적고 사람 결정으로 남긴다.

## 점검 결과 (계획 근거)

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | 기준 커밋 | qa-server main `45c5060`, gravit-server dev `0d3f9a3`, admin-web main `16cd54e`. 세 레포 모두 원격과 같고 커밋하지 않은 변경 없음 | `git rev-parse`, `git status --porcelain` |
| 2 | admin-web 레포 이름 | 원격 URL은 `Team-Gravit/gravit-admin-client`. `gh repo view Team-Gravit/gravit-admin-web`이 같은 레포로 이어져(이름 변경 리다이렉트) 같은 레포로 판단 | `gh repo view` |
| 3 | 산출물 경로 | `.gitignore`가 `/docs/*`를 무시하고 `deploy-runbook.md`만 예외. 설정을 바꾸지 않고 `git add -f`로 이 문서 하나만 추적한다. 저장소는 PUBLIC이라 비밀값, 개인 정보는 넣지 않는다 | `.gitignore:15-16`, `dd8d90f`, `gh repo view --json visibility` |
| 4 | 깨진 참조 | `03_DESIGN.md`, `openapi.yaml`은 `dd8d90f`(#7)에서 추적 해제됐고 로컬에도 없다 | `git log --all -- docs/design/03_DESIGN.md` |

## 영향 범위
### 신규 파일
- `docs/design/ADMIN_SERVER_DESIGN.md` - 관리자 서버 경계, 계약 설계(D-1 ~ D-10, 미결, 불일치 목록)
- `.claude/resources/plans/PLAN-28.md` - 이 계획서

### 수정 파일
- 없음. 프로덕션 코드, 테스트, 설정, Flyway, `.claude/CLAUDE.md`, `README.md`, rules는 바꾸지 않는다(D-9는 변경안만 문서에 적는다)

## 구현 계획
> 문서 작업이라 레이어별 계획 대신 문서 구성과 항목별 필수 요소를 적는다.
1. **머리말**: 세 레포 기준 브랜치, 커밋, 로컬 상태. 결정 요약 표(`| ID | 결정 | 선택안 | 사람 결정 필요 | 되돌리는 방법 |`)
2. **기능 ID 표**: 43개 기능 ID를 메인 `admin/controller`의 메서드, 경로, 이관 방식(소유, API, 조회)에 대응
3. **D-1 ~ D-10 본문**: 항목마다 (1) 현황과 근거(레포 기준 상대 경로) (2) 대안 2개 이상 (3) 비교 기준과 결과 (4) 선택과 이유 (5) 되돌리는 방법. 차이가 작거나 되돌리기 어려운 항목은 "사람 결정 필요"로 표시
4. **D-8 대시보드 이관 계획**: 메인 파일(Controller, Docs, Service, Repository, DTO, 테스트), 관리자 서버 신규 파일, 웹 파일까지 실제 경로로 채움
5. **D-9 하네스 개정안**: `.claude/CLAUDE.md`, `README.md`, `.claude/rules/project-structure.md`의 전, 후 문안
6. **맺음**: 미결 사항, 기준 문서(웹 명세 spec/03, Zod, 메인 DTO) 사이 불일치 목록, 사실과 달랐던 전제

## 결정 필요 (Decisions needed)
> 설계 문서 안의 "사람 결정 필요" 항목은 이번 PR에서 확정하지 않고 문서와 PR에 추천안으로 남긴다. 계획 단계에서 따로 확정할 항목은 없다.

## 검증
- 문서가 인용한 파일 경로가 각 레포 기준 커밋에 실제로 있는지 `git ls-tree`로 확인한다
- 기능 ID 43개가 모두 표에 있고 중복이 없는지 확인한다
- 결정 요약 표와 본문 선택안이 일치하는지 확인한다

## Deviation Log
> implement 스킬이 구현 중 계획을 벗어난 지점을 여기에 기록한다. (작성 시점엔 비워둔다)
