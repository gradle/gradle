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



package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.Matcher

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class BuildInitPluginIntegrationTest extends WellBehavedPluginTest {
    final wrapper = new WrapperTestFixture(testDirectory)

    @Override
    String getMainTask() {
        return "init"
    }

    def "init shows up on tasks overview "() {
        when:
        run 'tasks'

        then:
        result.output.contains "init - Initializes a new Gradle build."
    }

    def "creates a simple project when no pom file present and no type specified"() {
        given:
        assert !buildFile.exists()
        assert !settingsFile.exists()

        when:
        run 'init'

        then:
        wrapper.generated()
        buildFile.exists()
        settingsFile.exists()

        expect:
        succeeds 'tasks'
    }

    def "build file generation is skipped when build file already exists"() {
        given:
        buildFile.createFile()

        when:
        run('init')

        then:
        result.assertTasksExecuted(":init")
        result.output.contains("The build file 'build.gradle' already exists. Skipping build initialization.")

        and:
        !settingsFile.exists()
        wrapper.notGenerated()
    }

    def "build file generation is skipped when settings file already exists"() {
        given:
        settingsFile.createFile()

        when:
        run('init')

        then:
        result.assertTasksExecuted(":init")
        result.output.contains("The settings file 'settings.gradle' already exists. Skipping build initialization.")

        and:
        !buildFile.exists()
        wrapper.notGenerated()
    }

    def "build file generation is skipped when custom build file exists"() {
        given:
        def customBuildScript = testDirectory.file("customBuild.gradle").createFile()

        when:
        executer.usingBuildScript(customBuildScript)
        run('init')

        then:
        result.assertTasksExecuted(":init")
        result.output.contains("The build file 'customBuild.gradle' already exists. Skipping build initialization.")

        and:
        !buildFile.exists()
        !settingsFile.exists()
        wrapper.notGenerated()
    }

    def "build file generation is skipped when part of a multi-project build with non-standard settings file location"() {
        given:
        def customSettings = testDirectory.file("customSettings.gradle")
        customSettings << """
include 'child'
"""

        when:
        executer.usingSettingsFile(customSettings)
        run('init')

        then:
        result.assertTasksExecuted(":init")
        result.output.contains("This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.")

        and:
        !buildFile.exists()
        !settingsFile.exists()
        wrapper.notGenerated()
    }

    def "pom conversion is triggered when pom and no gradle file found"() {
        given:
        pom()

        when:
        run('init')

        then:
        pomValuesUsed()
    }

    def "pom conversion not triggered when build type is specified"() {
        given:
        pom()

        when:
        succeeds('init', '--type', 'java-library')

        then:
        pomValuesNotUsed()
    }

    def "gives decent error message when triggered with unknown init-type"() {
        when:
        fails('init', '--type', 'some-unknown-library')

        then:
        failure.assertHasCause("The requested build setup type 'some-unknown-library' is not supported.")
    }

    def "gives decent error message when using unknown test framework"() {
        when:
        fails('init', '--type', 'basic', '--test-framework', 'fake')

        then:
        failure.assertHasCause("The requested test framework 'fake' is not supported.")
    }

    def "gives decent error message when test framework is not supported by specific type"() {
        when:
        fails('init', '--type', 'basic', '--test-framework', 'spock')

        then:
        failure.assertHasCause("The requested test framework 'spock' is not supported in 'basic' setup type")
    }

    def "displays all build types and modifiers in help command output"() {
        when:
        run('help', '--task', 'init')

        then:
        result.output.contains("""Options
     --type     Set type of build to create.
                Available values are:
                     basic
                     groovy-application
                     groovy-library
                     java-application
                     java-library
                     pom
                     scala-library

     --test-framework     Set alternative test framework to be used.
                          Available values are:
                               spock
                               testng""");
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
