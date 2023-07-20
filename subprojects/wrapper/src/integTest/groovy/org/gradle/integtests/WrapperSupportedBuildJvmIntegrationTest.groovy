/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.GradleVersion

@Requires(
    value = [IntegTestPreconditions.UnsupportedJavaHomeAvailable, IntegTestPreconditions.NotEmbeddedExecutor],
    reason = "wrapperExecuter requires a real distribution")
@DoesNotSupportNonAsciiPaths(reason = "Java 6 seems to have issues with non-ascii paths")
class WrapperSupportedBuildJvmIntegrationTest extends AbstractWrapperIntegrationSpec {
    def "provides reasonable failure message when attempting to run under java #jdk.javaVersion"() {
        given:
        prepareWrapper()
        wrapperExecuter.withJavaHome(jdk.javaHome)

        expect:
        def failure = wrapperExecuter.withTasks("help").runWithFailure()
        failure.assertHasErrorOutput("Gradle ${GradleVersion.current().version} requires Java 1.8 or later to run. You are currently using Java ${jdk.javaVersion}.")

        where:
        jdk << AvailableJavaHomes.getJdks("1.6", "1.7")
    }

    def "provides reasonable failure message when attempting to run build under java #jdk.javaVersion"() {
        given:
        prepareWrapper()
        file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.canonicalPath)

        expect:
        def failure = wrapperExecuter.withTasks("help").runWithFailure()
        failure.assertHasErrorOutput("Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}.")

        where:
        jdk << AvailableJavaHomes.getJdks("1.6", "1.7")
    }
}
