/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import spock.lang.Issue

/**
 * Pins the Groovy closure dispatch and configuration-cache behavior of the public {@code service(Class)}
 * lookup in the corner cases where the receiver resolution or the captured instance interacts with the
 * configuration cache. Happy-path and error-message coverage lives in the core
 * {@code PublicServiceLookupIntegrationTest}.
 */
@Issue("https://github.com/gradle/gradle/issues/13121")
class ServiceLookupGroovyDispatchIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = new ConfigurationCacheFixture(this)

    def "service lookup in an owner-first task closure fails as an unsupported script reference at execution time"() {
        given:
        buildFile << """
            tasks.register("some") {
                onlyIf {
                    service(ProviderFactory)
                    throw new IllegalStateException("UNREACHABLE")
                }
                doFirst {
                }
            }
        """

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFailure("Invocation of 'service' references a Gradle script object from a Groovy closure at execution time, which is unsupported with the configuration cache.") {
            // The cause is not reported
        }
        outputDoesNotContain("UNREACHABLE")

        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Task `:some` of type `org.gradle.api.DefaultTask`: invocation of 'service' references a Gradle script object from a Groovy closure at execution time, which is unsupported with the configuration cache."
        }
    }

    def "looking up a settings-only service through a task closure resolves to the task and is rejected at configuration time"() {
        given:
        settingsFile << """
            gradle.rootProject {
                tasks.register("useLayout") {
                    // `service` here resolves to the Task receiver, which does not expose the
                    // settings-only BuildLayout, so this fails while the task is being configured.
                    def captured = service(BuildLayout)
                    doLast {
                        // Tripwire on the first line: if the action is ever entered, this prints.
                        println("REACHED ACTION")
                        println("settings dir: " + captured.settingsDirectory.asFile.name)
                    }
                }
            }
        """

        when:
        fails ":useLayout"

        then:
        // The rejection happens while the task is being created, not while it runs...
        failure.assertHasCause("Could not create task ':useLayout'.")
        failure.assertHasCause("org.gradle.api.file.BuildLayout is not available in tasks. It is available in settings scripts and plugins.")
        // ...so the task action is never entered.
        outputDoesNotContain("REACHED ACTION")
    }

    def "a settings-scoped service captured in a settings script and used in a task action cannot be re-resolved under the configuration cache"() {
        given:
        settingsFile << """
            // Captured at settings-script scope, where `service` resolves to the Settings receiver
            // and BuildLayout is available. The instance is then used from a task action.
            def captured = service(BuildLayout)
            gradle.rootProject {
                tasks.register("useLayout") {
                    doLast {
                        println("settings dir: " + captured.settingsDirectory.asFile.name)
                    }
                }
            }
        """

        when: "run without the configuration cache, the captured settings-scoped service is used directly"
        run ":useLayout"

        then:
        outputContains("settings dir: " + testDirectory.name)

        when: "run with the configuration cache, the captured service is re-resolved from the task's own project registry, which cannot see the settings scope"
        configurationCacheFails ":useLayout"

        then:
        failure.assertHasCause("No service of type BuildLayout available in project services.")
    }
}
