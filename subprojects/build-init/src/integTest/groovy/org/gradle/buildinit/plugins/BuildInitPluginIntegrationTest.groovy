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

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.hamcrest.Matcher
import spock.lang.Unroll

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.GROOVY
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

class BuildInitPluginIntegrationTest extends AbstractInitIntegrationSpec {

    def "init shows up on tasks overview "() {
        given:
        targetDir.file("settings.gradle").touch()

        when:
        run 'tasks'

        then:
        outputContains "init - Initializes a new Gradle build."
    }

    @Unroll
    def "creates a simple project with #scriptDsl build scripts when no pom file present and no type specified"() {
        given:
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        runInitWith scriptDsl

        then:
        dslFixture.assertGradleFilesGenerated()
        targetDir.file(".gitignore").assertIsFile()

        and:
        dslFixture.buildFile.assertContents(
            allOf(
                containsString("This is a general purpose Gradle build"),
                containsString("Learn how to create Gradle builds at")))
        outputContains("Get more help with your project: ")

        expect:
        succeeds 'tasks'

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "#targetScriptDsl build file generation is skipped when #existingScriptDsl build file already exists"() {
        given:
        def existingDslFixture = dslFixtureFor(existingScriptDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl)

        and:
        existingDslFixture.buildFile.createFile()

        when:
        runInitWith targetScriptDsl

        then:
        result.assertTasksExecuted(":init")
        outputContains("The build file '${existingDslFixture.buildFileName}' already exists. Skipping build initialization.")

        and:
        !targetDslFixture.settingsFile.exists()
        targetDslFixture.assertWrapperNotGenerated()

        where:
        [existingScriptDsl, targetScriptDsl] << ScriptDslFixture.scriptDslCombinationsFor(2)
    }

    @Unroll
    def "#targetScriptDsl build file generation is skipped when #existingScriptDsl settings file already exists"() {
        given:
        def existingDslFixture = dslFixtureFor(existingScriptDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl)

        and:
        existingDslFixture.settingsFile.createFile()

        when:
        runInitWith targetScriptDsl

        then:
        result.assertTasksExecuted(":init")
        outputContains("The settings file '${existingDslFixture.settingsFileName}' already exists. Skipping build initialization.")

        and:
        !targetDslFixture.buildFile.exists()
        targetDslFixture.assertWrapperNotGenerated()

        where:
        [existingScriptDsl, targetScriptDsl] << ScriptDslFixture.scriptDslCombinationsFor(2)
    }

    @Unroll
    def "#targetScriptDsl build file generation is skipped when custom #existingScriptDsl build file exists"() {
        given:
        def existingDslFixture = dslFixtureFor(existingScriptDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl)

        and:
        def customBuildScript = existingDslFixture.scriptFile("customBuild").createFile()

        when:
        executer.usingBuildScript(customBuildScript)
        runInitWith targetScriptDsl

        then:
        result.assertTasksExecuted(":init")
        outputContains("The build file '${customBuildScript.name}' already exists. Skipping build initialization.")

        and:
        !targetDslFixture.buildFile.exists()
        !targetDslFixture.settingsFile.exists()
        targetDslFixture.assertWrapperNotGenerated()

        where:
        [existingScriptDsl, targetScriptDsl] << ScriptDslFixture.scriptDslCombinationsFor(2)
    }

    @Unroll
    def "#targetScriptDsl build file generation is skipped when part of a multi-project build with non-standard #existingScriptDsl settings file location"() {
        given:
        def existingDslFixture = dslFixtureFor(existingScriptDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl)

        and:
        def customSettings = existingDslFixture.scriptFile("customSettings")
        customSettings << """
include("child")
"""

        when:
        executer.usingSettingsFile(customSettings)
        runInitWith targetScriptDsl

        then:
        result.assertTasksExecuted(":init")
        outputContains("This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.")

        and:
        !targetDslFixture.buildFile.exists()
        !targetDslFixture.settingsFile.exists()
        targetDslFixture.assertWrapperNotGenerated()

        where:
        [existingScriptDsl, targetScriptDsl] << ScriptDslFixture.scriptDslCombinationsFor(2)
    }

    def "pom conversion to groovy build scripts is triggered when pom and no gradle file found"() {
        given:
        pom()

        when:
        run('init')

        then:
        pomValuesUsed(dslFixtureFor(GROOVY))
    }

    @Unroll
    def "pom conversion to #scriptDsl build scripts not triggered when build type is specified"() {
        given:
        pom()

        when:
        succeeds('init', '--type', 'java-library', '--dsl', scriptDsl.id)

        then:
        pomValuesNotUsed(dslFixtureFor(scriptDsl))

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "gives decent error message when triggered with unknown init-type"() {
        when:
        fails('init', '--type', 'some-unknown-library')

        then:
        failure.assertHasCause("""The requested build type 'some-unknown-library' is not supported. Supported types:
  - 'basic'
  - 'cpp-application'
  - 'cpp-library'
  - 'groovy-application'
  - 'groovy-gradle-plugin'
  - 'groovy-library'
  - 'java-application'
  - 'java-gradle-plugin'
  - 'java-library'
  - 'kotlin-application'
  - 'kotlin-gradle-plugin'
  - 'kotlin-library'
  - 'pom'
  - 'scala-library'""")
    }

    def "gives decent error message when triggered with unknown dsl"() {
        when:
        fails('init', '--dsl', 'some-unknown-dsl')

        then:
        failure.assertHasCause("""The requested build script DSL 'some-unknown-dsl' is not supported. Supported DSLs:
  - 'groovy'
  - 'kotlin'""")
    }

    def "gives decent error message when using unknown test framework"() {
        when:
        fails('init', '--type', 'basic', '--test-framework', 'fake')

        then:
        failure.assertHasCause("""The requested test framework 'fake' is not supported for 'basic' build type. Supported frameworks:
  - 'none'""")
    }

    def "gives decent error message when test framework is not supported by specific type"() {
        when:
        fails('init', '--type', 'basic', '--test-framework', 'spock')

        then:
        failure.assertHasCause("""The requested test framework 'spock' is not supported for 'basic' build type. Supported frameworks:
  - 'none'""")
    }

    def "gives decent error message when project name option is not supported by specific type"() {
        when:
        fails('init', '--type', 'pom', '--project-name', 'thing')

        then:
        failure.assertHasCause("Project name is not supported for 'pom' build type.")
    }

    def "gives decent error message when package name option is not supported by specific type"() {
        when:
        fails('init', '--type', 'basic', '--package', 'thing')

        then:
        failure.assertHasCause("Package name is not supported for 'basic' build type.")
    }

    def "displays all build types and modifiers in help command output"() {
        when:
        run('help', '--task', 'init')

        then:
        outputContains("""Options
     --dsl     Set the build script DSL to be used in generated scripts.
               Available values are:
                    groovy
                    kotlin

     --package     Set the package for source files.

     --project-name     Set the project name.

     --test-framework     Set the test framework to be used.
                          Available values are:
                               junit
                               junit-jupiter
                               kotlintest
                               scalatest
                               spock
                               testng

     --type     Set the type of project to generate.
                Available values are:
                     basic
                     cpp-application
                     cpp-library
                     groovy-application
                     groovy-gradle-plugin
                     groovy-library
                     java-application
                     java-gradle-plugin
                     java-library
                     kotlin-application
                     kotlin-gradle-plugin
                     kotlin-library
                     pom
                     scala-library""")
    }

    def "does not warn or fail when initializing inside another build"() {
        given:
        def sub = file("sub")
        sub.mkdirs()
        executer.inDirectory(sub)

        when:
        file("settings.gradle") << "rootProject.name = 'root'"

        then:
        succeeds "init"
    }

    def "ignores gradle properties for existing build when initializing inside another project"() {
        given:
        def sub = file("sub")
        sub.mkdirs()
        executer.inDirectory(sub)

        when:
        file("settings.gradle") << "rootProject.name = 'root'"
        file("gradle.properties") << "org.gradle.jvmargs=-Xmx=BAD"

        then:
        succeeds "init"
    }

    private ExecutionResult runInitWith(BuildInitDsl dsl) {
        run 'init', '--dsl', dsl.id
    }

    private static pomValuesUsed(ScriptDslFixture dslFixture) {
        dslFixture.buildFile.assertContents(containsPomGroup(dslFixture))
        dslFixture.buildFile.assertContents(containsPomVersion(dslFixture))
        dslFixture.settingsFile.assertContents(containsPomArtifactId(dslFixture))
    }

    private static pomValuesNotUsed(ScriptDslFixture dslFixture) {
        dslFixture.buildFile.assertContents(not(containsPomGroup(dslFixture)))
        dslFixture.buildFile.assertContents(not(containsPomVersion(dslFixture)))
        dslFixture.settingsFile.assertContents(not(containsPomArtifactId(dslFixture)))
    }

    private static Matcher<String> containsPomGroup(ScriptDslFixture dslFixture) {
        dslFixture.containsStringAssignment('group', 'util')
    }

    private static Matcher<String> containsPomVersion(ScriptDslFixture dslFixture) {
        dslFixture.containsStringAssignment('version', '2.5')
    }

    private static Matcher<String> containsPomArtifactId(ScriptDslFixture dslFixture) {
        dslFixture.containsStringAssignment('rootProject.name', 'util')
    }
}
