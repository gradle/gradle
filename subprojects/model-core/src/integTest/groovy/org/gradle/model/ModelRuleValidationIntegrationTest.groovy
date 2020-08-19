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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
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
        failure.assertHasCause("Failed to apply plugin class 'MyPlugin'")
        failure.assertHasCause('''Type MyPlugin.Rules is not a valid rule source:
- Method strings() is not a valid rule method: The declared model element path ' ' is not a valid path: Model element name ' ' has illegal first character ' ' (names must start with an ASCII letter or underscore).''')
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
        failure.assertHasCause("Failed to apply plugin class 'MyPlugin'")
        failure.assertHasCause('''Type MyPlugin.Rules is not a valid rule source:
- Method strings() is not a valid rule method: The declared model element path 'foo. bar' is not a valid path: Model path 'foo. bar' is invalid due to invalid name component.''')
    }
}
