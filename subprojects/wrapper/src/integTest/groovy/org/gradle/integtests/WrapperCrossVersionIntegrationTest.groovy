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
import org.gradle.internal.SystemProperties

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

    void checkWrapperWorksWith(GradleDistribution wrapperGenVersion, GradleDistribution executionVersion) {
        if (!wrapperGenVersion.wrapperCanExecute(executionVersion.version)) {
            println "skipping $wrapperGenVersion as its wrapper cannot execute version ${executionVersion.version.version}"
            return
        }

        println "use wrapper from $wrapperGenVersion to build using $executionVersion"

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
    doLast { println "hello from \$gradle.gradleVersion" }
}
"""
        version(wrapperGenVersion).withTasks('wrapper').run()
        def result = version(executionVersion, wrapperGenVersion).usingExecutable('gradlew').withTasks('hello').run()
        assert result.output.contains("hello from $executionVersion.version.version")
    }

    GradleExecuter version(GradleDistribution runtime, GradleDistribution wrapper) {
        def executer = super.version(runtime)
        /**
         * We additionally pass the gradle user home as a system property.
         * Early gradle wrapper (< 1.7 don't honor --gradle-user-home command line option correctly
         * and leaking gradle dist under test into ~/.gradle/wrapper.
         */
        if (!wrapper.wrapperSupportsGradleUserHomeCommandLineOption) {
            if (!wrapper.supportsSpacesInGradleAndJavaOpts) {
                // Don't use the test-specific location as this contains spaces
                executer.withGradleUserHomeDir(new IntegrationTestBuildContext().gradleUserHomeDir)

                def buildDirTmp = file("build/tmp")
                if (buildDirTmp.absolutePath.contains(" ")) {
                    def jvmTmp = SystemProperties.instance.javaIoTmpDir
                    if (jvmTmp.contains(" ")) {
                        throw new IllegalStateException("Cannot run test as there is no tmp dir location available that does not contain a space in the path")
                    } else {
                        executer.withTmpDir(jvmTmp)
                    }
                } else {
                    executer.withTmpDir(buildDirTmp.absolutePath)
                }
            }
            executer.withGradleOpts("-Dgradle.user.home=${executer.gradleUserHomeDir}")
        }
        return executer
    }
}

