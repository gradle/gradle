/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle-private/issues/3247")
@Requires(UnitTestPreconditions.NotJava8OnMacOs)
class PluginApplicationErrorIntegrationTest extends AbstractIntegrationSpec {
    def pluginBuilder = new PluginBuilder(file("plugin"))

    def "reports failure to apply plugin by id"() {
        given:
        pluginBuilder.addPlugin("throw new Exception('throwing plugin')", "broken")
        pluginBuilder.publishTo(executer, file('external.jar'))

        buildFile << '''
buildscript {
    dependencies {
        classpath files('external.jar')
    }
}
apply plugin: 'broken'
'''

        when:
        fails()

        then:
        failure.assertHasCause("Failed to apply plugin 'broken'")
        failure.assertHasCause("throwing plugin")
    }

    def "reports failure to apply plugin by type"() {
        buildFile << '''
apply plugin: BrokenPlugin

class BrokenPlugin implements Plugin<Project> {
    void apply(Project target) {
        throw new Exception('throwing plugin')
    }
}
'''

        when:
        fails()

        then:
        failure.assertHasCause("Failed to apply plugin class 'BrokenPlugin'")
        failure.assertHasCause("throwing plugin")
    }

    def "cannot apply a plugin that does not implement Plugin and does not extend RuleSource"() {
        buildFile << '''
apply plugin: BrokenPlugin

class BrokenPlugin {
    void apply(Project target) {
    }
}
'''

        when:
        fails()

        then:
        failure.assertHasCause("Failed to apply plugin class 'BrokenPlugin'")
        failure.assertHasCause("'BrokenPlugin' is neither a plugin or a rule source and cannot be applied.")
    }

    @ToBeImplemented
    def "applying a project plugin to settings fails with user-friendly error"() {
        settingsFile << """
            plugins {
                id("base")
            }
        """

        when:
        fails()

        then:
        failure.assertHasCause("Failed to apply plugin 'org.gradle.base'")
        // TODO: this error is cryptic
        failure.assertHasCause("class org.gradle.initialization.DefaultSettings_Decorated cannot be cast to class org.gradle.api.Project")
    }

    @ToBeImplemented
    def "applying a settings plugin to a project fails with user-friendly error"() {
        buildFile << """
            apply plugin: SomeSettingsPlugin

            class SomeSettingsPlugin implements Plugin<Settings> {
                void apply(Settings target) {
                    throw new Exception("Unreachable boom")
                }
            }
        """

        when:
        fails()

        then:
        failure.assertHasCause("Failed to apply plugin class 'SomeSettingsPlugin'")
        // TODO: this error is cryptic
        failure.assertHasCause("class org.gradle.api.internal.project.DefaultProject_Decorated cannot be cast to class org.gradle.api.initialization.Settings")
    }
}
