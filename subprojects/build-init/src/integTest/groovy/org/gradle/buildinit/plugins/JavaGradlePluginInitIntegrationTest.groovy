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

import static org.gradle.buildinit.plugins.GroovyGradlePluginInitIntegrationTest.NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN


class JavaGradlePluginInitIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { 'plugin' }

    def "defaults to Kotlin build scripts"() {
        when:
        run ('init', '--type', 'java-gradle-plugin')

        then:
        dslFixtureFor(KOTLIN).assertGradleFilesGenerated()
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'java-gradle-plugin', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("some/thing/SomeThingPlugin.java")
        subprojectDir.file("src/test/java").assertHasDescendants("some/thing/SomeThingPluginTest.java")
        subprojectDir.file("src/functionalTest/java").assertHasDescendants("some/thing/SomeThingPluginFunctionalTest.java")

        and:
        commonJvmFilesGenerated(scriptDsl)

        when:
        run("build")

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "pluginRegistersATask")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "canRunTask")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "creates build using test suites with #scriptDsl build scripts when using --incubating"() {
        def dslFixture = dslFixtureFor(scriptDsl)

        when:
        run('init', '--type', 'java-gradle-plugin', '--dsl', scriptDsl.id, '--incubating')

        then:
        subprojectDir.file("src/main/java").assertHasDescendants("some/thing/SomeThingPlugin.java")
        subprojectDir.file("src/test/java").assertHasDescendants("some/thing/SomeThingPluginTest.java")
        subprojectDir.file("src/functionalTest/java").assertHasDescendants("some/thing/SomeThingPluginFunctionalTest.java")

        and:
        commonJvmFilesGenerated(scriptDsl)
        dslFixture.assertHasTestSuite('test')

        when:
        run("build")

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "pluginRegistersATask")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "canRunTask")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Issue("https://github.com/gradle/gradle/issues/18206")
    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_RUNNING_ON_EMBEDDED_EXECUTER_REASON)
    def "re-running check succeeds"() {
        given:
        run('init', '--type', 'java-gradle-plugin', '--dsl', scriptDsl.id)

        when:
        run('check')

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "pluginRegistersATask")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "canRunTask")

        when:
        run('check', '--rerun-tasks')

        then:
        assertTestPassed("some.thing.SomeThingPluginTest", "pluginRegistersATask")
        assertFunctionalTestPassed("some.thing.SomeThingPluginFunctionalTest", "canRunTask")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
