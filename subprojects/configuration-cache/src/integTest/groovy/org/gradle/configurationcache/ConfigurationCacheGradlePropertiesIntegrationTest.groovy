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

import org.junit.Assume

class ConfigurationCacheGradlePropertiesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "detects dynamic Gradle property access in settings script"() {
        Assume.assumeFalse(scriptFile == 'settings.gradle' && dynamicPropertyExpression =~ /property|findProperty/)
        given:
        def configurationCache = newConfigurationCacheFixture()
        file(scriptFile) << """
            println($dynamicPropertyExpression)
        """

        when:
        configurationCacheRun "help", "-PgradleProp=1"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-PgradleProp=1"

        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "-PgradleProp=2"

        then:
        configurationCache.assertStateStored()
        output.contains("because Gradle property 'gradleProp' has changed.")

        where:
        [dynamicPropertyExpression, scriptFile] << [
            ['gradleProp', 'properties.gradleProp', 'property("gradleProp")', 'findProperty("gradleProp")'],
            ['settings.gradle', 'build.gradle']
        ].combinations()
    }
}
