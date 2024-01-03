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

import org.gradle.configurationcache.fixtures.ScriptChangeFixture

class ConfigurationCacheIncludedBuildScriptChangesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "invalidates cache upon change to #scriptChangeSpec of included build"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def fixture = scriptChangeSpec.fixtureForProjectDir(file('build-logic'), testDirectory)
        fixture.setup()
        def build = { configurationCacheRunLenient(*fixture.buildArguments) }
        settingsFile << """
            includeBuild 'build-logic'
        """

        when:
        build()

        then:
        outputContains fixture.expectedOutputBeforeChange
        configurationCache.assertStateStored()

        when:
        build()

        then: 'scripts are not executed when loading from cache'
        outputDoesNotContain fixture.expectedOutputBeforeChange
        configurationCache.assertStateLoaded()

        when:
        fixture.applyChange()
        build()

        then:
        outputContains fixture.expectedCacheInvalidationMessage
        outputContains fixture.expectedOutputAfterChange
        configurationCache.assertStateStored()

        when:
        build()

        then:
        outputDoesNotContain fixture.expectedOutputBeforeChange
        outputDoesNotContain fixture.expectedOutputAfterChange
        configurationCache.assertStateLoaded()

        where:
        scriptChangeSpec << ScriptChangeFixture.specs()
    }
}
