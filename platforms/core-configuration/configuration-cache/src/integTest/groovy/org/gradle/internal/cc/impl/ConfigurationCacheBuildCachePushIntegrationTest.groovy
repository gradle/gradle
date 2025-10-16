/*
 * Copyright 2025 the original author or authors.
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


class ConfigurationCacheBuildCachePushIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "changing push via env variable does not invalidate configuration cache"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        settingsFile << """
            buildCache {
                local {
                    directory = new File('build-cache')
                    def pushProvider = providers.environmentVariable("PUSH_ENABLED").map { it == "true" }
                    getPush().set(pushProvider)
                }
            }
        """

        when:
        withBuildCache()
        executer.withEnvironmentVars(["PUSH_ENABLED": "true"]).withArguments("--configuration-cache")
        run("tasks")

        then:
        configurationCache.assertStateStored()

        when:
        withBuildCache()
        executer.withEnvironmentVars(["PUSH_ENABLED": "false"]).withArguments("--configuration-cache")
        run("tasks")

        then:
        configurationCache.assertStateLoaded()

        when:
        withBuildCache()
        executer.withEnvironmentVars(["PUSH_ENABLED": "true"]).withArguments("--configuration-cache")
        run("tasks")

        then:
        configurationCache.assertStateLoaded()
    }

    def "changing push directly invalidates configuration cache"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        settingsFile << """
            buildCache {
                local {
                    directory = new File('build-cache')
                    push = true
                }
            }
        """
        buildFile << "task myTask"


        when:
        withBuildCache()
        executer.withArguments("--configuration-cache")
        run("myTask")

        then:
        configurationCache.assertStateStored()

        when:
        settingsFile.text = """
            buildCache {
                local {
                    directory = new File('build-cache')
                    push = false
                }
            }
        """
        withBuildCache()
        executer.withArguments("--configuration-cache")
        run("myTask")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file 'settings.gradle' has changed.")
    }
}
