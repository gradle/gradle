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

package org.gradle.configurationcache.isolated

class IsolatedProjectsIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "option also enables configuration cache"() {
        settingsFile << """
            println "configuring settings"
        """
        buildFile """
            println "configuring project"
            task thing { }
        """

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("thing")

        then:
        configurationCache.assertStateStored()
        outputContains(ISOLATED_PROJECTS_MESSAGE)
        outputDoesNotContain(CONFIGURATION_CACHE_MESSAGE)
        outputContains("configuring settings")
        outputContains("configuring project")

        when:
        configurationCacheRun("thing")

        then:
        configurationCache.assertStateLoaded()
        outputContains(ISOLATED_PROJECTS_MESSAGE)
        outputDoesNotContain(CONFIGURATION_CACHE_MESSAGE)
        outputDoesNotContain("configuring settings")
        outputDoesNotContain("configuring project")
    }

    def "cannot disable configuration cache when option is enabled"() {
        buildFile """
            println "configuring project"
            task thing { }
        """

        when:
        configurationCacheFails("thing", "--no-configuration-cache")

        then:
        failure.assertHasDescription("The configuration cache cannot be disabled when isolated projects is enabled.")
    }
}
