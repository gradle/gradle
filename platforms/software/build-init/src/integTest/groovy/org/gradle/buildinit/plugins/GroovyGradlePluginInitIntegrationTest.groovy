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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.GROOVY

@Requires(value = IntegTestPreconditions.Groovy3OrEarlier)
// FIXME if Groovy 4 is bundled, cannot run without regenerating library-versions.properties
class GroovyGradlePluginInitIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { 'plugin' }

    def "defaults to Groovy build scripts"() {
        when:
        run('init', '--type', 'groovy-gradle-plugin')

        then:
        dslFixtureFor(GROOVY).assertGradleFilesGenerated()
    }

    public static final String NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON = "This test runs a build that itself runs a build in a test worker with 'gradleApi()' dependency, which needs to pick up Gradle modules from a real distribution"

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'groovy-gradle-plugin', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/groovy").assertHasDescendants("some/thing/SomeThingPlugin.groovy")
        subprojectDir.file("src/test/groovy").assertHasDescendants("some/thing/SomeThingPluginTest.groovy")
        subprojectDir.file("src/functionalTest/groovy").assertHasDescendants("some/thing/SomeThingPluginFunctionalTest.groovy")

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

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "creates build using test suites with #scriptDsl build scripts when using --incubating"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'groovy-gradle-plugin', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/groovy").assertHasDescendants("some/thing/SomeThingPlugin.groovy")
        subprojectDir.file("src/test/groovy").assertHasDescendants("some/thing/SomeThingPluginTest.groovy")
        subprojectDir.file("src/functionalTest/groovy").assertHasDescendants("some/thing/SomeThingPluginFunctionalTest.groovy")

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

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "creates with gradle.properties when using #scriptDsl build scripts with --incubating"() {
        when:
        run('init', '--type', 'groovy-gradle-plugin', '--dsl', scriptDsl.id, '--incubating')

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
    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "re-running check succeeds"() {
        given:
        run('init', '--type', 'groovy-gradle-plugin', '--dsl', scriptDsl.id)

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

    @Issue("https://github.com/gradle/gradle/issues/23298")
    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "running TestKit functional test in test source set succeeds"() {
        given:
        run('init', '--type', 'groovy-gradle-plugin', '--dsl', scriptDsl.id)

        // Copy functional test contents into default source set test
        def projectTest = subprojectDir.file('src/test/groovy/some/thing/SomeThingPluginTest.groovy')
        projectTest.text = subprojectDir.file('src/functionalTest/groovy/some/thing/SomeThingPluginFunctionalTest.groovy').text

        when:
        run('check')

        then:
        assertTestPassed("some.thing.SomeThingPluginFunctionalTest", "can run task")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
