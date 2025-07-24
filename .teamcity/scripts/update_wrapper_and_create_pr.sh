#!/bin/bash
set -ex

# Script to update Gradle wrapper and create a pull request
# 
# Usage:
#   ./update_wrapper_and_create_pr.sh [wrapper_version]
#
# Arguments:
#   wrapper_version - The Gradle version to update the wrapper to
# 
# Environment variables:
#   DEFAULT_BRANCH  - The default branch to create the pull request on (e.g. "master"/"release")
#   GITHUB_TOKEN    - GitHub bot token
#   TRIGGERED_BY    - Optional. If it's "Release - Final", version will be from version-info-final-release/version-info.properties
#                     If it's "Release - Release Candidate", version will be from version-info-release-candidate/version-info.properties

post() {
    local endpoint="$1"
    local data="$2"

    echo "POST $endpoint"
    
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
    WRAPPER_VERSION="${1:-}"

    : "${DEFAULT_BRANCH:?DEFAULT_BRANCH environment variable is required}"
    : "${GITHUB_TOKEN:?GITHUB_TOKEN environment variable is required}"
    
    if [[ "$TRIGGERED_BY" == *"Release - Final"* ]]; then
        source version-info-final-release/version-info.properties
        export WRAPPER_VERSION="$promotedVersion"
    elif [[ "$TRIGGERED_BY" == *"Release - Release Candidate"* ]]; then
        source version-info-release-candidate/version-info.properties
        export WRAPPER_VERSION="$promotedVersion"
    fi

    ./gradlew wrapper --gradle-version=$WRAPPER_VERSION 
    ./gradlew wrapper
    git add gradle && git add gradlew && git add gradlew.bat
    
    if git diff --cached --quiet; then
        echo "No changes to commit"
        exit 0
    fi
    
    BRANCH_NAME="devprod/update-wrapper-$(date +%Y%m%d-%H%M%S)"
    git switch -c $BRANCH_NAME
    git commit --signoff --author="bot-gradle <bot-gradle@gradle.com>" -m "Update Gradle wrapper to version $WRAPPER_VERSION"
    git push https://${GITHUB_TOKEN}@github.com/gradle/gradle.git $BRANCH_NAME
    
    PR_TITLE="Update Gradle wrapper to version $WRAPPER_VERSION"
    
    PR_RESPONSE=$(post "/pulls" "{
        \"title\": \"$PR_TITLE\",
        \"body\": \"$PR_TITLE\",
        \"head\": \"$BRANCH_NAME\",
        \"base\": \"$DEFAULT_BRANCH\"
    }")

    echo "PR_RESPONSE: $PR_RESPONSE"

    PR_NUMBER=$(echo "$PR_RESPONSE" | jq -r '.number')

    post "/issues/$PR_NUMBER/comments" '{
        "body": "@bot-gradle test and merge"
    }' 
        
    post "/issues/$PR_NUMBER/labels" '{
        "labels": ["@dev-productivity"]
    }'
}

main "$@"
