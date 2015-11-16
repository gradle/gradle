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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ModelRuleValidationIntegrationTest extends AbstractIntegrationSpec {

    def "invalid model name produces error message"() {
        when:
        buildScript """
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model(" ")
                    List<String> strings() {
                      []
                    }
                }
            }

            apply type: MyPlugin
        """

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Failed to apply plugin [class 'MyPlugin']")
        failure.assertHasCause("Path of declared model element created by rule MyPlugin.Rules#strings is invalid.")
        failure.assertHasCause("Model element name ' ' has illegal first character ' ' (names must start with an ASCII letter or underscore)")
    }

    def "model name can be at nested path"() {
        when:
        buildScript """
            class MyPlugin {
                static class Rules extends RuleSource {
                    @Model("foo. bar")
                    List<String> strings() {
                      []
                    }
                }
            }

            apply type: MyPlugin
        """

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Failed to apply plugin [class 'MyPlugin']")
        failure.assertHasCause("Path of declared model element created by rule MyPlugin.Rules#strings is invalid.")
        failure.assertHasCause("Model path 'foo. bar' is invalid due to invalid name component")
        failure.assertHasCause("Model element name ' bar' has illegal first character ' ' (names must start with an ASCII letter or underscore)")
    }

}
