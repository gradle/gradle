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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.composite.BuildIdentity
import org.gradle.tooling.composite.GradleBuild
import org.gradle.tooling.composite.GradleConnection
import org.gradle.tooling.composite.ModelResults
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.CollectionUtils

class LongRunningOperationCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    TestFile singleBuild
    GradleBuild singleBuildParticipant
    ModelResults<EclipseProject> modelResults

    def setup() {
        singleBuild = populate("single") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << """
        apply plugin: 'java'

        description = "not set"
        if (project.hasProperty("projectProperty")) {
            description = "Set from project property = \${project.projectProperty}"
        }
        if (System.properties.systemProperty) {
            description = "Set from system property = \${System.properties.systemProperty}"
        }

        task setDescription() << {
            project.description = "Set from task"
        }

        def write(msg) {
            file("result") << msg
        }

        task run() << {
            if (project.hasProperty("projectProperty")) {
                write("Project property = \${project.projectProperty}")
            }
            if (System.properties.systemProperty) {
                write("System property = \${System.properties.systemProperty}")
            }
        }
"""
        }
        singleBuildParticipant = createGradleBuildParticipant(singleBuild)
    }

    def "can call tasks before building composite model"() {
        when:
        modelResults = withCompositeConnection(singleBuild) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.forTasks("setDescription")
            modelBuilder.get()
        }
        then:
        modelResults.size() == 1
        CollectionUtils.single(modelResults).model.description == "Set from task"
    }

    def "can pass additional command-line arguments for project properties"() {
        when:
        modelResults = withCompositeConnection(singleBuild) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withArguments("-PprojectProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == 1
        CollectionUtils.single(modelResults).model.description == "Set from project property = foo"

        when:
        BuildLauncher buildLauncher = buildLauncherFor(singleBuildParticipant.toBuildIdentity(), singleBuildParticipant)
        buildLauncher.forTasks("run")
        buildLauncher.withArguments("-PprojectProperty=foo")
        buildLauncher.run()
        then:
        singleBuild.file("result").text == "Project property = foo"
    }

    def "can pass additional command-line arguments for system properties"() {
        when:
        modelResults = withCompositeConnection(singleBuild) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withArguments("-DsystemProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == 1
        CollectionUtils.single(modelResults).model.description == "Set from system property = foo"

        when:
        BuildLauncher buildLauncher = buildLauncherFor(singleBuildParticipant.toBuildIdentity(), singleBuildParticipant)
        buildLauncher.forTasks("run")
        buildLauncher.withArguments("-DsystemProperty=foo")
        buildLauncher.run()
        then:
        singleBuild.file("result").text == "System property = foo"
    }

    // TODO: This cannot run in embedded mode
    def "can pass additional jvm arguments"() {
        when:
        modelResults = withCompositeConnection(singleBuild) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setJvmArguments("-DsystemProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == 1
        CollectionUtils.single(modelResults).model.description == "Set from system property = foo"

        when:
        BuildLauncher buildLauncher = buildLauncherFor(singleBuildParticipant.toBuildIdentity(), singleBuildParticipant)
        buildLauncher.forTasks("run")
        buildLauncher.setJvmArguments("-DsystemProperty=foo")
        buildLauncher.run()
        then:
        singleBuild.file("result").text == "System property = foo"
    }

    private BuildLauncher buildLauncherFor(BuildIdentity buildIdentity, GradleBuild... participants) {
        def builder = createCompositeBuilder()
        builder.addBuilds(participants)
        def connection = builder.build()
        def buildLauncher = connection.newBuild(buildIdentity)
        buildLauncher
    }
}
