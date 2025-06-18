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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.maven.MavenModule

/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 */
class CompositeBuildCommandLineArgsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    MavenModule publishedModuleB

    def setup() {
        publishedModuleB = mavenRepo.module("org.test", "buildB", "1.0").publish()
        new ResolveTestFixture(buildA.buildFile).prepare()

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
    }

    def "passes project properties to included build"() {
        given:
        dependency 'org.test:buildB:1.0'
        and:

        [buildA, buildB].each {
            it.buildFile << """
    if (project.getProperty("passedProperty") != "foo") {
        throw new RuntimeException("property not passed to build")
    }
"""
        }

        when:
        execute(buildA, ":checkDeps", ["-PpassedProperty=foo"])

        then:
        assertTaskExecuted(":buildB", ":jar")
    }

    def "passes system property arguments to included build"() {
        given:
        dependency 'org.test:buildB:1.0'
        and:

        [buildA, buildB].each {
            it.buildFile << """
    if (providers.systemProperty('passedProperty').orNull != "foo") {
        throw new RuntimeException("property not passed to build")
    }
"""
        }

        when:
        execute(buildA, ":checkDeps", ["-DpassedProperty=foo"])

        then:
        assertTaskExecuted(":buildB", ":jar")
    }

    def "can include same build multiple times using --include-build and settings.gradle"() {
        given:
        dependency 'org.test:buildB:1.0'

        // Include 'buildB' twice via settings.gradle
        buildA.settingsFile << """
includeBuild '${buildB.toURI()}'
includeBuild '${buildB.toURI()}'
"""
        // Include 'buildB' twice via command-line arg
        def args = ["--include-build", '../buildB', "--include-build", '../buildB']

        when:
        execute(buildA, ":checkDeps", args)

        then:
        assertTaskExecuted(":buildB", ":jar")
    }

    def "does not exclude tasks when building artifact for included build"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        execute(buildA, ":checkDeps", ["--exclude-task", "jar"])

        then:
        assertTaskExecuted(":buildB", ":jar")
    }

    def "does not execute task actions when dry run specified on composite build"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        execute(buildA, ":build", ["--dry-run"])

        then:
        skipped(
            ":buildB:compileJava", ":buildB:processResources", ":buildB:classes", ":buildB:jar",
            ":compileJava", ":processResources", ":classes", ":jar", ":assemble",
            ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":check", ":build"
        )
    }

    def "dry-run can execute logic from included builds if it's required for configuration"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild 'build-logic-settings'
            }

            includeBuild 'build-logic-commons'
            includeBuild "build-logic"
        """
        settingsFile "build-logic-settings/settings.gradle", """
            println("I'm a build logic settings file")
        """
        buildFile "build-logic-settings/build.gradle", """
            plugins {
                id 'java'
            }
            tasks.register("settingsTask") {
               doLast {
                    println "I'm settings task"
               }
            }
            tasks.named("compileJava") {
                dependsOn "settingsTask"
            }
        """
        settingsFile "build-logic-commons/settings.gradle", """
            includeBuild('../build-logic-settings')
            include("basics")
        """
        buildFile "build-logic-commons/build.gradle", """
            plugins {
                id "base"
            }
            tasks.register("commonsTask") {
               dependsOn(":basics:commonsTask")
            }
        """
        buildFile "build-logic-commons/basics/build.gradle", """
            plugins {
                id 'java'
                id 'groovy-gradle-plugin'
            }
            tasks.register("commonsTask") {
               doLast {
                    println "I'm commons task"
               }
            }
            tasks.named("compileJava") {
                dependsOn "commonsTask"
            }
        """
        file('build-logic-commons/basics/src/main/groovy/dummy.plugin.gradle') << ""
        settingsFile "build-logic/settings.gradle", """
            pluginManagement {
                includeBuild '../build-logic-commons'
            }
        """
        buildFile "build-logic/build.gradle", """
            plugins {
                id "base"
                id 'dummy.plugin'
            }
        """

        buildFile """
            tasks.register("root") {
                dependsOn(gradle.includedBuild("build-logic-commons").task(":commonsTask"))
                dependsOn(gradle.includedBuild("build-logic").task(":check"))
                doLast {
                    println "I'm root task"
                }
            }
        """

        when:
        succeeds("root", "--dry-run")

        then:
        executedAndNotSkipped(
            ":build-logic-commons:basics:commonsTask", ":build-logic-commons:basics:jar"
        )
        skipped(":root", ":build-logic:check")
    }

    void skipped(String... taskNames) {
        for (String taskName : taskNames) {
            outputContains(taskName + " SKIPPED\n")
        }
    }

}
