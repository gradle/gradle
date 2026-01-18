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

import org.gradle.util.internal.ToBeImplemented

class ConfigurationCacheTaskOptionsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    def "changing task option used at configuration time invalidates configuration cache"() {
        given:
        buildFile """
            abstract class TaskWithOption extends DefaultTask {
                @Option(option = "myOption", description = "A test option")
                @Internal
                abstract Property<String> getMyOption()

                @Input
                abstract Property<String> getConfigTimeInput()

                @TaskAction
                void run() {
                    println "Option value: \${configTimeInput.orNull}"
                }
            }

            tasks.register("testTask", TaskWithOption) {
                // provider {} forces property to be computed at configuration time.
                // In real life, the property may be something like a name of configuration to print dependencies of, etc.
                configTimeInput = provider { myOption.orNull }
            }
        """

        when:
        configurationCacheRun "testTask", "--myOption=value1"

        then:
        outputContains "Option value: value1"
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "testTask", "--myOption=value1"

        then:
        outputContains "Option value: value1"
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "testTask", "--myOption=value2"

        then:
        outputContains "Option value: value2"
        configurationCache.assertStateStored()
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/24550")
    def "changing task option used only at execution time invalidates configuration cache"() {
        given:
        buildFile """
            abstract class TaskWithOption extends DefaultTask {
                @Option(option = "myOption", description = "A test option")
                @Input
                abstract Property<String> getMyOption()

                @TaskAction
                void run() {
                    println "Option value: \${myOption.orNull}"
                }
            }

            tasks.register("testTask", TaskWithOption)
        """

        when:
        configurationCacheRun "testTask", "--myOption=value1"

        then:
        outputContains "Option value: value1"
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "testTask", "--myOption=value1"

        then:
        outputContains "Option value: value1"
        configurationCache.assertStateLoaded()

        when:
        // TODO(https://github.com/gradle/gradle/issues/24550): This currently invalidates the cache, but ideally it should not
        //   since the option is only used at execution time
        configurationCacheRun "testTask", "--myOption=value2"

        then:
        outputContains "Option value: value2"
        configurationCache.assertStateStored()
    }
}
