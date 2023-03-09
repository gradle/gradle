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


package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r18.BrokenAction
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

@Requires([
    IntegTestPreconditions.Java5HomeAvailable,
    IntegTestPreconditions.Java6HomeAvailable,
    IntegTestPreconditions.Java7HomeAvailable
])
@TargetGradleVersion("current")
class ToolingApiUnsupportedBuildJvmCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        toolingApi.requireDaemons()
    }

    def "cannot run a build when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.newBuild().run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not execute build using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }

    def "cannot fetch model when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.model(GradleProject).get()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not fetch model of type 'GradleProject' using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }

    def "cannot run action when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.action(new BrokenAction()).run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not run build action using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }

    def "cannot run tests when build is configured to use Java 7 or earlier"() {
        given:
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.absolutePath)

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            connection.newTestLauncher().withJvmTestClasses("SomeTest").run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message.startsWith("Could not execute tests using ")
        e.cause.message == "Gradle ${targetDist.version.version} requires Java 8 or later to run. Your build is currently configured to use Java ${jdk.javaVersion.majorVersion}."

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6", "1.7")
    }
}
