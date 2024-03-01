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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.integtests.resolve.PluginDslSupport
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

// Restrict the number of combinations because that's not really what we want to test
@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class ComponentMetadataRulesInSettingsIntegrationTest extends AbstractModuleDependencyResolveTest implements PluginDslSupport {

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

    def "cannot mutate settings rules after settings have been evaluated"() {
        buildFile << """
            ${loggingRule('project')}

            gradle.settings.dependencyResolutionManagement {
                components.all(LoggingRule)
            }
        """

        when:
        fails ":help"

        then:
        failure.assertHasCause("Mutation of dependency resolution management in settings is only allowed during settings evaluation")
    }

    def "can prefer rules from settings even if project declares rules"() {
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

        settingsFile << """
            dependencyResolutionManagement {
                rulesMode.set(RulesMode.PREFER_SETTINGS)
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
        outputContains "Build was configured to prefer settings component metadata rules over project rules but rule 'LoggingRule' was added by build file 'build.gradle'"
    }

    def "provides a reasonable display name for opaque rules"() {
        withLoggingRuleInSettings()
        repository {
            'org:module:1.0'()
        }
        buildFile << """
            dependencies {
                conf 'org:module:1.0'
                components.all { details ->
                    println("Rule from project applied on \${details.id}")
                }
            }
        """

        settingsFile << """
            dependencyResolutionManagement {
                rulesMode.set(RulesMode.PREFER_SETTINGS)
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
        outputContains "Build was configured to prefer settings component metadata rules over project rules but rule 'opaque inline rule' was added by build file 'build.gradle'"
    }

    def "can fail the build if rules from settings are preferred and project declares rules"() {
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

        settingsFile << """
            dependencyResolutionManagement {
                rulesMode.set(RulesMode.FAIL_ON_PROJECT_RULES)
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause "Build was configured to prefer settings component metadata rules over project rules but rule 'LoggingRule' was added by build file 'build.gradle'"
    }

    // fails to delete directory under Windows otherwise
    @Requires(UnitTestPreconditions.NotWindows)
    def "rules applied in settings don't apply to plugin resolution"() {
        def pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)
        pluginPortal.start()
        def taskName = 'hello'
        def message = 'hello from plugin'
        def plugin = new PluginBuilder(file("my-plugin"))
            .addPluginWithPrintlnTask(taskName, message)
            .publishAs("org.test", "myplugin", "1.0", pluginPortal, executer)

        settingsFile << """
            dependencyResolutionManagement {
                components.all(MyRule)
            }

            class MyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    if (details.id.name == 'myplugin') {
                        throw new AssertionError("Rule shouldn't have been called")
                    }
                }
            }
        """

        withPlugins(['test-plugin': '1.0'])

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains(message)

        cleanup:
        pluginPortal.stop()
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
