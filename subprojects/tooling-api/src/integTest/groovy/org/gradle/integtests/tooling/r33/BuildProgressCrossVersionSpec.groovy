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
import org.gradle.tooling.ProjectConnection

@TargetGradleVersion(">=3.3")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification {
    def "generates project configuration events for single project build"() {
        given:
        settingsFile << "rootProject.name = 'single'"

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        def configureRootProject = events.operation("Configure root project 'single'")
        configureRootProject.parent == events.operation("Configure build")
    }

    def "generates project configuration events for multi-project build"() {
        given:
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        def configureBuild = events.operation("Configure build")

        def configureRoot = events.operation("Configure root project 'multi'")
        configureRoot.parent == configureBuild

        def configureA = events.operation("Configure project ':a'")
        configureA.parent == configureBuild

        def configureB = events.operation("Configure project ':b'")
        configureB.parent == configureBuild
    }

    def "generates events for nested project configuration"() {
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
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        def configureRoot = events.operation("Configure root project 'multi'")
        configureRoot.parent == events.operation("Configure build")

        def configureA = events.operation("Configure project ':a'")
        configureA.parent == configureRoot

        def configureB = events.operation("Configure project ':b'")
        configureB.parent == configureA
    }

    def "generates events for dependency resolution"() {
        given:
        buildFile << """
            allprojects { apply plugin: 'java' }
"""
        file("src/main/java/Thing.java") << """class Thing { }"""
        file("src/test/java/Thing.java") << """class ThingTest { }"""

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .forTasks("build")
                        .run()
        }

        then:
        events.operation("Resolve configuration ':compileClasspath'")
        events.operation("Resolve configuration ':testCompileClasspath'")
    }

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
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        def configureRoot = events.operation("Configure root project 'multi'")
        configureRoot.parent == events.operation("Configure build")

        def resolveCompile = events.operation("Resolve configuration ':compile'")
        resolveCompile.parent == configureRoot

        def configureA = events.operation("Configure project ':a'")
        configureA.parent == resolveCompile

        def resolveCompileA = events.operation("Resolve configuration ':a:compile'")
        resolveCompileA.parent == configureA

        def configureB = events.operation("Configure project ':b'")
        configureB.parent == resolveCompileA
    }

}
