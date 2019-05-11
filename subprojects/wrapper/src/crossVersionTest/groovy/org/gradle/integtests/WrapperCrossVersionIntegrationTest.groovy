/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.junit.Assume
import spock.lang.Unroll

@SuppressWarnings("IntegrationTestFixtures")
class WrapperCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    def setup() {
        requireOwnGradleUserHomeDir()
    }

    void canUseWrapperFromPreviousVersionToRunCurrentVersion() {
        when:
        GradleExecuter executer = prepareWrapperExecuter(previous, current)

        then:
        checkWrapperWorksWith(executer, current)

        cleanup:
        cleanupDaemons(executer, current)
    }

    void canUseWrapperFromCurrentVersionToRunPreviousVersion() {
        when:
        GradleExecuter executer = prepareWrapperExecuter(current, previous).withWarningMode(null)

        then:
        checkWrapperWorksWith(executer, previous)

        cleanup:
        cleanupDaemons(executer, previous)
    }

    @Unroll
    @Requires(adhoc = { AvailableJavaHomes.getJdks("1.6", "1.7") })
    def 'provides reasonable failure message when attempting to run current Version with previous wrapper under java #jdk.javaVersion'() {
        when:
        GradleExecuter executor = prepareWrapperExecuter(previous, current).withJavaHome(jdk.javaHome)

        then:
        def result = executor.usingExecutable('gradlew').withArgument('help').runWithFailure()
        result.hasErrorOutput("Gradle ${GradleVersion.current().version} requires Java 8 or later to run. You are currently using Java ${jdk.javaVersion.majorVersion}.")

        where:
        jdk << AvailableJavaHomes.getJdks("1.6", "1.7")
    }

    @Unroll
    @Requires(adhoc = { AvailableJavaHomes.getJdks("1.6", "1.7") })
    def 'provides reasonable failure message when attempting to run with previous wrapper and the build is configured to use Java #jdk.javaVersion'() {
        when:
        GradleExecuter executor = prepareWrapperExecuter(previous, current)
        file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.canonicalPath)

        then:
        def result = executor.usingExecutable('gradlew').withArgument('help').runWithFailure()
        result.hasErrorOutput("Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}.")

        where:
        jdk << AvailableJavaHomes.getJdks("1.6", "1.7")
    }

    private GradleExecuter prepareWrapperExecuter(GradleDistribution wrapperVersion, GradleDistribution executionVersion) {
        Assume.assumeTrue("skipping $wrapperVersion as its wrapper cannot execute version ${executionVersion.version.version}", wrapperVersion.wrapperCanExecute(executionVersion.version))

        println "use wrapper from $wrapperVersion to build using $executionVersion"

        buildFile << """
task wrapper (type: Wrapper, overwrite: true) {
    gradleVersion = '$executionVersion.version.version'
    distributionUrl = '${executionVersion.binDistribution.toURI()}'
}

println "using Java version \${System.getProperty('java.version')}"

task hello {
    doLast {
        println "hello from \$gradle.gradleVersion"
        println "using distribution at \$gradle.gradleHomeDir"
        println "using Gradle user home at \$gradle.gradleUserHomeDir"
    }
}
"""
        settingsFile << "rootProject.name = 'wrapper'"
        version(wrapperVersion).withTasks('wrapper').run()

        def executer = wrapperExecuter(wrapperVersion)
        executer
    }

    GradleExecuter wrapperExecuter(GradleDistribution wrapper) {
        def executer = super.version(wrapper)

        if (!wrapper.supportsSpacesInGradleAndJavaOpts) {
            // Don't use the test-specific location as this contains spaces
            executer.withGradleUserHomeDir(new IntegrationTestBuildContext().gradleUserHomeDir)
        }

        /**
         * We additionally pass the gradle user home as a system property.
         * Early gradle wrapper versions (< 1.7) don't honor the --gradle-user-home command line option correctly
         * and leaking gradle dist under test into ~/.gradle/wrapper.
         */
        if (!wrapper.wrapperSupportsGradleUserHomeCommandLineOption) {
            executer.withCommandLineGradleOpts("-Dgradle.user.home=${executer.gradleUserHomeDir}")
        }

        // Use isolated daemons in order to verify that using the installed distro works, and so that the daemons aren't visible to other tests, because
        // the installed distro is deleted at the end of this test
        executer.requireIsolatedDaemons()
        return executer
    }

    void checkWrapperWorksWith(GradleExecuter executer, GradleDistribution executionVersion) {
        def result = executer.usingExecutable('gradlew').withTasks('hello').run()

        assert result.output.contains("hello from $executionVersion.version.version")
        assert result.output.contains("using distribution at ${executer.gradleUserHomeDir.file("wrapper/dists")}")
        assert result.output.contains("using Gradle user home at $executer.gradleUserHomeDir")
    }

    static void cleanupDaemons(GradleExecuter executer, GradleDistribution executionVersion) {
        new DaemonLogsAnalyzer(executer.daemonBaseDir, executionVersion.version.version).killAll()
    }
}

