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
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(19)
        failure.assertHasCause("There was an error while evaluating a component selection rule.")
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
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(18)
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
                        withModule("org.utils:api") { ComponentSelection cs -> throw new Exception("From test") }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(18)
        failure.assertHasCause("There was an error while evaluating a component selection rule.")
        failure.assertHasCause("From test")
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
                        withModule("org.utils") { ComponentSelection cs -> }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(18)
        failureHasCause("Could not add a component selection rule for module 'org.utils'.")
        failureHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org.utils")
    }

    def "produces sensible error when @Mutate method doesn't provide ComponentSelection as the first parameter" () {
        buildFile << """
            $baseBuildFile
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all(new BadRuleSource())
                    }
                }
            }

            class BadRuleSource {
                def candidates = []

                @org.gradle.model.Mutate
                void select(String s) { }
            }
        """

        expect:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(13)
        failureHasCause("Type BadRuleSource is not a valid model rule source: \n- first parameter of rule method 'select' must be of type org.gradle.api.artifacts.ComponentSelection")
    }

    def "produces sensible error when rule source throws an exception" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            def ruleSource = new ExceptionRuleSource()

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all ruleSource
                    }
                }
            }

            class ExceptionRuleSource {
                def candidates = []

                @org.gradle.model.Mutate
                void select(ComponentSelection cs) {
                    throw new Exception("thrown from rule")
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(30)
        failure.assertHasCause("There was an error while evaluating a component selection rule.")
        failure.assertHasCause("java.lang.Exception: thrown from rule")
    }
}
