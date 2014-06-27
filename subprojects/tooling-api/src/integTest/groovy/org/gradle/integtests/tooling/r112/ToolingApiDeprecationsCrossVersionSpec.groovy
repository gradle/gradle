/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiDeprecationsCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        file("build.gradle") << """
task noop << {
    println "noop"
}
"""
    }

    @ToolingApiVersion(">=1.12")
    @TargetGradleVersion("<1.0-milestone-8")
    def "build rejected for pre 1.0m8 providers"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("noop")
            build.run()
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "Support for Gradle version ${targetDist.version.version} was removed in tooling API version 2.0. You should upgrade your Gradle build to use Gradle 1.0-milestone-8 or later."
    }

    @ToolingApiVersion(">=1.12")
    @TargetGradleVersion("<1.0-milestone-8")
    def "model retrieving fails for pre 1.0m8 providers"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def modelBuilder = connection.model(EclipseProject)
            modelBuilder.standardOutput = output
            modelBuilder.get()
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "Support for Gradle version ${targetDist.version.version} was removed in tooling API version 2.0. You should upgrade your Gradle build to use Gradle 1.0-milestone-8 or later."
    }

    @ToolingApiVersion("<1.2")
    @TargetGradleVersion(">=1.12")
    def "provider rejects build request from a tooling API older than 1.2"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("noop")
            build.run()
        }

        then:
        GradleConnectionException e = thrown()
        e.cause.message.contains('Support for clients using a tooling API version older than 1.2 was removed in Gradle 2.0. You should upgrade your tooling API client to version 1.2 or later.')
    }

    @ToolingApiVersion("<1.2")
    @TargetGradleVersion(">=1.12")
    def "provider rejects model request from a tooling API older than 1.2"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def modelBuilder = connection.model(EclipseProject)
            modelBuilder.standardOutput = output
            modelBuilder.get()
        }

        then:
        GradleConnectionException e = thrown()
        e.cause.message.contains('Support for clients using a tooling API version older than 1.2 was removed in Gradle 2.0. You should upgrade your tooling API client to version 1.2 or later.')
    }
}
