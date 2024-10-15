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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@TargetGradleVersion(">=3.3")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    @TargetGradleVersion(">=3.3 <4.0")
    def "generates events for project configuration where project configuration is nested"() {
        given:
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { apply plugin: 'java' }

            evaluationDependsOn(':a')
"""
        file("a/build.gradle") << """
            evaluationDependsOn(':b')
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
        configureBuild.children.contains(configureRoot)

        def configureA = events.operation("Configure project :a")
        configureA.parent == configureRoot
        configureRoot.children == [configureA]

        def configureB = events.operation("Configure project :b")
        configureB.parent == configureA
        configureA.children == [configureB]
    }

    def "generates events for dependency resolution"() {
        given:
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

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .forTasks("build")
                        .run()
        }

        then:
        events.assertIsABuild()

        def compileJava = events.operation("Task :compileJava")
        def compileTestJava = events.operation("Task :compileTestJava")
        def test = events.operation("Task :test")

        def compileClasspath = events.operation("Resolve dependencies :compileClasspath", "Resolve dependencies of :compileClasspath")
        compileClasspath.hasAncestor compileJava

        def testCompileClasspath = events.operation("Resolve dependencies :testCompileClasspath", "Resolve dependencies of :testCompileClasspath")
        testCompileClasspath.hasAncestor compileTestJava

        def testRuntimeClasspath = events.operation(
            "Resolve dependencies :testRuntime", "Resolve dependencies :testRuntimeClasspath",
            "Resolve dependencies of :testRuntime", "Resolve dependencies of :testRuntimeClasspath")
        testRuntimeClasspath.hasAncestor test
    }

    def "generates events for failed dependency resolution"() {
        given:
        buildFile << """
            allprojects { apply plugin: 'java' }
            dependencies { ${implementationConfiguration} 'thing:thing:1.0' }
"""
        file("src/main/java/Thing.java") << """class Thing { }"""

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .forTasks("build")
                        .run()
        }

        then:
        def e = thrown(BuildException)
        if (targetDist.addsTaskExecutionExceptionAroundAllTaskFailures) {
            e.cause.cause.message =~ /Could not resolve all (dependencies|files) for configuration ':compileClasspath'./
        } else {
            e.cause.message =~ /Could not resolve all (dependencies|files) for configuration ':compileClasspath'./
        }

        events.assertIsABuild()

        events.operation("Resolve dependencies :compileClasspath", "Resolve dependencies of :compileClasspath")
        // TODO: currently not marked as failed
    }

    @ToolingApiVersion("<8.12")
    def "does not include dependency resolution that is a child of a task when task events are not included (Tooling API client < 8.12)"() {
        given:
        buildFile << """
            allprojects { apply plugin: 'java' }
"""
        file("src/main/java/Thing.java") << """class Thing { }"""
        file("src/test/java/Thing.java") << """class ThingTest { }"""

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, [OperationType.GENERIC] as Set)
                    .forTasks("build")
                    .run()
        }

        then:
        !events.operations.find { it.name.contains("compileClasspath") }
    }

    @ToolingApiVersion(">=8.12")
    def "does not include dependency resolution that is a child of a task when task events are not included (Tooling API client >= 8.12)"() {
        given:
        buildFile << """
            allprojects { apply plugin: 'java' }
"""
        file("src/main/java/Thing.java") << """class Thing { }"""
        file("src/test/java/Thing.java") << """class ThingTest { }"""

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, [OperationType.GENERIC, OperationType.ROOT] as Set)
                    .forTasks("build")
                    .run()
        }

        then:
        !events.operations.find { it.name.contains("compileClasspath") }
    }

    @TargetGradleVersion(">=3.3 <3.5")
    def "generates events for interleaved project configuration and dependency resolution"() {
        given:
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { apply plugin: 'java' }
            dependencies {
                compile project(':a')
            }
            configurations.compile.each { println it }
"""
        file("a/build.gradle") << """
            dependencies {
                compile project(':b')
            }
            configurations.compile.each { println it }
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
        configureBuild.children.contains(configureRoot)

        def resolveCompile = events.operation("Resolve dependencies :compile", "Resolve dependencies of :compile")
        resolveCompile.parent == configureRoot
        configureRoot.children == [resolveCompile]

        def configureA = events.operation("Configure project :a")
        configureA.parent == resolveCompile
        resolveCompile.children == [configureA]

        def resolveCompileA = events.operation("Resolve dependencies :a:compile", "Resolve dependencies of :a:compile")
        resolveCompileA.parent == configureA
        configureA.children == [resolveCompileA]

        def configureB = events.operation("Configure project :b")
        configureB.parent == resolveCompileA
        resolveCompileA.children == [configureB]
    }
}
