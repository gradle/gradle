/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.FeaturePreviews
import org.gradle.internal.buildoption.FeatureFlags

class ConfigurationCacheFeatureFlagsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "toggling feature flag with system property invalidates cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        and: 'feature flag access at configuration time'
        buildFile """
            gradle.services.get($FeatureFlags.name).isEnabled(${FeaturePreviews.name}.Feature.STABLE_CONFIGURATION_CACHE)
            task check {}
        """

        when:
        configurationCacheRunLenient "check"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRunLenient "-D${FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE.systemPropertyName}=true", "check"

        then:
        configurationCache.assertStateStored()
    }

    def "feature flag state is restored when running from cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            plugins {
                id("groovy")
            }

            dependencies {
                implementation localGroovy()
            }
        """)

        settingsFile("""
            enableFeaturePreview("${FeaturePreviews.Feature.GROOVY_COMPILATION_AVOIDANCE.name()}")
        """)

        file("src/main/groovy/Main.groovy") << """
            class Main {}
        """

        when:
        configurationCacheRun("compileGroovy")

        then:
        configurationCache.assertStateStored()
        outputContains("Groovy compilation avoidance is an incubating feature")

        when:
        configurationCacheRun("compileGroovy", "--rerun-tasks")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Groovy compilation avoidance is an incubating feature")
    }
}
