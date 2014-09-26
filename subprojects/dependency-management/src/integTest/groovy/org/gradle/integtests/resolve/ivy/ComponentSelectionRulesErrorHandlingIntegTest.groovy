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

package org.gradle.integtests.resolve.ivy

class ComponentSelectionRulesErrorHandlingIntegTest extends AbstractComponentSelectionRulesIntegrationTest {
    def "produces sensible error when bad code is supplied in component selection rule" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection ->
                            foo()
                        }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasCause("Could not apply component selection rule with all().")
        failure.assertHasCause("Could not find method foo()")
    }

    def "produces sensible error for invalid component selection rule" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ${parameters} }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failureHasCause("The closure provided is not valid as a rule for 'ComponentSelectionRules'.")
        failureHasCause(message)

        where:
        parameters                           | message
        "String vs ->"                       | "First parameter of rule action closure must be of type 'ComponentSelection'."
        "ComponentSelection vs, String s ->" | "Unsupported parameter type: java.lang.String"
    }

    def "produces sensible error when rule throws an exception" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection cs -> throw new Exception("From test") }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasCause("Could not apply component selection rule with all().")
        failure.assertHasCause("From test")

        where:
        rule                                                                                                           | _
        '{ ComponentSelection cs -> throw new RuntimeException("From test") }'                                         | _
        '{ ComponentSelection cs -> throw new Exception("From test") }'                                                | _
        '{ ComponentSelection cs, ComponentMetadata cm -> throw new Exception("From test") }'                          | _
        '{ ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> throw new Exception("From test") }' | _
    }

    def "produces sensible error for invalid module target id" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        module("org.utils") { ComponentSelection cs -> }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasLineNumber(18)
        failureHasCause("Could not add a component selection rule for module 'org.utils'.")
        failureHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org.utils")
    }
}
