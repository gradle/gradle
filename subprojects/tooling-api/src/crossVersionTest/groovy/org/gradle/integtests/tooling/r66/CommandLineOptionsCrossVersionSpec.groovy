/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r66

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildLauncher

class CommandLineOptionsCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            println "max workers: " + gradle.startParameter.maxWorkerCount
        """
    }

    def "can specify options using properties file"() {
        when:
        file("gradle.properties") << "org.gradle.workers.max=12"
        def result = withBuild()

        then:
        result.standardOutput.contains("max workers: 12")
    }

    def "can specify options using command-line arguments"() {
        when:
        def result = withBuild { BuildLauncher launcher ->
            launcher.withArguments("--max-workers", "12")
        }

        then:
        result.standardOutput.contains("max workers: 12")
    }

    @TargetGradleVersion(">=6.6")
    def "can specify options using system properties defined as command-line arguments"() {
        when:
        def result = withBuild { BuildLauncher launcher ->
            launcher.withArguments("-Dorg.gradle.workers.max=12")
        }

        then:
        result.standardOutput.contains("max workers: 12")
    }

    @TargetGradleVersion(">=6.6")
    def "can specify options using system properties defined in JVM arguments"() {
        when:
        def result = withBuild { BuildLauncher launcher ->
            launcher.setJvmArguments("-Dorg.gradle.workers.max=12")
        }

        then:
        result.standardOutput.contains("max workers: 12")
    }

    def "command-line arguments take precedence over system properties"() {
        when:
        def result = withBuild { BuildLauncher launcher ->
            launcher.withArguments("-Dorg.gradle.workers.max=4", "--max-workers=12")
        }

        then:
        result.standardOutput.contains("max workers: 12")
    }

    @TargetGradleVersion(">=6.6")
    def "command-line system properties take precedence over JVM arg system properties"() {
        when:
        def result = withBuild { BuildLauncher launcher ->
            launcher.withArguments("-Dorg.gradle.workers.max=12")
            launcher.setJvmArguments("-Dorg.gradle.workers.max=4")
        }

        then:
        result.standardOutput.contains("max workers: 12")
    }

    @TargetGradleVersion(">=6.6")
    def "command-line system properties take precedence over properties file"() {
        when:
        file("gradle.properties") << "org.gradle.workers.max=4"
        def result = withBuild { BuildLauncher launcher ->
            launcher.withArguments("-Dorg.gradle.workers.max=12")
        }

        then:
        result.standardOutput.contains("max workers: 12")
    }

    @TargetGradleVersion(">=6.6")
    def "JVM arg system properties take precedence over properties file"() {
        when:
        file("gradle.properties") << "org.gradle.workers.max=4"
        def result = withBuild { BuildLauncher launcher ->
            launcher.setJvmArguments("-Dorg.gradle.workers.max=12")
        }

        then:
        result.standardOutput.contains("max workers: 12")
    }
}
