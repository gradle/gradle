/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.composite

/**
 * Tests for referencing tasks in {@code buildSrc} via {@code gradle.includedBuild('buildSrc').task(...)},
 * the same way tasks of any other included build can be referenced.
 */
class CompositeBuildBuildSrcTaskDependencyIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def setup() {
        buildA.file("buildSrc/build.gradle") << """
            allprojects {
                task logProject {
                    def rootProjectName = project.rootProject.name
                    def projectPath = project.path
                    doLast {
                        println "Executing build '" + rootProjectName + "' project '" + projectPath + "' task '" + path + "'"
                    }
                }
            }
        """
    }

    def "can depend on task in root project of buildSrc"() {
        when:
        buildA.buildFile << """
            task delegate {
                dependsOn gradle.includedBuild('buildSrc').task(':logProject')
            }
        """

        execute(buildA, ":delegate")

        then:
        executed ":buildSrc:logProject"
        output.contains("Executing build 'buildSrc' project ':' task ':logProject'")
    }

    def "can depend on task in subproject of buildSrc"() {
        given:
        createDirs("buildA/buildSrc", "buildA/buildSrc/sub")
        buildA.file("buildSrc/settings.gradle") << """
            include 'sub'
        """

        when:
        buildA.buildFile << """
            task delegate {
                dependsOn gradle.includedBuild('buildSrc').task(':sub:logProject')
            }
        """

        execute(buildA, ":delegate")

        then:
        executed ":buildSrc:sub:logProject"
        output.contains("Executing build 'buildSrc' project ':sub' task ':sub:logProject'")
    }

    def "can depend on multiple tasks of buildSrc"() {
        given:
        createDirs("buildA/buildSrc", "buildA/buildSrc/sub")
        buildA.file("buildSrc/settings.gradle") << """
            include 'sub'
        """

        when:
        buildA.buildFile << """
            def buildSrc = gradle.includedBuild('buildSrc')
            task delegate {
                dependsOn 'delegate1', 'delegate2'
            }
            task delegate1 {
                dependsOn buildSrc.task(':logProject')
                dependsOn buildSrc.task(':sub:logProject')
            }
            task delegate2 {
                dependsOn buildSrc.task(':logProject')
            }
        """

        execute(buildA, ":delegate")

        then:
        executed ":buildSrc:logProject", ":buildSrc:sub:logProject"
        output.contains("Executing build 'buildSrc' project ':' task ':logProject'")
        output.contains("Executing build 'buildSrc' project ':sub' task ':sub:logProject'")
    }

    def "can depend on task in buildSrc from subproject of composing build"() {
        given:
        createDirs("buildA", "buildA/a1")
        buildA.settingsFile << """
            include 'a1'
        """
        buildA.buildFile << """
            task("top-level") {
                dependsOn ':a1:delegate'
            }

            project(':a1') {
                task delegate {
                    dependsOn gradle.includedBuild('buildSrc').task(':logProject')
                }
            }
        """

        when:
        execute(buildA, ":top-level")

        then:
        executed ":buildSrc:logProject"
        output.contains("Executing build 'buildSrc' project ':' task ':logProject'")
    }

    def "reports failure when task does not exist for buildSrc"() {
        when:
        buildA.buildFile << """
            task delegate {
                dependsOn gradle.includedBuild('buildSrc').task(':does-not-exist')
            }
        """

        and:
        fails(buildA, ":delegate")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':delegate'.")
        failure.assertHasCause("Task with name 'does-not-exist' not found in project ':buildSrc'.")
    }

    def "reports failure when task path is not qualified for buildSrc"() {
        when:
        buildA.buildFile << """
            task delegate {
                dependsOn gradle.includedBuild('buildSrc').task('logProject')
            }
        """

        and:
        fails(buildA, "delegate")

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'buildA'.")
        failure.assertHasCause("Task path 'logProject' is not a qualified task path (e.g. ':task' or ':project:task')")
    }

    def "buildSrc cannot reference tasks in itself via includedBuild"() {
        when:
        buildA.file("buildSrc/build.gradle") << """
            task illegal {
                dependsOn gradle.includedBuild('buildSrc').task(':logProject')
            }
        """

        and:
        fails(buildA, ":buildSrc:illegal")

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':buildSrc'.")
        failure.assertHasCause("Included build 'buildSrc' not found in build 'buildSrc'.")
    }
}
