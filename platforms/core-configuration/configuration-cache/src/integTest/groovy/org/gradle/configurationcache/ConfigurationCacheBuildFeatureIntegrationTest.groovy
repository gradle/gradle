/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheBuildFeatureIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "build feature indicates requested and active status"() {
        def configurationCache = newConfigurationCacheFixture()

        buildFile """
            import org.gradle.api.configuration.BuildFeatures

            def buildFeatures = gradle.services.get(BuildFeatures)
            tasks.register("something") {
                doLast {
                    println "configurationCache.requested=" + buildFeatures.configurationCache.requested.getOrNull()
                    println "configurationCache.active=" + buildFeatures.configurationCache.active.get()
                }
            }
        """

        when:
        run "something"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("configurationCache.requested=null")
        outputContains("configurationCache.active=false")

        when:
        run "something", "--no-configuration-cache"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("configurationCache.requested=false")
        outputContains("configurationCache.active=false")

        when:
        configurationCacheRun "something"
        then:
        configurationCache.assertStateStored()
        outputContains("configurationCache.requested=true")
        outputContains("configurationCache.active=true")

        when:
        configurationCacheRun "something"
        then:
        configurationCache.assertStateLoaded()
        outputContains("configurationCache.requested=true")
        outputContains("configurationCache.active=true")
    }

    def "not active even if requested due to --export-keys flag"() {
        def configurationCache = newConfigurationCacheFixture()

        buildFile """
            import org.gradle.api.configuration.BuildFeatures

            def buildFeatures = gradle.services.get(BuildFeatures)
            tasks.register("something") {
                doLast {
                    println "configurationCache.requested=" + buildFeatures.configurationCache.requested.getOrNull()
                    println "configurationCache.active=" + buildFeatures.configurationCache.active.get()
                }
            }
        """

        when:
        configurationCacheRun "something", "--export-keys"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("configurationCache.requested=true")
        outputContains("configurationCache.active=false")
    }

}
