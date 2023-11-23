/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveFailureTestFixture

class ComponentMetadataRulesErrorHandlingIntegrationTest extends AbstractHttpDependencyResolutionTest {
    final resolve = new ResolveFailureTestFixture(buildFile)

    def setup() {
        ivyRepo.module('org.test', 'projectA', '1.0').publish()
        buildFile << """
            repositories {
                ivy {
                    url "$ivyRepo.uri"
                }
            }

            configurations { compile }

            dependencies {
                compile 'org.test:projectA:1.0'
            }
        """
        resolve.prepare()
    }

    def "produces sensible error when bad code is supplied in component metadata rule" () {
        def lines = buildFile.readLines().size()
        buildFile << """
            class WrongRule implements ComponentMetadataRule {
                public void execute(ComponentMetadataContext context) {
                    foo()
                }
            }
            dependencies {
                components {
                    all(WrongRule)
                }
            }
        """

        expect:
        fails 'checkDeps'
        resolve.assertFailurePresent(failure)
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 3)
        failure.assertHasCause("There was an error while evaluating a component metadata rule for org.test:projectA:1.0.")
        failure.assertHasCause("No signature of method: WrongRule.foo() is applicable for argument types: () values: []")
    }

    def "produces sensible error for invalid component metadata rule" () {
        def lines = buildFile.readLines().size()
        buildFile << """
            dependencies {
                components {
                    all { ${parameters} }
                }
            }
        """

        expect:
        fails 'checkDeps'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 3)
        failureHasCause("The closure provided is not valid as a rule for 'ComponentMetadataHandler'.")
        failureHasCause(message)

        where:
        parameters                           | message
        "String vs ->"                       | "First parameter of rule action closure must be of type 'ComponentMetadataDetails'."
        "ComponentMetadata cm, String s ->"  | "Rule may not have an input parameter of type: java.lang.String. " +
                                               "Second parameter must be of type: " +
                                               "org.gradle.api.artifacts.ivy.IvyModuleDescriptor or org.gradle.api.artifacts.maven.PomModuleDescriptor."
    }

    def "produces sensible error when rule throws an exception" () {
        def lines = buildFile.readLines().size()
        buildFile << """
            class ThrowingRule implements ComponentMetadataRule {
                public void execute(ComponentMetadataContext context) {
                    throw new Exception('From Test')
                }
            }
            dependencies {
                components {
                    all(ThrowingRule)
                }
            }
        """

        expect:
        fails 'checkDeps'
        resolve.assertFailurePresent(failure)
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 3)
        failure.assertHasCause("There was an error while evaluating a component metadata rule for org.test:projectA:1.0.")
        failure.assertHasCause("From Test")
    }

    def "produces sensible error for invalid module target id" () {
        def lines = buildFile.readLines().size()
        buildFile << """
            class UnusedRule implements ComponentMetadataRule {
                public void execute(ComponentMetadataContext context) {
                    throw new Exception('From Test')
                }
            }

            dependencies {
                components {
                    withModule('org.test', UnusedRule)
                }
            }
        """

        expect:
        fails 'checkDeps'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 9)
        failureHasCause("Could not add a component metadata rule for module 'org.test'.")
        failureHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org.test")
    }

    def "produces sensible error when @Mutate method doesn't provide ComponentSelection as the first parameter" () {
        def lines = buildFile.readLines().size()
        buildFile << """
            dependencies {
                components {
                    all(new BadRuleSource())
                }
            }

            class BadRuleSource {
                @org.gradle.model.Mutate
                void process(String s) { }
            }
        """

        expect:
        fails 'checkDeps'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 3)
        failureHasCause("""Type BadRuleSource is not a valid rule source:
- Method process(java.lang.String) is not a valid rule method: First parameter of a rule method must be of type org.gradle.api.artifacts.ComponentMetadataDetails""")

    }

    def "produces sensible error when rule source throws an exception" () {
        def lines = buildFile.readLines().size()
        buildFile << """
            dependencies {
                components {
                    all(new ExceptionRuleSource())
                }
            }

            class ExceptionRuleSource {
                def candidates = []

                @org.gradle.model.Mutate
                void process(ComponentMetadataDetails cmd) {
                    throw new Exception("thrown from rule")
                }
            }
        """

        expect:
        fails 'checkDeps'
        resolve.assertFailurePresent(failure)
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 12)
        failure.assertHasCause("There was an error while evaluating a component metadata rule for org.test:projectA:1.0.")
        failure.assertHasCause("java.lang.Exception: thrown from rule")
    }
}
