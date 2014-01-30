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
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion(">=1.12")
class ToolingApiDeprecationsCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        toolingApi.isEmbedded = false
        file("build.gradle") << """
task noop << {
    println "noop"
}
"""
    }

    @TargetGradleVersion("<1.0-milestone-8")
    def "build shows deprecation warning for pre 1.0m8 providers"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("noop")
            build.run()
        }

        then:
        output.toString().contains(deprecationMessage(targetDist.version.version))
    }

    @TargetGradleVersion("<1.0-milestone-8")
    def "model retrieving shows deprecation warning for pre 1.0m8 providers"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def modelBuilder = connection.model(EclipseProject)
            modelBuilder.standardOutput = output
            def model = modelBuilder.get()
        }

        then:
        output.toString().contains(deprecationMessage(targetDist.version.version))
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "build shows no deprecation warning for 1.0m8+ providers"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("noop")
            build.run()
        }

        then:
        !output.toString().contains(deprecationMessage(targetDist.version.version))
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "model retrieving shows no deprecation warning for 1.0m8+ providers"() {
        when:
        def output = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            def modelBuilder = connection.model(GradleProject)
            modelBuilder.standardOutput = output
            def model = modelBuilder.get()
        }

        then:
        !output.toString().contains(deprecationMessage(targetDist.version.version))
    }

    def deprecationMessage(def version) {
        "Connecting to Gradle build version " + version + " has been deprecated and is scheduled to be removed in Gradle 2.0"
    }
}
