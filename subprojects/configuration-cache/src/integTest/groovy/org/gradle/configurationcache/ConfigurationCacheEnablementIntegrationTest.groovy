/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import spock.lang.Unroll


class ConfigurationCacheEnablementIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Unroll
    def "can enable with a command line #origin"() {

        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        run 'help', argument

        then:
        outputContainsIncubatingFeatureUsage()
        configurationCache.assertStateStored()

        when:
        run 'help', argument

        then:
        outputContainsIncubatingFeatureUsage()
        configurationCache.assertStateLoaded()

        where:
        origin            | argument
        "long option"     | ENABLE_CLI_OPT
        "system property" | ENABLE_SYS_PROP
    }

    def "can enable with a property in root directory gradle.properties"() {

        given:
        def configurationCache = newConfigurationCacheFixture()

        and:
        file('gradle.properties') << """
            $ENABLE_GRADLE_PROP
        """

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        configurationCache.assertStateStored()

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        configurationCache.assertStateLoaded()
    }

    def "can enable with a property in gradle user home gradle.properties"() {

        given:
        def configurationCache = newConfigurationCacheFixture()

        and:
        executer.requireOwnGradleUserHomeDir()
        executer.gradleUserHomeDir.file('gradle.properties') << """
            $ENABLE_GRADLE_PROP
        """

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        configurationCache.assertStateStored()

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        configurationCache.assertStateLoaded()
    }

    @Unroll
    def "can disable with a command line #cliOrigin when enabled in gradle.properties"() {

        given:
        def configurationCache = newConfigurationCacheFixture()

        and:
        file('gradle.properties') << """
            ${ConfigurationCacheOption.PROPERTY_NAME}=true
        """

        when:
        run 'help', cliArgument

        then:
        configurationCache.assertNoConfigurationCache()

        where:
        cliOrigin         | cliArgument
        "long option"     | DISABLE_CLI_OPT
        "system property" | DISABLE_SYS_PROP
    }

    private void outputContainsIncubatingFeatureUsage() {
        outputContains(CONFIGURATION_CACHE_MESSAGE)
        outputDoesNotContain(ISOLATED_PROJECTS_MESSAGE)
    }
}
