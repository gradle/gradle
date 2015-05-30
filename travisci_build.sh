#!/usr/bin/env bash
if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  echo "==== This was not a pull request, nothing executed! ===="
else
  ./gradlew prBuild$CI_TEST_SPLIT -x integTest --continue --stacktrace
fi
