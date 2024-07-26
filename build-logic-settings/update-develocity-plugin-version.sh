#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <new-version>"
  exit 1
fi

NEW_VERSION=$1

FILES=(
  "build-logic-commons/build-platform/build.gradle.kts"
  "build-logic-settings/settings.gradle.kts"
  "settings.gradle.kts"
)

for FILE in "${FILES[@]}"; do
  if [ -f "$FILE" ]; then
    sed -i "" "s/com.gradle:develocity-gradle-plugin:[0-9]*\.[0-9]*\.[0-9]*/com.gradle:develocity-gradle-plugin:${NEW_VERSION}/" "$FILE"
    sed -i "" "s/id(\"com.gradle.develocity\").version(\"[0-9]*\.[0-9]*\.[0-9]*\")/id(\"com.gradle.develocity\").version(\"${NEW_VERSION}\")/" "$FILE"
    echo "Updated $FILE to version $NEW_VERSION"
  else
    echo "File $FILE not found"
  fi
done

echo "All files updated successfully."
