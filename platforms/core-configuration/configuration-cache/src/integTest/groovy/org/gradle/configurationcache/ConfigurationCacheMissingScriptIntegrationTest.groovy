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

import org.gradle.configurationcache.fixtures.MissingScriptFixture
import spock.lang.Issue


@Issue("https://github.com/gradle/gradle/issues/18897")
class ConfigurationCacheMissingScriptIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "picking up formerly-missing #missingScriptsSpec"() {
        given:
        def configCache = newConfigurationCacheFixture()
        def fixture = missingScriptsSpec.setUpFixtureFor(this)

        and:
        fixture.createInitialBuildLayout()

        when:
        configurationCacheRun 'ok'

        then:
        configCache.assertStateStored()

        when:
        fixture.addMissingScript()

        and:
        configurationCacheRun 'ok'

        then:
        outputContains fixture.expectedCacheInvalidationMessage

        and:
        configCache.assertStateStored()

        when:
        configurationCacheRun 'ok'

        then:
        configCache.assertStateLoaded()

        where:
        missingScriptsSpec << MissingScriptFixture.specs()
    }
}
