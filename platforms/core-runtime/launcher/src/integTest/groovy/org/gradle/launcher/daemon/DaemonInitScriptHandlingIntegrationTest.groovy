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

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.integtests.fixtures.executer.DefaultGradleDistribution
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

/**
 * Tests that init scripts are used from the _clients_ GRADLE_HOME, not the daemon server's.
 */
@Issue("https://issues.gradle.org/browse/GRADLE-2408")
@LeaksFileHandles("isolated daemons are not always stopped in time")
//may fail with 'Unable to delete file: daemon.out.log'
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "tests a real Gradle distribution")
class DaemonInitScriptHandlingIntegrationTest extends DaemonIntegrationSpec {

    def "init scripts from client distribution are used, not the one the daemon was started with"() {
        given:
        def distro1 = createDistribution(1)
        def distro2 = createDistribution(2)

        and:
        file("buildSrc/build.gradle") << """
            println "buildSrc: runtime gradle home: \${gradle.gradleHomeDir}"
        """
        buildFile << """
            println "main build: runtime gradle home: \${gradle.gradleHomeDir}"
        """

        and:
        executer.withTasks("help")

        when:
        def distro1Result = runWithGradleHome(distro1)

        then:
        distro1Result.assertOutputContains "from distro 1"
        distro1Result.assertOutputContains "buildSrc: runtime gradle home: ${distro1.absolutePath}"
        distro1Result.assertOutputContains "main build: runtime gradle home: ${distro1.absolutePath}"

        when:
        def distro2Result = runWithGradleHome(distro2)

        then:
        distro2Result.assertNotOutput "from distro 1"
        distro2Result.assertOutputContains "from distro 2"
        distro2Result.assertOutputContains "buildSrc: runtime gradle home: ${distro1.absolutePath}"
        distro2Result.assertOutputContains "main build: runtime gradle home: ${distro1.absolutePath}"

        and:
        daemons.daemons.size() == 1
    }

    TestFile createDistribution(int i) {
        def distro = file("distro$i")
        distro.copyFrom(distribution.getGradleHomeDir())
        distro.file("bin", OperatingSystem.current().getScriptName("gradle")).permissions = 'rwx------'
        distro.file("init.d/init.gradle") << """
            gradle.rootProject {
                println "from distro $i"
            }
        """
        distro
    }

    def runWithGradleHome(TestFile gradleHome) {
        def copiedDistro = new DefaultGradleDistribution(executer.distribution.version, gradleHome, null)
        def daemonExecuter = new DaemonGradleExecuter(copiedDistro, executer.testDirectoryProvider)
        executer.copyTo(daemonExecuter)
        return daemonExecuter.run()
    }
}
