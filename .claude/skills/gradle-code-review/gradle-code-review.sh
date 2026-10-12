#!/usr/bin/env bash
#
# Copyright 2026 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0

# Companion script for the gradle-code-review skill.
#
# All git access the review needs goes through here, so the skill only
# requires permission to run this one script rather than each individual
# git command. Read-only: it never mutates the repo.
#
# Usage:
#   gradle-code-review.sh scope [TARGET_REF]         Summary of what is in scope
#   gradle-code-review.sh diff  [TARGET_REF]         Full review diff (committed + working tree + untracked)
#   gradle-code-review.sh log   PATH [TARGET_REF]    Commit history with patches for a path
#   gradle-code-review.sh blame PATH                 git blame for a path (full file, intentionally unscoped)
#
# TARGET_REF is the branch the change is destined for on gradle/gradle
# (github.com). That remote is located by URL, so its name need not be
# "origin". When TARGET_REF is omitted it is detected as:
#   1. the base branch of an open PR for the current branch (via `gh`), if any;
#   2. otherwise the inferred fork parent among the likely integration
#      branches — master, release, then release<N>x (newest first) — chosen by
#      which one HEAD actually forked from (the most recent merge-base);
#      candidate order only breaks ties.
# The review range is <merge-base(TARGET_REF, HEAD)>..HEAD — everything since
# the fork point, plus (for `diff`) uncommitted and untracked changes.
set -euo pipefail

# Run git with a fixed configuration so the output does not depend on the
# user's global/local git config (custom log formats, external diff/pager
# tools, colouring, prefix styles, …). Every git invocation below goes
# through this wrapper; per-command flags (--no-ext-diff, --no-textconv,
# explicit --pretty) cover the knobs that cannot be forced via -c.
git() {
  command git \
    -c core.pager=cat \
    -c color.ui=false \
    -c diff.external= \
    -c diff.mnemonicPrefix=false \
    -c diff.noprefix=false \
    -c diff.relative=false \
    -c log.showSignature=false \
    -c log.decorate=false \
    -c format.pretty=medium \
    -c status.relativePaths=false \
    -c blame.showEmail=false \
    -c blame.showRoot=false \
    "$@"
}

usage() {
  # Print the header comment block as usage, minus the shebang line.
  grep '^#' "$0" | grep -v '^#!' | sed 's/^# \{0,1\}//'
}

# --- Target-branch detection -------------------------------------------------

has_remote() { git remote get-url "$1" >/dev/null 2>&1; }

# Remote whose URL points at github.com/gradle/gradle, regardless of its name
# (matches git@github.com:gradle/gradle.git and https://github.com/gradle/gradle).
gradle_remote() {
  local name url
  while read -r name url; do
    if [[ "$url" =~ github\.com[:/]gradle/gradle(\.git)?/?$ ]]; then
      printf '%s' "$name"; return
    fi
  done < <(git remote -v | awk '$3 == "(fetch)" { print $1, $2 }')
}

# Fallback when no remote is clearly gradle/gradle: upstream, else origin, else
# the first configured remote.
fallback_remote() {
  local r
  for r in upstream origin; do has_remote "$r" && { printf '%s' "$r"; return; }; done
  git remote | head -n1
}

# The remote we treat as the gradle/gradle target repository.
target_remote() {
  local r; r="$(gradle_remote)"
  [[ -n "$r" ]] && { printf '%s' "$r"; return; }
  fallback_remote
}

# Map a plain branch name (e.g. a PR base) to a tracking ref, preferring the
# target remote, then any remote, then a local branch.
resolve_branch_ref() {
  local name="$1" r
  for r in "$(target_remote)" upstream origin $(git remote); do
    [[ -n "$r" ]] || continue
    if git rev-parse --verify --quiet "refs/remotes/$r/$name" >/dev/null; then
      printf '%s' "$r/$name"; return
    fi
  done
  git rev-parse --verify --quiet "refs/heads/$name" >/dev/null && { printf '%s' "$name"; return; }
  printf '%s' "$name"   # last resort: let base_for validate/fail with context
}

# Ordered candidate target refs on the target remote: master, release, then
# release<N>x (newest first). Only existing branches are emitted.
candidate_targets() {
  local remote="$1" c
  for c in master release; do
    git rev-parse --verify --quiet "refs/remotes/$remote/$c" >/dev/null && printf '%s\n' "$remote/$c"
  done
  # release<N>x, sorted by N descending (portable: prefix number, numeric sort).
  git for-each-ref --format='%(refname:short)' "refs/remotes/$remote/release*" \
    | sed "s#^$remote/##" \
    | grep -E '^release[0-9]+x$' \
    | sed -E 's/^release([0-9]+)x$/\1 &/' \
    | sort -k1,1 -nr \
    | awk -v R="$remote" '{ print R"/"$2 }'
}

# Print "<ref>\t<source>" for the detected target, or nothing.
detect_target() {
  # 1) Definitive: the base branch of an open PR for the current branch.
  if command -v gh >/dev/null 2>&1; then
    local base_ref
    base_ref="$(gh pr view --json baseRefName -q .baseRefName 2>/dev/null || true)"
    if [[ -n "$base_ref" ]]; then
      printf '%s\t%s' "$(resolve_branch_ref "$base_ref")" "open PR base branch"
      return
    fi
  fi

  # 2) No PR: infer the fork parent. Among the candidates, the branch HEAD
  #    forked from is the one whose merge-base with HEAD is the most recent
  #    commit; ties fall to candidate order (master > release > newer release<N>x).
  local remote; remote="$(target_remote)"
  [[ -n "$remote" ]] || return

  local best="" best_base="" cand mb
  while IFS= read -r cand; do
    mb="$(git merge-base "$cand" HEAD 2>/dev/null || true)"
    [[ -n "$mb" ]] || continue
    if [[ -z "$best" ]]; then
      best="$cand"; best_base="$mb"
    elif [[ "$mb" != "$best_base" ]] && git merge-base --is-ancestor "$best_base" "$mb"; then
      best="$cand"; best_base="$mb"
    fi
  done < <(candidate_targets "$remote")

  [[ -n "$best" ]] && printf '%s\t%s' "$best" "inferred fork parent (no PR)"
}

# Resolve the effective target from an optional explicit argument, setting the
# globals `target` and `target_source`. Errors out if nothing can be resolved.
target=""
target_source=""
resolve() {
  local explicit="${1:-}" out
  if [[ -n "$explicit" ]]; then
    target="$explicit"; target_source="explicit argument"; return
  fi
  out="$(detect_target)"
  if [[ -z "$out" ]]; then
    echo "gradle-code-review.sh: could not auto-detect a target branch (no open PR, and no candidate branch found on the gradle/gradle remote). Pass TARGET_REF explicitly, e.g. 'scope upstream/master'." >&2
    exit 2
  fi
  target="${out%%$'\t'*}"
  target_source="${out#*$'\t'} (auto-detected)"
}

base_for() {
  local target="${1:-}"
  git rev-parse --verify --quiet "$target^{commit}" >/dev/null || {
    echo "gradle-code-review.sh: unknown target ref '$target' — fetch it or pass the correct branch (e.g. origin/master)" >&2
    exit 2
  }
  git merge-base "$target" HEAD || {
    echo "gradle-code-review.sh: could not determine a merge base with '$target' (no common ancestor with HEAD?)" >&2
    exit 2
  }
}

# --- Commands ----------------------------------------------------------------

cmd="${1:-}"
[[ -n "$cmd" ]] || { usage >&2; exit 2; }
shift

case "$cmd" in
  scope)
    resolve "${1:-}"
    base="$(base_for "$target")"
    echo "Target branch : $target [$target_source]"
    echo "Fork point    : $base"
    echo
    echo "== Commits since fork point =="
    git log --no-decorate --abbrev=12 --pretty=tformat:'%h %s' "$base"..HEAD
    echo
    echo "== Files changed (committed) =="
    git diff --no-ext-diff --stat=200 "$base"..HEAD
    echo
    echo "== Working tree (uncommitted) =="
    if [[ -n "$(git status --porcelain)" ]]; then
      git status --porcelain
    else
      echo "clean"
    fi
    ;;

  diff)
    resolve "${1:-}"
    base="$(base_for "$target")"
    echo "### Committed changes ($base..HEAD, target $target)"
    git diff --no-ext-diff --no-textconv "$base"..HEAD
    echo
    echo "### Uncommitted changes (working tree vs HEAD)"
    git diff --no-ext-diff --no-textconv HEAD
    echo
    echo "### Untracked files (shown as new-file additions)"
    if [[ -z "$(git ls-files --others --exclude-standard)" ]]; then
      echo "none"
    else
      # Diff each untracked file against the empty file so its full content
      # is shown as an addition. --no-index reports differences with exit 1,
      # which is expected here, so swallow it. Read-only: nothing is staged.
      while IFS= read -r -d '' f; do
        printf '\n--- new file: %s\n' "$f"
        git diff --no-ext-diff --no-textconv --no-index -- /dev/null "$f" || true
      done < <(git ls-files --others --exclude-standard -z)
    fi
    ;;

  log)
    path="${1:?PATH required}"
    resolve "${2:-}"
    base="$(base_for "$target")"
    git log -p --no-ext-diff --no-textconv --pretty=medium "$base"..HEAD -- "$path"
    ;;

  blame)
    path="${1:?PATH required}"
    git blame -- "$path"
    ;;

  *)
    echo "Unknown command: $cmd" >&2
    usage >&2
    exit 2
    ;;
esac
