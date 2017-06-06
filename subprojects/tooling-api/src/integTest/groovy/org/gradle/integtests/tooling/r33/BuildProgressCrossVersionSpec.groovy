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
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=3.3")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification {
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

        configureBuild.children == [configureRoot, configureA]
    }

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
                repositories { mavenCentral() }
                dependencies { testCompile 'junit:junit:4.12' }
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
        compileClasspath.parent == compileJava

        def testCompileClasspath = events.operation("Resolve dependencies :testCompileClasspath", "Resolve dependencies of :testCompileClasspath")
        testCompileClasspath.parent == compileTestJava

        def testRuntimeClasspath = events.operation(
            "Resolve dependencies :testRuntime", "Resolve dependencies :testRuntimeClasspath",
            "Resolve dependencies of :testRuntime", "Resolve dependencies of :testRuntimeClasspath")
        testRuntimeClasspath.parent == test
    }

    def "generates events for failed dependency resolution"() {
        given:
        buildFile << """
            allprojects { apply plugin: 'java' }
            dependencies { compile 'thing:thing:1.0' }
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
        e.cause.message =~ /Could not resolve all (dependencies|files) for configuration ':compileClasspath'./

        events.assertIsABuild()

        events.operation("Resolve dependencies :compileClasspath", "Resolve dependencies of :compileClasspath")
        // TODO: currently not marked as failed
    }

    def "does not include dependency resolution that is a child of a task when task events are not included"() {
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

        buildSrcTasks.child("Task :buildSrc:a:compileJava").child("Resolve dependencies :buildSrc:a:compileClasspath", "Resolve dependencies of :buildSrc:a:compileClasspath")
        buildSrcTasks.child("Task :buildSrc:b:compileJava").child("Resolve dependencies :buildSrc:b:compileClasspath", "Resolve dependencies of :buildSrc:b:compileClasspath")

        buildSrcTasks.child("Task :buildSrc:a:test").descendant("Gradle Test Run :buildSrc:a:test")
        buildSrcTasks.child("Task :buildSrc:b:test")

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
        events.operation("Task :buildSrc:a:test")
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
        events.operation("Gradle Test Run :buildSrc:a:test")
        events.operation("Test ok(Test)")
        events.operation("Gradle Test Run :test")
    }

    def javaProjectWithTests() {
        buildFile << """
            allprojects { 
                apply plugin: 'java'
                repositories { mavenCentral() }
                dependencies { testCompile 'junit:junit:4.12' }
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
                repositories { mavenCentral() }
                dependencies { testCompile 'junit:junit:4.12' }
            }
            dependencies {
                compile project(':a')
                compile project(':b')
            }
"""
        file("buildSrc/a/src/main/java/A.java") << "public class A {}"
        file("buildSrc/a/src/test/java/Test.java") << "public class Test { @org.junit.Test public void ok() { } }"
        file("buildSrc/b/src/main/java/B.java") << "public class B {}"
        file("buildSrc/b/src/test/java/Test.java") << "public class Test { @org.junit.Test public void ok() { } }"
    }
}
