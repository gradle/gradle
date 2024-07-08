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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

class DaemonJvmCompatibilityCrossVersionSpec extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {
    def setup() {
        buildFile.touch()
        settingsFile << """
            rootProject.name = "root"
        """
    }

    @TargetGradleVersion(">=8.8")
    @Requires(IntegTestPreconditions.Java21HomeAvailable)
    def "can run a build with Java 21 followed by another build with a different version"() {
        given:
        def jdk21 = AvailableJavaHomes.getJdk21()
        // We do not compare to the JavaVersion.VERSION_21 constant, as that does not exist in prior versions of Gradle
        def otherVersion = AvailableJavaHomes.getAvailableJdk { (it.languageVersion.majorVersion != "21") }
        Assume.assumeNotNull(otherVersion)
        withInstallations(jdk21.javaHome, otherVersion.javaHome)

        file("gradle.properties").writeProperties("org.gradle.java.home": jdk21.javaHome.absolutePath)
        withConnection { connection ->
            connection.newBuild().forTasks('help').run()
        }

        file("gradle.properties").writeProperties("org.gradle.java.home": otherVersion.javaHome.absolutePath)
        withConnection { connection ->
            connection.newBuild().forTasks('help').run()
        }
    }
}
