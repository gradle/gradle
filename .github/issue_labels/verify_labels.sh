#!/usr/bin/env sh

count=1
until [ $count -gt 10 ]; do
  labels=$(gh api --header 'Accept: application/vnd.github+json' --method GET /repos/gradle/gradle/labels?page=$count)

  groovy verify_labels.groovy "$labels"

  ((count++))
done
