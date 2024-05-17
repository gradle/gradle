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

package org.gradle.integtests.composite

import org.gradle.api.Plugin
import org.gradle.api.Project

class CompositeBuildNestingIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    def "can nest included builds"() {
        given:
        dependency(buildA, "org.test:buildB:1.2")

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildB)

        when:
        execute(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildC:jar")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":jar")
    }

    def "a nested included build is substituted into all other builds"() {
        given:
        dependency(buildA, "org.test:buildB:1.2")
        dependency(buildA, "org.test:buildC:1.2")

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }

        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildD:1.2' }
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildB)

        def buildD = singleProjectBuild("buildD") {
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildD)

        when:
        execute(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildC:jar")
        result.assertTaskExecuted(":buildD:jar")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":jar")
    }

    def "a build can be included by multiple other builds"() {
        given:
        dependency(buildA, "org.test:buildB:1.2")

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        includeBuild(buildC)
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                apply plugin: 'java'
                dependencies { implementation 'org.test:buildC:1.2' }
            """
        }
        includeBuild(buildB)

        when:
        execute(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildC:jar")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":jar")
    }

    def "nested build can contribute to build script classpath"() {
        def buildC = singleProjectBuild("buildC") {
            settingsFile << """
                rootProject.name = 'libc'
            """
            buildFile << """
                apply plugin: 'java'
            """
            file("src/main/java/LibC.java") << """
                public class LibC { }
            """
        }
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                apply plugin: 'java-gradle-plugin'
                dependencies { implementation 'org.test:libc:1.2' }
                gradlePlugin.plugins {
                    b {
                        id = 'b'
                        implementationClass = 'PluginB'
                    }
                }
            """
            file("src/main/java/PluginB.java") << """
                import ${Project.name};
                import ${Plugin.name};
                public class PluginB implements Plugin<Project> {
                    public void apply(Project project) {
                        new LibC();
                        project.getTasks().register("go");
                    }
                }
            """
        }

        buildA.settingsFile.text = """
            pluginManagement {
                includeBuild("${buildB.toURI()}")
                resolutionStrategy.eachPlugin { details ->
                    if (details.requested.id.name == 'b') {
                        details.useModule('org.test:buildB:1.2')
                    }
                }
            }
        """ + buildA.settingsFile.text
        buildA.buildFile.text = """
            plugins { id 'b' version '12' }
        """ + buildA.buildFile.text

        when:
        execute(buildA, "go")

        then:
        result.assertTaskExecuted(":buildC:jar")
        result.assertTaskExecuted(":buildB:jar")
        result.assertTaskExecuted(":go")
    }

    def "reports failure for duplicate included build name"() {
        given:
        def buildC = singleProjectBuild("buildC")
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}') {
                    name = 'buildB'
                }
            """
        }
        includeBuild(buildB)

        when:
        fails(buildA, "help")

        then:
        failure.assertHasDescription("Included build $buildC has build path :buildB which is the same as included build $buildB")
    }

    def "reports failure for included build name that conflicts with subproject name"() {
        given:
        createDirs("buildA", "buildA/buildC")
        buildA.settingsFile << """
            include 'buildC'
"""
        def buildC = singleProjectBuild("buildC")
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
        }
        includeBuild(buildB)

        when:
        fails(buildA, "help")

        then:
        failure.assertHasDescription("Included build in ${buildC} has name 'buildC' which is the same as a project of the main build.")
    }

    def "included build name can be the same as root project name"() {
        given:
        def buildC = singleProjectBuild("buildC") {
            settingsFile << """
                rootProject.name = 'buildA'
            """
        }
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
        }

        when:
        includeBuild(buildB)

        then:
        execute(buildA, "help")
    }
}
