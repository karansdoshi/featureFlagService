#!/usr/bin/env bash
#
# Push this repository to GitHub. The build environment had no gh CLI and no credentials,
# so the remote/push was deferred to you (see DECISIONS.md / D-015).
#
# Usage:
#   scripts/push.sh <git-remote-url> [branch]      # add 'origin' and push (branch default: main)
#   scripts/push.sh --create <owner>/<repo>        # create the repo via an authenticated gh CLI, then push
#
# Examples:
#   scripts/push.sh git@github.com:you/feature-flag-service.git
#   scripts/push.sh https://github.com/you/feature-flag-service.git
#   scripts/push.sh --create you/feature-flag-service
set -euo pipefail

if [ "${1:-}" = "--create" ]; then
  REPO="${2:?Usage: scripts/push.sh --create <owner>/<repo>}"
  if ! command -v gh >/dev/null 2>&1; then
    echo "error: gh CLI not found. Install it or use: scripts/push.sh <git-remote-url>" >&2
    exit 1
  fi
  gh repo create "$REPO" --private --source . --remote origin --push
  echo "Created and pushed to $REPO"
  exit 0
fi

REMOTE_URL="${1:?Usage: scripts/push.sh <git-remote-url> [branch]}"
BRANCH="${2:-main}"

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE_URL"
else
  git remote add origin "$REMOTE_URL"
fi

git push -u origin "$BRANCH"
echo "Pushed '$BRANCH' to $REMOTE_URL"
