/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.cc.impl.fixtures.ConfigurationCacheOptOutDeprecations
import spock.lang.Issue

@Issue([
    "https://github.com/gradle/gradle/issues/39163",
    "https://github.com/gradle/gradle/issues/39164",
    "https://github.com/gradle/gradle/issues/39165",
    "https://github.com/gradle/gradle/issues/39166",
])
class ConfigurationCacheOptOutDeprecationIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements ConfigurationCacheOptOutDeprecations {

    private static final String IGNORE_FS_CHECKS_PROPERTY = StartParameterBuildOptions.ConfigurationCacheIgnoredFileSystemCheckInputs.PROPERTY_NAME
    private static final String IGNORE_INPUTS_IN_SERIALIZATION_PROPERTY = StartParameterBuildOptions.ConfigurationCacheIgnoreInputsDuringStore.PROPERTY_NAME
    private static final String IGNORE_UNSUPPORTED_LISTENERS_PROPERTY = StartParameterBuildOptions.ConfigurationCacheIgnoreUnsupportedBuildEventsListeners.PROPERTY_NAME
    private static final String SKIP_LOGGING_LISTENERS_PROPERTY = StartParameterBuildOptions.ConfigurationCacheSkipTaskLoggingListenersSerialization.PROPERTY_NAME

    def "setting #property in gradle.properties emits a deprecation warning on cache store and on cache hit"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        file("gradle.properties") << "$property=$value"

        when:
        expectDeprecatedOptOutWarning(property)
        configurationCacheRun("help")

        then:
        configurationCache.assertStateStored()

        when:
        expectDeprecatedOptOutWarning(property)
        configurationCacheRun("help")

        then:
        configurationCache.assertStateLoaded()

        where:
        property                                | value
        IGNORE_FS_CHECKS_PROPERTY               | "build/*.lock"
        IGNORE_INPUTS_IN_SERIALIZATION_PROPERTY | "true"
        IGNORE_UNSUPPORTED_LISTENERS_PROPERTY   | "true"
        SKIP_LOGGING_LISTENERS_PROPERTY         | "true"
    }

    def "setting #property on the command line emits a deprecation warning even when the configuration cache is disabled"() {
        given:
        expectDeprecatedOptOutWarning(property)

        expect:
        succeeds("help", "-D$property=$value", "--no-configuration-cache")

        where:
        property                                | value
        IGNORE_FS_CHECKS_PROPERTY               | "build/*.lock"
        IGNORE_INPUTS_IN_SERIALIZATION_PROPERTY | "true"
        IGNORE_UNSUPPORTED_LISTENERS_PROPERTY   | "true"
        SKIP_LOGGING_LISTENERS_PROPERTY         | "true"
    }

    def "explicitly setting #property to false emits a deprecation warning"() {
        given:
        expectDeprecatedOptOutWarning(property)

        expect:
        succeeds("help", "-D$property=false")

        where:
        property << [
            IGNORE_INPUTS_IN_SERIALIZATION_PROPERTY,
            IGNORE_UNSUPPORTED_LISTENERS_PROPERTY,
            SKIP_LOGGING_LISTENERS_PROPERTY,
        ]
    }

    def "no deprecation warning is emitted when the opt-out properties are not set"() {
        expect:
        configurationCacheRun("help")
    }
}
