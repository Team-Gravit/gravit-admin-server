#!/usr/bin/env bash
# CD가 서버에 둘 .env를 만든다. 값은 GitHub Secrets를 환경 변수로 받는다(cd.yml의 "배포 시크릿 확인", "서버 .env 작성" step).
# 인자: 출력 파일 경로. 확인만 할 때는 /dev/null.
# 값이 비었거나 .env에 그대로 쓸 수 없는 문자가 있으면 키 이름만 출력하고 실패한다. 값은 출력하지 않는다.
# 키를 더하거나 빼면 cd.yml의 두 env 블록과 .claude/spec/secret-convention.md 표를 함께 고친다.
set -euo pipefail

KEYS=(
  DOCKER_HUB_USERNAME
  ADMIN_API_PORT
  ADMIN_API_CPUS
  ADMIN_API_MEMORY
  ADMIN_DB_URL
  ADMIN_DB_USERNAME
  ADMIN_DB_PASSWORD
  MAIN_API_HOST
  MAIN_DB_URL
  MAIN_DB_USERNAME
  MAIN_DB_PASSWORD
  FIGMA_TOKEN
  NOTION_TOKEN
  GITHUB_TOKEN
  LITELLM_API_KEY
)

# 따옴표 없이 KEY=value로 써도 compose와 서버의 grep, cut 명령이 같은 값을 읽는 문자만 허용한다.
SAFE_VALUE='^[A-Za-z0-9_./:@%+=,?&~-]+$'

out=${1:?출력 파일 경로가 필요합니다}

missing=()
invalid=()
for key in "${KEYS[@]}"; do
  value=${!key:-}
  if [ -z "$value" ]; then
    missing+=("$key")
  elif ! [[ $value =~ $SAFE_VALUE ]]; then
    invalid+=("$key")
  fi
done

if [ ${#missing[@]} -gt 0 ]; then
  echo "비어 있거나 등록되지 않은 시크릿: ${missing[*]}"
fi
if [ ${#invalid[@]} -gt 0 ]; then
  echo ".env에 쓸 수 없는 문자가 있는 시크릿(영숫자와 _ . / : @ % + = , ? & ~ - 만 허용): ${invalid[*]}"
fi
if [ ${#missing[@]} -gt 0 ] || [ ${#invalid[@]} -gt 0 ]; then
  exit 1
fi

umask 077
: > "$out"
for key in "${KEYS[@]}"; do
  printf '%s=%s\n' "$key" "${!key}" >> "$out"
done
echo "배포 .env 키 ${#KEYS[@]}개 확인"
