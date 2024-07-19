/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.smalltalk

import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest

class ConfigurationCacheSmalltalkIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    // TODO: remove as the API stabilizes
    def setup() {
        // Required, because the Gradle API jar is computed once a day,
        // and the new API might not be visible for tests that require compilation
        // against that API, e.g. the cases like a plugin defined in an included build
        executer.requireOwnGradleUserHomeDir()
    }

    def "constant model provider can be used as a task input"() {
        settingsFile """
            settings.buildModels.registerModel("someKey", String) {
                println("Computing model for someKey")
                "this is something"
            }
        """

        buildFile """
            abstract class Something extends DefaultTask {
                @Input
                abstract Property<String> getGreeting()

                @TaskAction void run() { println greeting.get() }
            }

            def modelProvider = project.buildModels.getModel("someKey", String)
            tasks.register("something", Something) {
                greeting = modelProvider
            }
        """

        when:
        configurationCacheRun "something"

        then:
        configurationCache.assertStateStored()
        outputContains("Computing model for someKey")
        outputContains("this is something")

        when:
        configurationCacheRun "something"

        then:
        configurationCache.assertStateLoaded()
        outputDoesNotContain("Computing model for someKey")
        outputContains("this is something")
    }

}
