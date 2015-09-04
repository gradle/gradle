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

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Assume

@LeaksFileHandles
class WrapperCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    def setup() {
        requireOwnGradleUserHomeDir()
    }

    public void canUseWrapperFromPreviousVersionToRunCurrentVersion() {
        expect:
        checkWrapperWorksWith(previous, current)
    }

    public void canUseWrapperFromCurrentVersionToRunPreviousVersion() {
        expect:
        checkWrapperWorksWith(current, previous)
    }

    void checkWrapperWorksWith(GradleDistribution wrapperVersion, GradleDistribution executionVersion) {
        Assume.assumeTrue("skipping $wrapperVersion as its wrapper cannot execute version ${executionVersion.version.version}", wrapperVersion.wrapperCanExecute(executionVersion.version))

        println "use wrapper from $wrapperVersion to build using $executionVersion"

        buildFile << """

task wrapper(type: Wrapper) {
    gradleVersion = '$executionVersion.version.version'
}

if (wrapper.hasProperty('urlRoot')) {
    println "configuring the wrapper using the old way: 'urlRoot'..."
    wrapper.urlRoot = '${executionVersion.binDistribution.parentFile.toURI()}'
} else {
    println "configuring the wrapper using the new way: 'distributionUrl'..."
    wrapper.distributionUrl = '${executionVersion.binDistribution.toURI()}'
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
        version(wrapperVersion).withTasks('wrapper').run()

        def executer = version(executionVersion, wrapperVersion)
        def result = executer.usingExecutable('gradlew').withTasks('hello').run()

        assert result.output.contains("hello from $executionVersion.version.version")
        assert result.output.contains("using distribution at ${executer.gradleUserHomeDir.file("wrapper/dists")}")
        assert result.output.contains("using Gradle user home at $executer.gradleUserHomeDir")
    }

    GradleExecuter version(GradleDistribution runtime, GradleDistribution wrapper) {
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
}

