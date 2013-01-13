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



package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.integtests.fixtures.executer.DefaultGradleDistribution
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

/**
 * Tests that init scripts are used from the _clients_ GRADLE_HOME, not the daemon server's.
 */
@Issue("http://issues.gradle.org/browse/GRADLE-2408")
class DaemonInitScriptHandlingIntegrationTest extends DaemonIntegrationSpec {

    TestFile createDistribution(int i) {
        def distro = file("distro$i")
        distro.copyFrom(distribution.getGradleHomeDir())
        distro.file("bin", OperatingSystem.current().getScriptName("gradle")).permissions = 'rwx------'
        distro.file("init.d/init.gradle") << """
            gradle.allprojects {
                task echo << { println "from distro $i" }
            }
        """
        distro
    }

    def runWithGradleHome(TestFile gradleHome) {
        def copiedDistro = new DefaultGradleDistribution(executer.distribution.version, gradleHome, null)
        executer.copyTo(new DaemonGradleExecuter(copiedDistro, executer.testDirectoryProvider)).run()
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

        and:
        executer.withTasks("echo")

        when:
        def distro1Result = runWithGradleHome(distro1)

        then:
        distro1Result.output.contains "from distro 1"
        distro1Result.output.contains "runtime gradle home: ${distro1.absolutePath}"

        when:
        def distro2Result = runWithGradleHome(distro2)

        then:
        distro2Result.output.contains "from distro 2"
        distro1Result.output.contains "runtime gradle home: ${distro1.absolutePath}"

        cleanup:
        stopDaemonsNow()
    }

}
