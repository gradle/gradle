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

package org.gradle.instantexecution

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import spock.lang.Unroll


class InstantExecutionEnablingIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Unroll
    def "can enable with a command line #origin"() {

        given:
        def fixture = newInstantExecutionFixture()

        when:
        run 'help', argument

        then:
        outputContainsIncubatingFeatureUsage()
        fixture.assertStateStored()

        when:
        run 'help', argument

        then:
        outputContainsIncubatingFeatureUsage()
        fixture.assertStateLoaded()

        where:
        origin            | argument
        "long option"     | CONFIGURATION_CACHE_CLI_OPTION
        "system property" | "-D${ConfigurationCacheOption.PROPERTY_NAME}=true"
    }

    def "can enable with a property in root directory gradle.properties"() {

        given:
        def fixture = newInstantExecutionFixture()
        file('gradle.properties') << """
            ${ConfigurationCacheOption.PROPERTY_NAME}=true
        """

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        fixture.assertStateStored()

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        fixture.assertStateLoaded()
    }

    def "can enable with a property in gradle user home gradle.properties"() {

        given:
        def fixture = newInstantExecutionFixture()
        executer.requireOwnGradleUserHomeDir()
        executer.gradleUserHomeDir.file('gradle.properties') << """
            ${ConfigurationCacheOption.PROPERTY_NAME}=true
        """

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        fixture.assertStateStored()

        when:
        run 'help'

        then:
        outputContainsIncubatingFeatureUsage()
        fixture.assertStateLoaded()
    }

    @Unroll
    def "can disable with a command line #origin when enabled in gradle.properties"() {

        given:
        def fixture = newInstantExecutionFixture()
        file('gradle.properties') << """
            ${ConfigurationCacheOption.PROPERTY_NAME}=true
        """

        when:
        run 'help', argument

        then:
        fixture.assertNoInstantExecution()

        where:
        origin            | argument
        "long option"     | "--no-${ConfigurationCacheOption.LONG_OPTION}"
        "system property" | "-D${ConfigurationCacheOption.PROPERTY_NAME}=false"
    }

    private void outputContainsIncubatingFeatureUsage() {
        outputContains("Configuration cache is an incubating feature.")
    }
}
