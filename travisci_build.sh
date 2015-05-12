#!/usr/bin/env sh
if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  echo "==== This was not a pull request, nothing executed! ===="
else
  ./gradlew pullRequestValidation
fi
