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

    def "does not pass build-file argument when configuring included build"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildA.settingsFile << """
rootProject.buildFileName='build-copy.gradle'
"""

        buildA.file("build-copy.gradle").copyFrom(buildA.buildFile)

        when:
        executer.expectDocumentedDeprecationWarning("Specifying custom build file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        execute(buildA, ":checkDeps", ["--build-file", "build-copy.gradle"])

        then:
        assertTaskExecuted(":buildB", ":jar")
    }

    def "does not pass settings-file argument when configuring included build"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildA.file("settings-copy.gradle") << """
rootProject.name = 'buildA'
includeBuild '../buildB'
"""

        when:
        executer.expectDocumentedDeprecationWarning("Specifying custom settings file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        execute(buildA, ":checkDeps", ["--settings-file", "settings-copy.gradle"])

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

    // Included build tasks are incorrect executed with `--dry-run`. See gradle/composite-builds#113
    def "does not execute task actions when dry run specified on composite build"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        execute(buildA, ":build", ["--dry-run"])

        then:
        skipped(
            ":compileJava", ":processResources", ":classes", ":jar", ":assemble",
            ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":check", ":build")
    }

    void skipped(String... taskNames) {
        for (String taskName : taskNames) {
            outputContains(taskName + " SKIPPED\n")
        }
    }

}
