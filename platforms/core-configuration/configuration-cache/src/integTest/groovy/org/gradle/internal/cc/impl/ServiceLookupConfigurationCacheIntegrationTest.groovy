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
 * Pins that a service can be looked up inside a task action when the configuration cache entry is
 * reused. Happy-path and error-message coverage lives in the core {@code ScriptServiceLookupIntegrationTest}.
 */
@Issue("https://github.com/gradle/gradle/issues/39131")
class ServiceLookupConfigurationCacheIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = new ConfigurationCacheFixture(this)

    def "a service looked up inside a task action resolves to the task and works with the configuration cache"() {
        given:
        file("thing.txt").text = "content"
        buildFile """
            tasks.register("cleanThing") {
                doLast {
                    service(FileSystemOperations).delete {
                        delete("thing.txt")
                    }
                    println("REACHED ACTION")
                }
            }
        """

        when:
        configurationCacheRun ":cleanThing"

        then:
        configurationCache.assertStateStored()
        outputContains("REACHED ACTION")
        !file("thing.txt").exists()

        when:
        file("thing.txt").text = "content"
        configurationCacheRun ":cleanThing"

        then:
        configurationCache.assertStateLoaded()
        outputContains("REACHED ACTION")
        !file("thing.txt").exists()
    }
}
