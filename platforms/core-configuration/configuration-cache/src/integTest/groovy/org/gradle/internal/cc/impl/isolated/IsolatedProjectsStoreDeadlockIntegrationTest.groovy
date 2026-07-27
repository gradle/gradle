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

package org.gradle.internal.cc.impl.isolated

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Timeout

class IsolatedProjectsStoreDeadlockIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(30_000)

    def setup() {
        server.start()
    }

    @Issue("https://github.com/gradle/gradle/pull/38108")
    @Timeout(60)
    def "store does not deadlock when user code reads Gradle.rootProject while writing a shared object"() {
        // Isolated Projects writes the task graph to the configuration cache in parallel. Shared objects
        // (value sources, build service parameters) are written through a single shared write context
        // guarded by a monitor, while each node group is written holding its owning project's lock. The
        // store deadlocked between two writer threads:
        //
        //   - the subproject writer held the monitor (writing a value source's parameters) and, while
        //     doing so, ran user code that read Gradle.rootProject, which then tried to take the root
        //     project lock;
        //   - the root writer held the root project lock (to write the root project's node) and was
        //     blocked entering that same monitor to write its own shared object.
        //
        // At store time the captured project resolves to the real DefaultGradle, so the read hits
        // DefaultGradle.getRootProject(): removing the lock it took there breaks the cycle.
        //
        // The barrier below releases both writers only once each holds its lock: the root writer holds
        // the root project lock and the subproject writer holds the monitor. Writing a shared object only
        // needs the owning project's lock, so the subproject writer reaches the monitor and its barrier
        // request even while the root writer parks on its own request holding the root lock.
        given:
        def configurationCache = newConfigurationCacheFixture()

        settingsFile << """
            rootProject.name = "root"
            include "sub"
        """

        buildFile """
            abstract class ConstSource implements ${ValueSource.name}<String, ${ValueSourceParameters.name}.None> {
                @Override String obtain() { "const" }
            }

            tasks.register("foo") {
                // Runs while the root project lock is held, before the value source below is written.
                // Reaching the barrier here ensures the subproject already holds the monitor, so writing
                // the value source - a shared object - blocks this writer on the monitor while it holds
                // the root lock.
                inputs.property("coordinate", provider {
                    ${server.callFromBuild("root-lock-held")}
                    "ok"
                })
                inputs.property("source", providers.of(ConstSource) {})
            }
        """

        buildFile "sub/build.gradle", """
            abstract class RootNameSource implements ${ValueSource.name}<String, RootNameSource.Params> {
                interface Params extends ${ValueSourceParameters.name} {
                    Property<String> getRootName()
                }
                @Override String obtain() { parameters.rootName.get() }
            }

            def source = providers.of(RootNameSource) {
                // Computed while this writer holds the monitor (the value source is a shared object).
                // Once the barrier releases, both locks are held, so reading the root project name here
                // crosses into the root project lock held by the other writer.
                parameters.rootName = provider {
                    ${server.callFromBuild("monitor-held")}
                    gradle.rootProject.name
                }
            }

            tasks.register("foo") {
                inputs.property("source", source)
            }
        """

        and: "release both writers only once each holds its lock"
        server.expectConcurrent("root-lock-held", "monitor-held")

        when:
        isolatedProjectsRun(":foo", ":sub:foo")

        then:
        configurationCache.assertStateStored()
    }
}
