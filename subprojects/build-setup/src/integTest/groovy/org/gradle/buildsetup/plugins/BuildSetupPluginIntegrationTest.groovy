/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.plugins

import org.gradle.buildsetup.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.Matcher

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class BuildSetupPluginIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getMainTask() {
        return "setupBuild"
    }

    @Override
    String getPluginId() {
        "build-setup"
    }

    def "setupBuild shows up on tasks overview "() {
        when:
        def executed = run 'tasks'
        then:
        executed.output.contains "setupBuild - Initializes a new Gradle build."
    }

    def "can be executed without existing pom"() {
        given:
        assert !buildFile.exists()
        when:
        run 'setupBuild'
        then:
        new WrapperTestFixture(testDirectory).generated()
        buildFile.exists()
    }

    def "auto-applied setupBuild task can be triggered with camel-case"() {
        given:
        assert !buildFile.exists()
        when:
        run setupTaskNAme
        then:
        new WrapperTestFixture(testDirectory).generated()
        buildFile.exists()
        where:
        setupTaskNAme << ["setupBuild", "sBuild", "setupB"]
    }

    def "build file generation is skipped when build file already exists"() {
        given:
        assert buildFile.createFile()

        when:
        def executed = run('setupBuild')

        then:
        executed.assertTasksExecuted(":setupBuild")
        executed.output.contains("The build file 'build.gradle' already exists. Skipping build initialization.")
    }

    def "build file generation is skipped when settings file already exists"() {
        given:
        assert settingsFile.createFile()

        when:
        def executed = run('setupBuild')

        then:
        executed.assertTasksExecuted(":setupBuild")
        executed.output.contains("The settings file 'settings.gradle' already exists. Skipping build initialization.")
    }

    def "build file generation is skipped when custom build file exists"() {
        given:
        def customBuildScript = testDirectory.file("customBuild.gradle").createFile()

        when:
        executer.usingBuildScript(customBuildScript)
        def executed = run('setupBuild')

        then:
        executed.assertTasksExecuted(":setupBuild")
        executed.output.contains("The build file 'customBuild.gradle' already exists. Skipping build initialization.")
    }

    def "build file generation is skipped when part of a multi-project build with non-standard settings file location"() {
        given:
        def customSettings = testDirectory.file("customSettings.gradle")
        customSettings << """
include 'child'
"""
        when:
        executer.usingSettingsFile(customSettings)
        def executed = run('setupBuild')

        then:
        executed.assertTasksExecuted(":setupBuild")
        executed.output.contains("This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.")
    }

    def "pom conversion is triggered when pom and no gradle file found"() {
        given:
        pom()
        when:
        succeeds('setupBuild')
        then:
        pomValuesUsed()

    }

    def "pom conversion not triggered when setupBuild-type passed"() {
        given:
        pom()
        when:
        succeeds('setupBuild', '--type', 'java-library')
        then:
        pomValuesNotUsed()
    }

    def "gives decent error message when triggered with unknown setupBuild-type"() {
        when:
        fails('setupBuild', '--type', 'some-unknown-library')
        then:
        errorOutput.contains("Declared setup-type 'some-unknown-library' is not supported.")
    }


    private TestFile pom() {
        file("pom.xml") << """
      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>util</groupId>
        <artifactId>util</artifactId>
        <version>2.5</version>
        <packaging>jar</packaging>
      </project>"""
    }

    private pomValuesUsed() {
        buildFile.assertContents(containsPomGroup())
        buildFile.assertContents(containsPomVersion())
        settingsFile.assertContents(containsPomArtifactId())
    }

    private pomValuesNotUsed() {
        buildFile.assertContents(not(containsPomGroup()))
        buildFile.assertContents(not(containsPomVersion()))
        settingsFile.assertContents(not(containsPomArtifactId()))
    }

    private Matcher<String> containsPomGroup() {
        containsString("group = 'util'")
    }

    private Matcher<String> containsPomVersion() {
        containsString("version = '2.5'")
    }

    private Matcher<String> containsPomArtifactId() {
        containsString("rootProject.name = 'util'")
    }

}
