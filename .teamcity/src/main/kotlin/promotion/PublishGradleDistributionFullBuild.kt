/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package promotion

import jetbrains.buildServer.configs.kotlin.buildSteps.script

abstract class PublishGradleDistributionFullBuild(
    // The branch to be promoted
    promotedBranch: String,
    prepTask: String? = null,
    promoteTask: String,
    triggerName: String,
    gitUserName: String = "bot-teamcity",
    gitUserEmail: String = "bot-teamcity@gradle.com",
    extraParameters: String = "",
) : BasePublishGradleDistribution(promotedBranch, prepTask, triggerName, gitUserName, gitUserEmail, extraParameters) {
    init {
        steps {
            script {
                scriptContent = """
#!/bin/bash

cat <<EOF > version-info.properties
buildTimestamp=20250722015451+0000
commitId=6611489182396aa56ff4524813d1e8070d2e879e
downloadUrl=https\://services.gradle.org/distributions-snapshots/gradle-9.0.0-rc-3-all.zip
hasBeenReleased=true
isSnapshot=true
promotedVersion=${if (prepTask == "prepFinalRelease") "9.0.0" else "9.0.0-rc-3"}
versionBase=9.0.0
EOF
                """
            }
        }

        artifactRules =
            """
            version-info.properties => ./
            """.trimIndent()
    }
}
