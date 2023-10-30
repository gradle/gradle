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
import org.gradle.buildinit.plugins.internal.BuildScriptBuilder
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.hamcrest.Matcher

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

class BuildInitPluginIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { 'app' }

    def "init shows up on tasks overview "() {
        given:
        targetDir.file("settings.gradle").touch()

        when:
        run 'tasks'

        then:
        outputContains "init - Initializes a new Gradle build."
    }

    def "creates a simple project with #scriptDsl build scripts when no pom file present and no type specified"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        def dslFixture = ScriptDslFixture.of(scriptDsl, targetDir, null)

        when:
        runInitWith scriptDsl

        then:
        commonFilesGenerated(scriptDsl, dslFixture)

        and:
        dslFixture.buildFile.assertContents(
            allOf(
                containsString("This is a general purpose Gradle build"),
                containsString(documentationRegistry.getSampleForMessage())
            )
        )

        expect:
        succeeds 'properties'

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates a simple project with #scriptDsl build scripts when no pom file present and no type specified which uses @Incubating APIs"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        def dslFixture = ScriptDslFixture.of(scriptDsl, targetDir, null)

        when:
        runInitWith scriptDsl, '--incubating'

        then:
        commonFilesGenerated(scriptDsl, dslFixture)

        and:
        dslFixture.buildFile.assertContents(
            allOf(
                containsString("This is a general purpose Gradle build"),
                containsString(documentationRegistry.getSampleForMessage()),
                containsString(BuildScriptBuilder.getIncubatingApisWarning())))

        expect:
        succeeds 'properties'

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "#targetScriptDsl build file generation is skipped when #existingScriptDsl build file already exists"() {
        given:
        def existingDslFixture = rootProjectDslFixtureFor(existingScriptDsl as BuildInitDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl as BuildInitDsl)

        and:
        existingDslFixture.buildFile.createFile()

        when:
        runInitWith targetScriptDsl as BuildInitDsl

        then:
        result.assertTasksExecuted(":init")
        outputContains("The build file '${existingDslFixture.buildFileName}' already exists. Skipping build initialization.")

        and:
        !targetDslFixture.settingsFile.exists()
        targetDslFixture.assertWrapperNotGenerated()

        where:
        [existingScriptDsl, targetScriptDsl] << ScriptDslFixture.scriptDslCombinationsFor(2)
    }

    def "#targetScriptDsl build file generation is skipped when #existingScriptDsl settings file already exists"() {
        given:
        def existingDslFixture = dslFixtureFor(existingScriptDsl as BuildInitDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl as BuildInitDsl)

        and:
        existingDslFixture.settingsFile.createFile()

        when:
        runInitWith targetScriptDsl as BuildInitDsl

        then:
        result.assertTasksExecuted(":init")
        outputContains("The settings file '${existingDslFixture.settingsFileName}' already exists. Skipping build initialization.")

        and:
        !targetDslFixture.buildFile.exists()
        targetDslFixture.assertWrapperNotGenerated()

        where:
        [existingScriptDsl, targetScriptDsl] << ScriptDslFixture.scriptDslCombinationsFor(2)
    }

    def "#targetScriptDsl build file generation is skipped when custom #existingScriptDsl build file exists"() {
        given:
        def existingDslFixture = dslFixtureFor(existingScriptDsl as BuildInitDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl as BuildInitDsl)

        and:
        def customBuildScript = existingDslFixture.scriptFile("build").createFile()

        when:
        runInitWith targetScriptDsl as BuildInitDsl

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

    def "#targetScriptDsl build file generation is skipped when part of a multi-project build with non-standard #existingScriptDsl settings file location"() {
        given:
        def existingDslFixture = dslFixtureFor(existingScriptDsl as BuildInitDsl)
        def targetDslFixture = dslFixtureFor(targetScriptDsl as BuildInitDsl)

        and:
        def customSettings = existingDslFixture.scriptFile("customSettings")
        customSettings.parentFile.createDirs("child")
        customSettings << """
            include("child")
        """

        when:
        executer.usingSettingsFile(customSettings)
        executer.expectDocumentedDeprecationWarning("Specifying custom settings file location has been deprecated. This is scheduled to be removed in Gradle 9.0. " +
            "Consult the upgrading guide for further information: ${documentationRegistry.getDocumentationFor("upgrading_version_7", "configuring_custom_build_layout")}")
        runInitWith targetScriptDsl as BuildInitDsl

        then:
        result.assertTasksExecuted(":init")
        outputContains("The settings file '${customSettings.name}' already exists. Skipping build initialization.")

        and:
        !targetDslFixture.buildFile.exists()
        !targetDslFixture.settingsFile.exists()
        targetDslFixture.assertWrapperNotGenerated()

        where:
        [existingScriptDsl, targetScriptDsl] << ScriptDslFixture.scriptDslCombinationsFor(2)
    }

    def "pom conversion to kotlin build scripts is triggered when pom and no gradle file found"() {
        given:
        pom()

        when:
        run('init')

        then:
        pomValuesUsed(rootProjectDslFixtureFor(KOTLIN))
    }

    def "pom conversion to #scriptDsl build scripts not triggered when build type is specified"() {
        given:
        pom()

        when:
        succeeds('init', '--type', 'java-application', '--dsl', scriptDsl.id)

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
  - 'scala-application'
  - 'scala-library'""")
    }

    def "gives decent error message when triggered with unknown dsl"() {
        when:
        fails('init', '--dsl', 'some-unknown-dsl')

        then:
        failure.assertHasCause("""The requested build script DSL 'some-unknown-dsl' is not supported. Supported DSLs:
  - 'kotlin'
  - 'groovy'""")
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
        targetDir.file("settings.gradle").touch()
        run('help', '--task', 'init')

        then:
        outputContains("""Options
     --dsl     Set the build script DSL to be used in generated scripts.
               Available values are:
                    groovy
                    kotlin

     --incubating     Allow the generated build to use new features and APIs.

     --no-incubating     Disables option --incubating.

     --insecure-protocol     How to handle insecure URLs used for Maven Repositories.
                             Available values are:
                                  ALLOW
                                  FAIL
                                  UPGRADE
                                  WARN

     --java-version     Provides java version to use in the project.

     --package     Set the package for source files.

     --project-name     Set the project name.

     --split-project     Split functionality across multiple subprojects?

     --no-split-project     Disables option --split-project.

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
                     scala-application
                     scala-library""")
    }

    def "can initialize in a directory that is under another build's root directory"() {
        when:
        containerDir.file("settings.gradle") << "rootProject.name = 'root'"

        then:
        succeeds "init"
        targetDir.file("settings.gradle.kts").assertIsFile()
        targetDir.file("build.gradle.kts").assertIsFile()
    }

    def "fails when initializing in a project directory of another build that contains a build script"() {
        when:
        containerDir.file("settings.gradle") << """
            rootProject.name = 'root'
            include('${targetDir.name}')
        """
        targetDir.file("build.gradle") << """
            // empty
        """

        then:
        fails "init"
        failure.assertHasDescription("Task 'init' not found in project ':some-thing'.")
        targetDir.assertHasDescendants("build.gradle")
    }

    def "fails when initializing in a project directory of another build that does not contain a build script"() {
        when:
        containerDir.file("settings.gradle") << """
            rootProject.name = 'root'
            include('${targetDir.name}')
        """

        then:
        fails "init"
        failure.assertHasDescription("Task 'init' not found in project ':some-thing'.")
        targetDir.listFiles().size() == 0 // Is still empty
    }

    def "warns when initializing in root project directory of another single project build that does not contain a build script"() {
        when:
        containerDir.file("settings.gradle") << """
            rootProject.name = 'root'
            rootProject.projectDir = file('${targetDir.name}')
        """

        then:
        succeeds "init"
        outputContains("The settings file '..${File.separatorChar}settings.gradle' already exists. Skipping build initialization.")
        targetDir.list().size() == 0 // ensure nothing generated
    }

    def "can create build in user home directory"() {
        when:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        def dotGradleDir = targetDir.file('.gradle')
        dotGradleDir.mkdirs()
        def gradlePropertiesFile = dotGradleDir.file("gradle.properties").touch()
        gradlePropertiesFile << """
            foo=bar
        """
        def snapshot = gradlePropertiesFile.snapshot()
        executer.withGradleUserHomeDir(dotGradleDir)
        executer.withArguments("--project-cache-dir", dotGradleDir.path)

        then:
        succeeds "init"
        targetDir.file("gradlew").assertIsFile()
        targetDir.file("settings.gradle.kts").assertIsFile()
        targetDir.file("build.gradle.kts").assertIsFile()
        targetDir.file(".gradle/gradle.properties").assertHasNotChangedSince(snapshot)
    }

    def "can create build in user home directory when user home directory has custom name"() {
        when:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        def dotGradleDir = targetDir.file('.guh')
        dotGradleDir.mkdirs()
        executer.withGradleUserHomeDir(dotGradleDir)

        then:
        succeeds "init"
        targetDir.file("gradlew").assertIsFile()
        targetDir.file("settings.gradle.kts").assertIsFile()
        targetDir.file("build.gradle.kts").assertIsFile()
    }

    private ExecutionResult runInitWith(BuildInitDsl dsl, String... initOptions) {
        def tasks = ['init', '--dsl', dsl.id]
        tasks.addAll(initOptions)
        run tasks
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
