#!/bin/bash
set -e

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
    
    echo "Making POST request to: $endpoint"
    echo "Data: $data"
    
    echo "Executing curl command..."
    local response=$(curl -X POST \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        -H "Content-Type: application/json" \
        "https://api.github.com/repos/gradle/gradle$endpoint" \
        -d "$data" \
        -w "\n%{http_code}" \
        2>/dev/null)
    
    echo "Raw curl response: $response"
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n -1)
    
    echo "Extracted HTTP code: $http_code"
    echo "Extracted body: $body"
    
    echo "HTTP Status: $http_code"
    echo "Response: $body"
    
    if [[ "$http_code" -ge 400 ]]; then
        echo "Error: HTTP $http_code"
        return 1
    fi
    
    if [[ -z "$body" ]]; then
        echo "Error: Empty response body"
        return 1
    fi
    
    echo "$body"
}

main() {
    WRAPPER_VERSION="${1:-}"

    : "${DEFAULT_BRANCH:?DEFAULT_BRANCH environment variable is required}"
    : "${GITHUB_TOKEN:?GITHUB_TOKEN environment variable is required}"
    
    # Check if jq is available
    if ! command -v jq &> /dev/null; then
        echo "Error: jq is required but not installed"
        exit 1
    fi

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
    
    BRANCH_NAME="bot/update-wrapper-$(date +%Y%m%d-%H%M%S)"
    git switch -c $BRANCH_NAME
    git commit --signoff --author="bot-gradle <bot-gradle@gradle.com>" -m "Update Gradle wrapper to version $WRAPPER_VERSION"
    git push https://${GITHUB_TOKEN}@github.com/gradle/gradle.git $BRANCH_NAME
    
    PR_TITLE="Update Gradle wrapper to version $WRAPPER_VERSION"
    echo "Creating pull request..."
    if ! PR_RESPONSE=$(post "/pulls" "{
        \"title\": \"$PR_TITLE\",
        \"body\": \"$PR_TITLE\",
        \"head\": \"$BRANCH_NAME\",
        \"base\": \"$DEFAULT_BRANCH\"
    }"); then
        echo "Failed to create pull request"
        exit 1
    fi
    
    echo "PR Response: $PR_RESPONSE"
    
    if ! PR_NUMBER=$(echo "$PR_RESPONSE" | jq -r '.number // empty'); then
        echo "Failed to extract PR number from response"
        echo "Response: $PR_RESPONSE"
        exit 1
    fi
    
    if [[ -n "$PR_NUMBER" && "$PR_NUMBER" != "null" ]]; then
        echo "Created PR #$PR_NUMBER"
        
        echo "Adding comment to PR #$PR_NUMBER..."
        if ! post "/issues/$PR_NUMBER/comments" '{
            "body": "@bot-gradle test and merge"
        }'; then
            echo "Warning: Failed to add comment to PR"
        fi
        
        echo "Adding labels to PR #$PR_NUMBER..."
        if ! post "/issues/$PR_NUMBER/labels" '{
            "labels": ["@dev-productivity"]
        }'; then
            echo "Warning: Failed to add labels to PR"
        fi
    else
        echo "Failed to create PR or extract PR number"
        echo "Response: $PR_RESPONSE"
        exit 1
    fi
}

main "$@"
