/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@TargetGradleVersion(">=3.3")
class BasicProjectConfigurationProgressCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "generates project configuration events for single project build"() {
        given:
        settingsFile << "rootProject.name = 'single'"

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        def configureBuild = events.operation("Configure build")

        def configureRootProject = events.operation("Configure project :")
        configureRootProject.parent == configureBuild

        configureBuild.children.contains(configureRootProject)
    }

    def "generates project configuration events for multi-project build"() {
        given:
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        def configureBuild = events.operation("Configure build")

        def configureRoot = events.operation("Configure project :")
        configureRoot.parent == configureBuild
        configureRoot.descriptor.name == 'Project :' || configureRoot.descriptor.name == 'Configure project :'

        def configureA = events.operation("Configure project :a")
        configureA.parent == configureBuild
        configureA.descriptor.name == 'Project :a' || configureA.descriptor.name == 'Configure project :a'

        def configureB = events.operation("Configure project :b")
        configureB.parent == configureBuild
        configureB.descriptor.name == 'Project :b' || configureB.descriptor.name == 'Configure project :b'

        configureBuild.children.containsAll(configureRoot, configureA, configureB)
    }

    def "generates project configuration events when configuration fails"() {
        given:
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        file("a/build.gradle") << """
            throw new RuntimeException("broken")
"""

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        def e = thrown(BuildException)
        e.cause.message =~ /A problem occurred evaluating project ':a'/

        events.assertIsABuild()

        def configureBuild = events.operation("Configure build")
        configureBuild.failed

        def configureRoot = events.operation("Configure project :")
        configureRoot.parent == configureBuild

        def configureA = events.operation("Configure project :a")
        configureA.parent == configureBuild
        configureA.failed
        configureA.failures[0].message == "A problem occurred configuring project ':a'."
    }

    def "generates events for buildSrc builds"() {
        given:
        buildSrc()
        javaProjectWithTests()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        def buildSrc = events.operation("Build buildSrc")
        def configureBuildSrc = buildSrc.child({ it.startsWith("Configure build") })
        configureBuildSrc.child("Configure project :buildSrc")
        configureBuildSrc.child("Configure project :buildSrc:a")
        configureBuildSrc.child("Configure project :buildSrc:b")

        def buildSrcTasks = buildSrc.child({ it.startsWith("Run tasks") })

        def buildSrcCompileJava = buildSrcTasks.child("Task :buildSrc:compileJava")
        buildSrcCompileJava.descriptor.name == ':buildSrc:compileJava'
        buildSrcCompileJava.descriptor.taskPath == ':buildSrc:compileJava'

        buildSrcTasks.child("Task :buildSrc:a:compileJava").descendant("Resolve dependencies :buildSrc:a:compileClasspath", "Resolve dependencies of :buildSrc:a:compileClasspath")
        buildSrcTasks.child("Task :buildSrc:b:compileJava").descendant("Resolve dependencies :buildSrc:b:compileClasspath", "Resolve dependencies of :buildSrc:b:compileClasspath")

        if (targetDist.runsBuildSrcTests) {
            buildSrcTasks.child("Task :buildSrc:a:test").descendant("Gradle Test Run :buildSrc:a:test").descendant("Test ok(ATest)")
            buildSrcTasks.child("Task :buildSrc:b:test")
        }

        when:
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, [OperationType.TASK] as Set)
                    .forTasks("build")
                    .run()
        }

        then:
        events.tasks.size() == events.operations.size()
        events.operation("Task :buildSrc:a:compileJava")
        if (targetDist.runsBuildSrcTests) {
            events.operation("Task :buildSrc:a:test")
        }
        events.operation("Task :compileJava")
        events.operation("Task :test")

        when:
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, [OperationType.TEST] as Set)
                    .withArguments("--rerun-tasks")
                    .forTasks("build")
                    .run()
        }

        then:
        events.tests.size() == events.operations.size()
        if (targetDist.runsBuildSrcTests) {
            events.operation("Gradle Test Run :buildSrc:a:test").descendant("Test ok(ATest)")
        }
        events.operation("Gradle Test Run :test").descendant("Test ok(ThingTest)")
    }

    def javaProjectWithTests() {
        buildFile << """
            allprojects {
                apply plugin: 'java'
                ${mavenCentralRepository()}
                dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
            }
"""
        file("src/main/java/Thing.java") << """class Thing { }"""
        file("src/test/java/ThingTest.java") << """
            public class ThingTest {
                @org.junit.Test
                public void ok() { }
            }
        """
    }

    def buildSrc() {
        file("buildSrc/settings.gradle") << "include 'a', 'b'"
        file("buildSrc/build.gradle") << """
            allprojects {
                apply plugin: 'java'
                ${mavenCentralRepository()}
                dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
            }
            dependencies {
                ${implementationConfiguration} project(':a')
                ${implementationConfiguration} project(':b')
            }
"""
        file("buildSrc/a/src/main/java/A.java") << "public class A {}"
        file("buildSrc/a/src/test/java/ATest.java") << "public class ATest { @org.junit.Test public void ok() { } }"
        file("buildSrc/b/src/main/java/B.java") << "public class B {}"
        file("buildSrc/b/src/test/java/BTest.java") << "public class BTest { @org.junit.Test public void ok() { } }"
    }
}
