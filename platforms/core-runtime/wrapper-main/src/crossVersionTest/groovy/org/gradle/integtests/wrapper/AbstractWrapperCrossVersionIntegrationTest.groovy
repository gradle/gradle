/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.wrapper

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
abstract class AbstractWrapperCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    def setup() {
        requireOwnGradleUserHomeDir()
    }

    protected GradleExecuter prepareWrapperExecuter(GradleDistribution wrapperVersion, GradleDistribution executionVersion) {
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

        wrapperExecuter(executionVersion)
    }

    private GradleExecuter wrapperExecuter(GradleDistribution wrapper) {
        def executer = super.version(wrapper)
        // Use isolated daemons in order to verify that using the installed distro works, and so that the daemons aren't visible to other tests, because
        // the installed distro is deleted at the end of this test
        executer.requireIsolatedDaemons()
        executer.usingExecutable('gradlew')
        return executer
    }
}
