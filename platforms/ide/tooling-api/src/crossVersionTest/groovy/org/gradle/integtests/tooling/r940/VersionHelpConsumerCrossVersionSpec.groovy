/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion

@ToolingApiVersion(">=9.4.0")
@TargetGradleVersion(">=9.4.0")
class VersionHelpConsumerCrossVersionSpec extends ToolingApiSpecification {

    def "prints version info and ignores tasks when --version is present"() {
        def output = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("nonExistentTask")
                .withArguments("--version")
                .setStandardOutput(output)
                .run()
        }

        then:
        def content = output.toString("UTF-8")
        content.contains("Gradle")
        content.contains("Build time:")
        !content.contains("Task 'nonExistentTask' not found")
    }

    def "prints help info and ignores tasks when --help is present"() {
        def output = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("nonExistentTask")
                .withArguments("--help")
                .setStandardOutput(output)
                .run()
        }

        then:
        def content = output.toString("UTF-8")
        content.contains("USAGE: gradle")
        content.contains("Shows this help message")
        !content.contains("Task 'nonExistentTask' not found")
    }

    def "prints version info and continues when --show-version is present"() {
        def output = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("help")
                .withArguments("--show-version")
                .setStandardOutput(output)
                .run()
        }

        then:
        def content = output.toString("UTF-8")
        content.contains("Gradle")
        content.contains("Build time:")
        content.contains("Welcome to Gradle")
        content.contains("BUILD SUCCESSFUL")
    }
}
