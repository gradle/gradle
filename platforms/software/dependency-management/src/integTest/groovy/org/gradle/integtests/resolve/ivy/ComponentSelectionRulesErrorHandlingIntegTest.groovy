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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class ComponentSelectionRulesErrorHandlingIntegTest extends AbstractComponentSelectionRulesIntegrationTest {

    @Requires(UnitTestPreconditions.IsGroovy3)
    def "produces sensible error when bad code is supplied in component selection rule with Groovy 3"() {
        def lines = buildFile.readLines().size()
        buildFile << """
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

        when:
        repositoryInteractions {
            'org.utils:api:1.2' {
                allowAll()
            }
        }

        then:
        fails ':checkDeps'
        GradleContextualExecuter.configCache || failure.assertHasDescription("Execution failed for task ':checkDeps'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 10)
        failure.assertHasCause("There was an error while evaluating a component selection rule for org.utils:api:1.2.")
        failure.assertHasCause("Could not find method foo()")
    }

    @Requires(UnitTestPreconditions.IsGroovy4)
    def "produces sensible error when bad code is supplied in component selection rule with Groovy 4"() {
        buildFile << """
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

        when:
        repositoryInteractions {
            'org.utils:api:1.2' {
                allowAll()
            }
        }

        then:
        fails ':checkDeps'
        GradleContextualExecuter.configCache || failure.assertHasDescription("Execution failed for task ':checkDeps'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(40)
        failure.assertHasCause("There was an error while evaluating a component selection rule for org.utils:api:1.2.")
        failure.assertHasCause('No signature of method: org.gradle.api.internal.artifacts.DefaultComponentSelection.foo() is applicable for argument types: () values: []')
    }

    def "produces sensible error for invalid component selection rule"() {
        def lines = buildFile.readLines().size()
        buildFile << """
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

        when:
        repositoryInteractions {
            'org.utils:api:1.2' {
                allowAll()
            }
        }

        then:
        fails ':checkDeps'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 9)
        failureHasCause("The closure provided is not valid as a rule for 'ComponentSelectionRules'.")
        failureHasCause(message)

        where:
        parameters                           | message
        "String vs ->"                       | "First parameter of rule action closure must be of type 'ComponentSelection'."
        "ComponentSelection vs, String s ->" | "Rule may not have an input parameter of type: java.lang.String."
    }

    def "produces sensible error when closure rule throws an exception"() {
        def lines = buildFile.readLines().size()
        buildFile << """
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

        when:
        repositoryInteractions {
            'org.utils:api:1.2' {
                allowAll()
            }
        }

        then:
        fails ':checkDeps'
        GradleContextualExecuter.configCache || failure.assertHasDescription("Execution failed for task ':checkDeps'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 9)
        failure.assertHasCause("There was an error while evaluating a component selection rule for org.utils:api:1.2.")
        failure.assertHasCause("From test")
    }

    def "produces sensible error for invalid module target id"() {
        def lines = buildFile.readLines().size()
        buildFile << """
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

        when:
        repositoryInteractions {
            'org.utils:api:1.2' {
                allowAll()
            }
        }

        then:
        fails ':checkDeps'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 9)
        failureHasCause("Could not add a component selection rule for module 'org.utils'.")
        failureHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org.utils")
    }

    def "produces sensible error when @Mutate method doesn't provide ComponentSelection as the first parameter"() {
        def lines = buildFile.readLines().size()
        buildFile << """
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
        fails ':checkDeps'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 5)
        failureHasCause("""Type BadRuleSource is not a valid rule source:
- Method select(java.lang.String) is not a valid rule method: First parameter of a rule method must be of type org.gradle.api.artifacts.ComponentSelection""")
    }

    def "produces sensible error when rule source throws an exception"() {
        def lines = buildFile.readLines().size()
        buildFile << """
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

        when:
        repositoryInteractions {
            'org.utils:api:1.2' {
                allowAll()
            }
        }

        then:
        fails ':checkDeps'
        GradleContextualExecuter.configCache || failure.assertHasDescription("Execution failed for task ':checkDeps'.")
        failure.assertHasFileName("Build file '$buildFile.path'")
        failure.assertHasLineNumber(lines + 21)
        failure.assertHasCause("There was an error while evaluating a component selection rule for org.utils:api:1.2.")
        failure.assertHasCause("java.lang.Exception: thrown from rule")
    }

    def "reports missing module when component selection rule requires meta-data"() {
        buildFile << """
configurations {
    conf {
        resolutionStrategy.componentSelection {
            all { ComponentSelection selection ->
                selection.metadata // Access the metadata
            }
        }
    }
}
dependencies {
    conf "org.utils:api:+"
}
"""

        repository {
            'org.utils:api:2.1'()
        }

        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '2.1' {
                    expectGetMetadataMissing()
                }
            }
        }

        then:
        fails ":checkDeps"
        failure.assertHasCause("""Could not find any matches for org.utils:api:+ as no versions of org.utils:api are available.
Searched in the following locations:
  - ${versionListingURI('org.utils', 'api')}
${triedMetadata('org.utils', 'api', '2.1', false)}
Required by:
""")

        when:
        resetExpectations()
        repositoryInteractions {
            'org.utils:api:2.1' {
                expectResolve()
            }
        }

        then:
        succeeds ":checkDeps"
    }

    def "reports broken module when component selection rule requires meta-data"() {
        buildFile << """
configurations {
    conf {
        resolutionStrategy.componentSelection {
            all { ComponentSelection selection ->
                selection.metadata // Access the metadata
            }
        }
    }
}
dependencies {
    conf "org.utils:api:+"
}
"""

        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '2.1' {
                    withModule(IvyModule) {
                        ivy.expectGetBroken()
                    }
                    withModule(MavenModule) {
                        pom.expectGetBroken()
                    }
                }
            }
        }

        then:
        fails ":checkDeps"
        failure.assertHasCause("Could not resolve org.utils:api:+.")
        failure.assertHasCause("Could not resolve org.utils:api:2.1.")
        failure.assertHasCause("Could not GET '${legacyMetadataURI('org.utils', 'api', '2.1')}'. Received status code 500 from server: broken")

        when:
        resetExpectations()
        repositoryInteractions {
            'org.utils:api:2.1' {
                expectGetMetadata()
                withModule {
                    artifact.expectGetBroken()
                }
            }
        }

        then:
        fails ":checkDeps"
        failure.assertHasCause("Could not download api-2.1.jar (org.utils:api:2.1)")
        failure.assertHasCause("Could not GET '${artifactURI('org.utils', 'api', '2.1')}'. Received status code 500 from server: broken")

        when:
        resetExpectations()
        repositoryInteractions {
            'org.utils:api:2.1' {
                expectGetArtifact()
            }
        }

        then:
        succeeds ":checkDeps"
    }
}
