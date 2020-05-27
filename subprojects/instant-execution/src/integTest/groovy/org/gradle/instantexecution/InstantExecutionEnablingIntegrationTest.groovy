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
        if (deprecation) {
            expectDeprecatedProperty(ConfigurationCacheOption.PROPERTY_NAME, 'on')
        }

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
        origin                       | deprecation | argument
        "long option"                | false       | ENABLE_CLI_OPT
        "system property"            | false       | ENABLE_SYS_PROP
        "deprecated system property" | true        | "-D${ConfigurationCacheOption.PROPERTY_NAME}=on"
    }

    @Unroll
    def "can enable with a #origin in root directory gradle.properties"() {

        given:
        def fixture = newInstantExecutionFixture()
        if (deprecation) {
            expectDeprecatedProperty(ConfigurationCacheOption.PROPERTY_NAME, 'on')
        }

        and:
        file('gradle.properties') << """
            $propertyLine
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

        where:
        origin                | deprecation | propertyLine
        "property"            | false       | ENABLE_GRADLE_PROP
        "deprecated property" | true        | "${ConfigurationCacheOption.PROPERTY_NAME}=on"
    }

    @Unroll
    def "can enable with a #origin in gradle user home gradle.properties"() {

        given:
        def fixture = newInstantExecutionFixture()
        if (deprecation) {
            expectDeprecatedProperty(ConfigurationCacheOption.PROPERTY_NAME, 'on')
        }

        and:
        executer.requireOwnGradleUserHomeDir()
        executer.gradleUserHomeDir.file('gradle.properties') << """
            $propertyLine
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

        where:
        origin                | deprecation | propertyLine
        "property"            | false       | ENABLE_GRADLE_PROP
        "deprecated property" | true        | "${ConfigurationCacheOption.PROPERTY_NAME}=on"
    }

    @Unroll
    def "can disable with a command line #cliOrigin when enabled in gradle.properties"() {

        given:
        def fixture = newInstantExecutionFixture()
        if (deprecation) {
            expectDeprecatedProperty(ConfigurationCacheOption.PROPERTY_NAME, 'off')
        }

        and:
        file('gradle.properties') << """
            ${ConfigurationCacheOption.PROPERTY_NAME}=true
        """

        when:
        run 'help', cliArgument

        then:
        fixture.assertNoInstantExecution()

        where:
        cliOrigin                    | deprecation | cliArgument
        "long option"                | false       | DISABLE_CLI_OPT
        "system property"            | false       | DISABLE_SYS_PROP
        "deprecated system property" | true        | "-D${ConfigurationCacheOption.PROPERTY_NAME}=off"
    }

    private void outputContainsIncubatingFeatureUsage() {
        outputContains("Configuration cache is an incubating feature.")
    }

    private void expectDeprecatedProperty(String name, String value) {
        executer.beforeExecute {
            expectDeprecationWarning("Property '$name' with value '$value' has been deprecated. This is scheduled to be removed in Gradle 7.0.")
        }
    }
}
