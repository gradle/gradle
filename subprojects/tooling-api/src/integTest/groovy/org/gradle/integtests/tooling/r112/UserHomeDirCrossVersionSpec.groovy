/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher

class UserHomeDirCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        toolingApi.isEmbedded = false
    }

    @ToolingApiVersion(">=1.0")
    def "tooling api spawns a daemon in specified userHomeDir"() {
        File userHomeDir = temporaryFolder.createDir('userhomedir')
        projectDir.file('settings.gradle') << 'rootProject.name="test"'
        projectDir.file('build.gradle') << """task gradleBuild(type: GradleBuild) << {
    println 'userHomeDir=' + startParameter.gradleUserHomeDir
}
"""
        ByteArrayOutputStream baos = new ByteArrayOutputStream()

        when:
        toolingApi.withConnector { connector ->
            connector.useGradleUserHomeDir(userHomeDir)
        }
        toolingApi.withConnection { connection ->
            BuildLauncher build = connection.newBuild();
            if (targetDist.version.compareTo(targetDist.version.version('1.0-milestone-7')) > 0) {
                build.setJvmArguments('-Xmx32m')
            }
            build.withArguments('-Dorg.gradle.daemon.idletimeout=120000')
            build.forTasks("gradleBuild");
            build.standardOutput = baos
            build.run()
        }
        def output = baos.toString("UTF-8")

        then:
        output.contains('userHomeDir=' + userHomeDir.absolutePath)
    }

    @ToolingApiVersion("<1.0")
    def "tooling api spawns a daemon in specified userHomeDir against old toolingApi"() {
        File userHomeDir = temporaryFolder.createDir('userhomedir')
        projectDir.file('settings.gradle') << 'rootProject.name="test"'
        projectDir.file('build.gradle') << """task gradleBuild(type: GradleBuild) << {
    println 'userHomeDir=' + startParameter.gradleUserHomeDir
}
"""
        ByteArrayOutputStream baos = new ByteArrayOutputStream()

        when:
        toolingApi.withConnector { connector ->
            connector.useGradleUserHomeDir(userHomeDir)
        }
        toolingApi.withConnection { connection ->
            BuildLauncher build = connection.newBuild();
            build.forTasks("gradleBuild");
            build.standardOutput = baos
            build.run()
        }
        def output = baos.toString("UTF-8")

        then:
        output.contains('userHomeDir=' + userHomeDir.absolutePath)
    }
}
