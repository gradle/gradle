/*
 * Copyright 2021 the original author or authors.
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

class ConfigurationCacheGradlePropertiesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "detects dynamic Gradle property access in settings script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            println($dynamicPropertyExpression)
        """

        when:
        configurationCacheRun "help", "-PtheProperty=1"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-PtheProperty=1"

        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "-PtheProperty=2"

        then:
        configurationCache.assertStateStored()
        output.contains("because Gradle property 'theProperty' has changed.")

        where:
        dynamicPropertyExpression << [
            'theProperty',
            'properties.theProperty'
        ]
    }
}
