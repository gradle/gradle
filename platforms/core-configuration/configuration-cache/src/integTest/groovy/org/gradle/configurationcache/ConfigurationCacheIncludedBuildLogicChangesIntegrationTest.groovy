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

import org.gradle.configurationcache.fixtures.BuildLogicChangeFixture

class ConfigurationCacheIncludedBuildLogicChangesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "invalidates cache upon change to included #fixtureSpec"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def fixture = fixtureSpec.fixtureForProjectDir(file('build-logic'))
        fixture.setup()
        settingsFile << """
            pluginManagement {
                includeBuild 'build-logic'
            }
        """
        buildFile << """
            plugins { id('$fixture.pluginId') }
        """

        when:
        configurationCacheRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputBeforeChange
        configurationCache.assertStateStored()

        when:
        configurationCacheRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputBeforeChange
        configurationCache.assertStateLoaded()

        when:
        fixture.applyChange()
        configurationCacheRunLenient fixture.task

        then:
        outputContains fixture.expectedCacheInvalidationMessage
        outputContains fixture.expectedOutputAfterChange
        configurationCache.assertStateStored()

        when:
        configurationCacheRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputAfterChange
        configurationCache.assertStateLoaded()

        where:
        fixtureSpec << BuildLogicChangeFixture.specs()
    }
}
