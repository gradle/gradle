#!/usr/bin/env bash
if [ "$TRAVIS_PULL_REQUEST_FIXME" == "false" ]; then
  echo "==== This was not a pull request, nothing executed! ===="
else
  ./gradlew prBuild$CI_TEST_SPLIT -x integTest --continue -i --stacktrace
fi
