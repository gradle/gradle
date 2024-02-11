/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.r14


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import spock.lang.Issue

/**
 * Tests that init scripts are used from the _clients_ GRADLE_HOME, not the daemon server's.
 */
@Issue("https://issues.gradle.org/browse/GRADLE-2408")
@LeaksFileHandles
class ToolingApiInitScriptCrossVersionIntegrationTest extends ToolingApiSpecification {

    TestFile createDistribution(int i) {
        def distro = temporaryDistributionFolder.file("distro$i")
        distro.deleteDir()

        distro.copyFrom(getTargetDist().getGradleHomeDir())
        distro.file("bin", OperatingSystem.current().getScriptName("gradle")).permissions = 'rwx------'
        distro.file("init.d/init.gradle") << """
            gradle.allprojects {
                task echo { doLast { println "from distro $i" } }
            }
        """
        distro
    }

    String runWithInstallation(TestFile gradleHome) {
        toolingApi.requireIsolatedDaemons()
        toolingApi.withConnector { GradleConnector it ->
            it.useInstallation(new File(gradleHome.absolutePath))
        }
        withBuild { it.forTasks("echo") }.standardOutput
    }

    def "init scripts from client distribution are used, not from the test"() {
        given:
        def distro1 = createDistribution(1)
        def distro2 = createDistribution(2)

        and:
        buildFile << """
            echo.doLast {
                println "runtime gradle home: \${gradle.gradleHomeDir}"
            }
        """

        when:
        def distro1Output = runWithInstallation(distro1)

        then:
        distro1Output.contains "from distro 1"
        distro1Output.contains "runtime gradle home: ${distro1.absolutePath}"

        when:
        def distro2Output = runWithInstallation(distro2)

        then:
        distro2Output.contains "from distro 2"
        distro2Output.contains "runtime gradle home: ${distro1.absolutePath}"
    }
}

