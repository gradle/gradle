/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN

@LeaksFileHandles
class KotlinGradlePluginInitIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { 'plugin' }

    def "defaults to kotlin build scripts"() {
        when:
        run ('init', '--type', 'kotlin-gradle-plugin')

        then:
        dslFixtureFor(KOTLIN).assertGradleFilesGenerated()
    }

    @IgnoreIf({ GradleContextualExecuter.embedded }) // This test runs a build that itself runs a build in a test worker with 'gradleApi()' dependency, which needs to pick up Gradle modules from a real distribution
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'kotlin-gradle-plugin', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/kotlin").assertHasDescendants("some/thing/SomeThingPlugin.kt")
        subprojectDir.file("src/test/kotlin").assertHasDescendants("some/thing/SomeThingPluginTest.kt")
        subprojectDir.file("src/functionalTest/kotlin").assertHasDescendants("some/thing/SomeThingPluginFunctionalTest.kt")

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertDoesNotUseTestSuites()

        when:
        run("build")

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "plugin registers task")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "can run task")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @IgnoreIf({ GradleContextualExecuter.embedded }) // This test runs a build that itself runs a build in a test worker with 'gradleApi()' dependency, which needs to pick up Gradle modules from a real distribution
    def "creates build using test suites with #scriptDsl build scripts when using --incubating"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'kotlin-gradle-plugin', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/kotlin").assertHasDescendants("some/thing/SomeThingPlugin.kt")
        subprojectDir.file("src/test/kotlin").assertHasDescendants("some/thing/SomeThingPluginTest.kt")
        subprojectDir.file("src/functionalTest/kotlin").assertHasDescendants("some/thing/SomeThingPluginFunctionalTest.kt")

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertHasTestSuite("test")
        dslFixture.assertHasTestSuite("functionalTest")

        when:
        run("build")

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "plugin registers task")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "can run task")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @IgnoreIf({ GradleContextualExecuter.embedded }) // This test runs a build that itself runs a build in a test worker with 'gradleApi()' dependency, which needs to pick up Gradle modules from a real distribution
    def "creates with gradle.properties when using #scriptDsl build scripts with --incubating"() {
        when:
        run('init', '--type', 'kotlin-gradle-plugin', '--dsl', scriptDsl.id, '--incubating')

        then:
        gradlePropertiesGenerated()

        when:
        run("build")

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "plugin registers task")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "can run task")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Issue("https://github.com/gradle/gradle/issues/18206")
    @IgnoreIf({ GradleContextualExecuter.embedded }) // This test runs a build that itself runs builds in a test worker with 'gradleApi()' dependency, which needs to pick up Gradle modules from a real distribution
    def "re-running check succeeds with #scriptDsl"() {
        given:
        run('init', '--type', 'kotlin-gradle-plugin', '--dsl', scriptDsl.id)

        when:
        run('check')

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "plugin registers task")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "can run task")

        when:
        run('check', '--rerun-tasks')

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "plugin registers task")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "can run task")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
