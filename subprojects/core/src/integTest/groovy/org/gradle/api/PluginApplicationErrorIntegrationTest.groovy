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
}
