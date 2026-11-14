#!/usr/bin/env bash
# Merges an official SmartTube release into this fork, keeping the Premium changes.
# Usage: premium/merge-upstream.sh <git ref to merge> <release name>
set -euo pipefail
REF="$1"; NAME="$2"

git config user.name "github-actions[bot]"
git config user.email "41898+github-actions[bot]@users.noreply.github.com"

git merge --no-commit --no-ff "$REF" || true

# Keep this fork's own .github folder: SmartTube's workflow changes are skipped
# (automatic jobs aren't allowed to push workflow changes, and they aren't used here).
for path in $( { git diff --name-only HEAD -- .github; git diff --name-only --diff-filter=U -- .github; } | sort -u ); do
  if git cat-file -e "HEAD:$path" 2>/dev/null; then
    git checkout HEAD -- "$path"
  else
    git rm -f -q --ignore-unmatch "$path"
  fi
done
for path in $(git diff --cached --name-only --diff-filter=A -- .github); do
  git rm -f -q --cached "$path"; rm -f "$path"
done

CONFLICTS=$(git diff --name-only --diff-filter=U)
if [ -n "$CONFLICTS" ]; then
  echo "::error::SmartTube $NAME changed the same lines as the Premium changes. Nothing was changed. Files:"
  echo "$CONFLICTS"
  git merge --abort
  exit 1
fi

git commit -q -m "Update to SmartTube $NAME (Premium changes kept)"
echo "Merged SmartTube $NAME"
