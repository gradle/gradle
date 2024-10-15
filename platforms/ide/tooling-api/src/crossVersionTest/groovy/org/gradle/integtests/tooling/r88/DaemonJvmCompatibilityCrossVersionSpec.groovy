/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling.r88

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class DaemonJvmCompatibilityCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile.touch()
        settingsFile << """
            rootProject.name = "root"
        """
    }

    @TargetGradleVersion(">=8.8")
    @Requires(value = [IntegTestPreconditions.Java17HomeAvailable, IntegTestPreconditions.Java21HomeAvailable])
    def "can run a build with Java 21 followed by another build with a different version"() {
        given:
        def jdk21 = AvailableJavaHomes.getJdk21()
        def jdk17 = AvailableJavaHomes.getJdk17()

        file("gradle.properties").writeProperties("org.gradle.java.home": jdk21.javaHome.absolutePath)
        withConnection { connection ->
            connection.newBuild().forTasks('help').run()
        }

        file("gradle.properties").writeProperties("org.gradle.java.home": jdk17.javaHome.absolutePath)
        withConnection { connection ->
            connection.newBuild().forTasks('help').run()
        }
    }
}
