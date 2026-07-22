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
        buildFile """
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

    def "a settings-scoped service captured in a settings script survives the configuration cache and is usable in a task action"() {
        given:
        settingsFile """
            // Captured at settings-script scope, where `service` resolves to the Settings receiver
            // and BuildLayout is available. The instance is then used from a task action.
            def captured = service(BuildLayout)
            gradle.rootProject {
                tasks.register("useLayout") {
                    doLast {
                        // Tripwire on the first line: prints only if the action is actually entered.
                        println("REACHED ACTION")
                        println("settings dir: " + captured.settingsDirectory.asFile.name)
                    }
                }
            }
        """

        when: "run without the configuration cache, the captured settings-scoped service is used directly"
        run ":useLayout"

        then:
        outputContains("REACHED ACTION")
        outputContains("settings dir: " + testDirectory.name)

        when: "the configuration cache entry is stored, BuildLayout is captured by value"
        configurationCacheRun ":useLayout"

        then:
        configurationCache.assertStateStored()
        outputContains("REACHED ACTION")
        outputContains("settings dir: " + testDirectory.name)

        when: "the configuration cache entry is reused, the captured BuildLayout is restored by value"
        configurationCacheRun ":useLayout"

        then:
        configurationCache.assertStateLoaded()
        outputContains("REACHED ACTION")
        outputContains("settings dir: " + testDirectory.name)
    }
}
