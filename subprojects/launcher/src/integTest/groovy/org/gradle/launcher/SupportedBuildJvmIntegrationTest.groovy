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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import spock.lang.Unroll

@Requires(adhoc = { AvailableJavaHomes.getJdks("1.6", "1.7") })
class SupportedBuildJvmIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        requireGradleDistribution()
    }

    @Unroll
    def "provides reasonable failure message when attempting to run under java #jdk.javaVersion"() {
        given:
        executer.withJavaHome(jdk.javaHome)

        expect:
        fails("help")
        failure.assertHasErrorOutput("Gradle ${GradleVersion.current().version} requires Java 8 or later to run. You are currently using Java ${jdk.javaVersion.majorVersion}.")

        where:
        jdk << AvailableJavaHomes.getJdks("1.6", "1.7")
    }

    @Unroll
    def "fails when build is configured to use Java #jdk.javaVersion"() {
        given:
        file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.canonicalPath)

        expect:
        fails("help")
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}.")

        where:
        jdk << AvailableJavaHomes.getJdks("1.6", "1.7")
    }
}
