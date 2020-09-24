/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.rules

import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.file.TestFile

class ComponentMetadataRulesInSettingsIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can declare component metadata rules in settings"() {
        withLoggingRuleInSettings()
        repository {
            'org:module:1.0'()
        }
        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        outputContains 'Rule from settings applied on org:module:1.0'
    }

    def "can declare component metadata rules in settings using registar"() {
        settingsFile << """
            dependencyResolutionManagement {
                components.all(LoggingRule)
            }

            ${loggingRule('settings')}
        """
        repository {
            'org:module:1.0'()
        }
        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        outputContains 'Rule from settings applied on org:module:1.0'
    }

    def "doesn't apply rules from settings if project declares rules"() {
        withLoggingRuleInSettings()
        repository {
            'org:module:1.0'()
        }
        buildFile << """
            dependencies {
                conf 'org:module:1.0'
                components.all(LoggingRule)
            }

            ${loggingRule('project')}
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        outputContains 'Rule from project applied on org:module:1.0'
    }

    private TestFile withLoggingRuleInSettings() {
        settingsFile << """
            dependencyResolutionManagement {
                components {
                    all(LoggingRule)
                }
            }

            ${loggingRule('settings')}
        """
    }

    private static String loggingRule(String source) {
        """
            class LoggingRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    println("Rule from $source applied on \${context.details.id}")
                }
            }
"""
    }
}
