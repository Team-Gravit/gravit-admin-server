---
name: open-pr
description: |
  PR을 생성한다. 본문은 PR 템플릿의 두 구획(Related Issue, Implementation)으로 쓰고 담당자, 라벨, 리뷰어를 지정한다.
  Trigger: "PR 날려줘", "PR 만들어줘", "PR 생성해줘", "PR 올려줘"
  Do NOT use for: 커밋(→ commit-push), 이슈, 브랜치 생성(→ open-issue)
  Boundary: PR 생성과 담당자, 라벨, 리뷰어 지정까지만 한다. 머지는 하지 않는다.
argument-hint: "[base 브랜치]"
allowed-tools: Read, Write, Bash(git *), Bash(gh *)
model: sonnet
effort: medium
---

## PR 생성

PR은 두 가지만 담는다. 닫을 이슈(`## Related Issue`), 이슈의 Plan을 어떻게 구현했는가(`## Implementation`).

### 시작 조건

1. 현재 브랜치 `{type}/{번호}-{slug}`에서 type과 이슈 번호를 얻는다. 번호가 없으면 묻는다
2. `gh pr view --json url,state`로 이 브랜치에 열린 PR이 있으면 그 URL을 알리고 끝낸다
3. 원격 브랜치가 없거나 `git rev-list --count @{u}..HEAD`가 0보다 크면 push되지 않은 커밋이 있다고 알리고 `commit-push`를 권한 뒤 멈춘다
4. base는 $ARGUMENTS가 있으면 그것, 없으면 git-convention 브랜치 전략의 base(지금 `dev`)

### Phase 1: 재료 모으기

1. `gh issue view {번호} --json title,body,labels`로 이슈를 읽는다. 이슈의 Issue와 Plan이 본문의 기준이다
2. `git log --oneline {base}..HEAD`, `git diff --stat {base}...HEAD`로 실제로 한 작업을 확인한다. 내용이 필요한 파일만 diff로 본다
3. 로컬 계획서 `.claude/resources/plans/PLAN-{번호}.md`(git 추적 안 함)가 있으면 Deviation Log를 읽는다
4. 대화에서 정한 결정 중 이슈에 없는 것이 있으면 메모한다

### Phase 2: 제목, 본문 작성

1. 제목: `{type}: {설명}(#{번호})`. git-convention 제목 규칙을 따른다
2. 본문: `.github/PULL_REQUEST_TEMPLATE.md`의 구획을 그대로 쓴다. HTML 주석은 넣지 않는다. 구조, 분량, 말투는 `template/example.md`를 따른다
   - Related Issue: `- close #{번호}` 한 줄만
   - Implementation: 작업 전 상황 한두 문장, 이어서 Plan을 실제로 어떻게 구현했는지 순서대로 3~6줄. Plan과 달라진 점이 있으면 마지막에 `> [!NOTE]` 콜아웃으로 이유와 함께 한 줄
   - 문단, 목록, 콜아웃, 구획 사이에는 `<br>`을 한 줄 둔다. 목록 항목 사이에는 두지 않는다
3. 쓰지 않는 것: 변경 파일과 메서드 나열(diff가 보여준다), 커밋 메시지 복사, 템플릿에 없는 구획, 문제와 해결을 부풀린 서술
4. 문체: 존댓말(`~했습니다`). git-convention 표기 규칙(가운데점 대신 콤마, 긴 대시 대신 짧은 대시)
5. 본문은 세션 스크래치패드에 파일로 저장한다

### Phase 3: 생성

1. 리뷰어: git-convention 팀 로스터에서 호출자(`gh api user --jq .login`)를 뺀 나머지. 남는 사람이 없으면 지정하지 않는다
2. 라벨: 이슈의 라벨을 그대로 쓴다

   ```bash
   gh pr create --title "{제목}" --body-file {본문 파일} --base {base} \
     --assignee @me --label "{라벨}" [--reviewer {리뷰어1},{리뷰어2}]
   ```

   - 리뷰어 지정이 실패해도 PR은 되돌리지 않는다. `gh pr edit {번호} --add-reviewer {리뷰어}`로 한 번 다시 시도하고, 그래도 실패하면 보고에 남긴다

### Phase 4: 결과 보고

`template/output.md`를 Read해 채워 보고한다. 주석은 출력하지 않는다.
