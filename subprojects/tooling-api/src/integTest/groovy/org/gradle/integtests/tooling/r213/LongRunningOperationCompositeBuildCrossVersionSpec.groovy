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
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.connection.ModelResults
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.CollectionUtils
import spock.lang.Ignore

class LongRunningOperationCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    def escapeHeader = "\u001b["
    TestFile singleBuild
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

        System.out.println("This is standard out")
        System.err.println("This is standard err")
"""
        }
    }

    @TargetGradleVersion(">=1.2")
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
        BuildLauncher buildLauncher = buildLauncherFor(singleBuild)
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
        BuildLauncher buildLauncher = buildLauncherFor(singleBuild)
        buildLauncher.forTasks("run")
        buildLauncher.withArguments("-DsystemProperty=foo")
        buildLauncher.run()
        then:
        singleBuild.file("result").text == "System property = foo"
    }

    // TODO:
    @Ignore("This cannot run in embedded mode")
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
        BuildLauncher buildLauncher = buildLauncherFor(singleBuild)
        buildLauncher.forTasks("run")
        buildLauncher.setJvmArguments("-DsystemProperty=foo")
        buildLauncher.run()
        then:
        singleBuild.file("result").text == "System property = foo"
    }

    private BuildLauncher buildLauncherFor(TestFile rootDir) {
        def builder = createCompositeBuilder()
        def buildIdentity = createGradleBuildParticipant(builder, rootDir).toBuildIdentity()
        def connection = builder.build()
        def buildLauncher = connection.newBuild(buildIdentity)
        buildLauncher
    }

    def "can receive stdout and stderr with model requests"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        OutputStream stdErr = new ByteArrayOutputStream()
        when:
        withCompositeConnection(singleBuild) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setStandardOutput(stdOut)
            modelBuilder.setStandardError(stdErr)
            modelBuilder.get()
        }
        then:
        !stdOut.toString().contains(escapeHeader)
        stdOut.toString().contains("This is standard out")
        stdErr.toString().contains("This is standard err")
    }

    def "can receive stdout and stderr with build launcher"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        OutputStream stdErr = new ByteArrayOutputStream()
        when:
        def buildLauncher = buildLauncherFor(singleBuild)
        buildLauncher.forTasks("run")
        buildLauncher.setStandardOutput(stdOut)
        buildLauncher.setStandardError(stdErr)
        buildLauncher.run()
        then:
        !stdOut.toString().contains(escapeHeader)
        stdOut.toString().contains("This is standard out")
        stdErr.toString().contains("This is standard err")
    }

    @Ignore
    @TargetGradleVersion(">=2.3")
    def "can colorize output with model requests"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        when:
        withCompositeConnection(singleBuild) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setStandardOutput(stdOut)
            modelBuilder.colorOutput = true
            modelBuilder.get()
        }
        then:
        stdOut.toString().contains(escapeHeader)
        stdOut.toString().contains("This is standard out")
    }

    @Ignore
    @TargetGradleVersion(">=2.3")
    def "can colorize output with build launcher"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        when:
        def buildLauncher = buildLauncherFor(singleBuild)
        buildLauncher.forTasks("run")
        buildLauncher.setStandardOutput(stdOut)
        buildLauncher.colorOutput = true
        buildLauncher.run()
        then:
        stdOut.toString().contains(escapeHeader)
        stdOut.toString().contains("This is standard out")
    }
}
