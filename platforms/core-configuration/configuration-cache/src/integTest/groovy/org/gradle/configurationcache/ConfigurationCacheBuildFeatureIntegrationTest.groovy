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

    def "build feature indicates requested and enabled status"() {
        def configurationCache = newConfigurationCacheFixture()

        buildFile """
            def buildFeatures = gradle.buildFeatures
            tasks.register("something") {
                doLast {
                    println "configurationCache.isRequested=" + buildFeatures.configurationCache.isRequested()
                    println "configurationCache.isEnabled=" + buildFeatures.configurationCache.isEnabled()
                }
            }
        """

        when:
        run "something"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("configurationCache.isRequested=false")
        outputContains("configurationCache.isEnabled=false")

        when:
        configurationCacheRun "something"
        then:
        configurationCache.assertStateStored()
        outputContains("configurationCache.isRequested=true")
        outputContains("configurationCache.isEnabled=true")

        when:
        configurationCacheRun "something"
        then:
        configurationCache.assertStateLoaded()
        outputContains("configurationCache.isRequested=true")
        outputContains("configurationCache.isEnabled=true")
    }

    def "disabled even if requested due to --export-keys flag"() {
        def configurationCache = newConfigurationCacheFixture()

        buildFile """
            def buildFeatures = gradle.buildFeatures
            tasks.register("something") {
                doLast {
                    println "configurationCache.isRequested=" + buildFeatures.configurationCache.isRequested()
                    println "configurationCache.isEnabled=" + buildFeatures.configurationCache.isEnabled()
                }
            }
        """

        when:
        configurationCacheRun "something", "--export-keys"
        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("configurationCache.isRequested=true")
        outputContains("configurationCache.isEnabled=false")
    }

}
