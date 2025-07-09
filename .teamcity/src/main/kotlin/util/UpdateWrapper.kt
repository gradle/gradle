package util

import common.BuildToolBuildJvm
import common.Os
import common.VersionedSettingsBranch
import common.javaHome
import common.requiresNotEc2Agent
import common.requiresOs
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import vcsroots.useAbsoluteVcs

object UpdateWrapper : BuildType({
    name = "UpdateWrapper"
    id("UpdateWrapper")

    vcs.useAbsoluteVcs(VersionedSettingsBranch.fromDslContext().vcsRootId())

    requirements {
        requiresOs(Os.LINUX)
    }

    params {
        text(
            "wrapperVersion",
            "should-be-overridden",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description =
                "The version of Gradle to update to. " +
                    "Can be a specific version or one of 'latest', 'release-candidate', 'release-milestone', 'release-nightly', 'nightly'.",
        )
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
    }

    steps {
        script {
            name = "Update Wrapper"
            scriptContent =
                """
                #!/bin/bash
                set -e

                git config user.name "bot-gradle"
                git config user.email "bot-gradle@gradle.com"

                ./gradlew wrapper --gradle-version=%wrapperVersion%
                ./gradlew wrapper

                git add .

                if git diff --cached --quiet; then
                    echo "No changes to commit"
                    exit 0
                fi

                TIMESTAMP=$(date +%%Y%%m%%d-%%H%%M%%S)
                BRANCH_NAME="update-wrapper-${"$"}TIMESTAMP"

                git switch -c ${"$"}BRANCH_NAME
                git commit --signoff -m "Update Gradle wrapper to version %wrapperVersion%"
                git push https://%github.bot-gradle.token%@github.com/gradle/gradle.git ${"$"}BRANCH_NAME

                PR_TITLE="Update Gradle wrapper to version %wrapperVersion%"

                curl -X POST \
                    -H "Authorization: token %github.bot-gradle.token%" \
                    -H "Accept: application/vnd.github.v3+json" \
                    https://api.github.com/repos/gradle/gradle/pulls \
                    -d "{
                        \"title\": \"${"$"}PR_TITLE\",
                        \"body\": \"${"$"}PR_TITLE\",
                        \"head\": \"${"$"}BRANCH_NAME\",
                        \"base\": \"%teamcity.build.branch%\"
                    }"
                """.trimIndent()
        }
    }
})
