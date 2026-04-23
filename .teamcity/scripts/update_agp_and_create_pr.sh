#!/bin/bash
set -e

# Script to update AGP versions and create a pull request
#
# Usage:
#   ./update_agp_and_create_pr.sh
#
# Environment variables:
#   DEFAULT_BRANCH  - The default branch to create the pull request on (e.g. "master")
#   GITHUB_TOKEN    - GitHub bot token

post() {
    local endpoint="$1"
    local data="$2"

    local response=$(curl -X POST \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        -H "Content-Type: application/json" \
        "https://api.github.com/repos/gradle/gradle$endpoint" \
        -d "$data" \
        -w "\n%{http_code}" \
        2>/dev/null)

    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n -1)

    if [[ "$http_code" -ge 400 ]]; then
        printf "Error: HTTP %s - %s\n" "$http_code" "$body" >&2
        exit 1
    fi

    echo "$body"
}

main() {
    : "${DEFAULT_BRANCH:?DEFAULT_BRANCH environment variable is required}"
    : "${GITHUB_TOKEN:?GITHUB_TOKEN environment variable is required}"

    ./gradlew updateAgpVersions

    git add gradle/dependency-management/agp-versions.properties
    git add platforms/documentation/docs/src/docs/userguide/releases/compatibility.adoc

    if git diff --cached --quiet; then
        echo "No changes to commit"
        exit 0
    fi

    BRANCH_NAME="tide/update-agp-$(date +%Y%m%d-%H%M%S)"
    git switch -c $BRANCH_NAME
    git commit --signoff --author="bot-gradle <bot-gradle@gradle.com>" -m "Update AGP versions"
    git push https://${GITHUB_TOKEN}@github.com/gradle/gradle.git $BRANCH_NAME

    PR_TITLE="Update tested AGP versions"

    PR_RESPONSE=$(post "/pulls" "{
        \"title\": \"$PR_TITLE\",
        \"body\": \"$PR_TITLE\",
        \"head\": \"$BRANCH_NAME\",
        \"base\": \"$DEFAULT_BRANCH\"
    }")

    echo "PR_RESPONSE: $PR_RESPONSE"

    PR_NUMBER=$(echo "$PR_RESPONSE" | jq -r '.number' 2>/dev/null)

    post "/issues/$PR_NUMBER/comments" '{
        "body": "@bot-gradle test and merge"
    }'

    post "/issues/$PR_NUMBER/labels" '{
        "labels": ["a:chore", "in:performance-test", "in:smoke-test", "re:android"]
    }'

    post "/pulls/$PR_NUMBER/requested_reviewers" '{
        "team_reviewers": ["bt-tide"]
    }'
}

main "$@"
