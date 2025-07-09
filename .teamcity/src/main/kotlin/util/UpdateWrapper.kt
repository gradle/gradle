package util

import common.BuildToolBuildJvm
import common.Os
import common.VersionedSettingsBranch
import common.buildToolGradleParameters
import common.gradleWrapper
import common.javaHome
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.freeDiskSpace
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import vcsroots.useAbsoluteVcs

object UpdateWrapper : BuildType({
    name = "UpdateWrapper"
    id("UpdateWrapper")

    vcs.useAbsoluteVcs(VersionedSettingsBranch.fromDslContext().vcsRootId())

    params {
        param("wrapperVersion", "should-be-overridden")
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
    }

    steps {
        script {
            name = "Update Wrapper"
            scriptContent = """
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
                
                git commit -m "Update Gradle wrapper to version %wrapperVersion%"
                
                TIMESTAMP=$(date +%Y%m%d-%H%M%S)
                BRANCH_NAME="update-wrapper-${"$"}TIMESTAMP"
                
                git checkout -b ${"$"}BRANCH_NAME
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
