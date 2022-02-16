#!/bin/bash

set -x
set -e

# Usage:
# curl -s {the script raw url} | bash -s {project number in https://github.com/orgs/gradle/projects/17} {PR node id}
# e.g.
# curl -s https://raw.githubusercontent.com/gradle/gradle/master/.github/workflows/add-to-github-project.sh | bash -s 17 ${{ github.event.pull_request.node_id }}

PROJECT_NUMBER=$1
PULL_REQUEST_NODE_ID=$2

if [ -z "$PROJECT_NUMBER" ]
then
    echo "parameter PROJECT_NUMBER not found"
    exit 1
fi

if [ -z "$PULL_REQUEST_NODE_ID" ]
then
    echo "parameter PULL_REQUEST_NODE_ID not found"
    exit 1
fi

gh api graphql -f query='
query($org: String!, $number: Int!) {
  organization(login: $org){
    projectNext(number: $number) {
      id
      fields(first:20) {
        nodes {
          id
        }
      }
    }
  }
}' -f org=gradle -F number=$PROJECT_NUMBER > project_data.json

PROJECT_ID=$(jq '.data.organization.projectNext.id' project_data.json)

gh api graphql -f query='
mutation($project:ID!, $pr:ID!) {
  addProjectNextItem(input: {projectId: $project, contentId: $pr}) {
    projectNextItem {
      id
    }
  }
}' -f project=$PROJECT_ID -f pr=$PULL_REQUEST_NODE_ID
