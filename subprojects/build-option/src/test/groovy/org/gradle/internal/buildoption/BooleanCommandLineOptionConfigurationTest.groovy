/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.buildoption

import spock.lang.Specification

class BooleanCommandLineOptionConfigurationTest extends Specification {

    private static final String LONG_OPTION = 'build-cache'
    private static final String SHORT_OPTION = '-C'
    private static final String ENABLED_DESCRIPTION = 'Enabled the build cache.'
    private static final String DISABLED_DESCRIPTION = 'Disabled the build cache.'

    def "is instantiated without short option"() {
        when:
        BooleanCommandLineOptionConfiguration configuration = BooleanCommandLineOptionConfiguration.create(LONG_OPTION, ENABLED_DESCRIPTION, DISABLED_DESCRIPTION)

        then:
        configuration.longOption == LONG_OPTION
        configuration.shortOption == null
        configuration.description == ENABLED_DESCRIPTION
        configuration.disabledDescription == DISABLED_DESCRIPTION
        !configuration.incubating
        !configuration.deprecated
        configuration.allOptions == [LONG_OPTION] as String[]
    }

    def "is instantiated with short option"() {
        when:
        BooleanCommandLineOptionConfiguration configuration = BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, ENABLED_DESCRIPTION, DISABLED_DESCRIPTION)

        then:
        configuration.longOption == LONG_OPTION
        configuration.shortOption == SHORT_OPTION
        configuration.description == ENABLED_DESCRIPTION
        configuration.disabledDescription == DISABLED_DESCRIPTION
        !configuration.incubating
        !configuration.deprecated
        configuration.allOptions == [LONG_OPTION, SHORT_OPTION] as String[]
    }

    def "can mark option as incubating"() {
        when:
        BooleanCommandLineOptionConfiguration configuration = BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, ENABLED_DESCRIPTION, DISABLED_DESCRIPTION)
        configuration.incubating()

        then:
        configuration.incubating
    }

    def "can mark option as deprecated"() {
        when:
        BooleanCommandLineOptionConfiguration configuration = BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, ENABLED_DESCRIPTION, DISABLED_DESCRIPTION)
        configuration.deprecated()

        then:
        configuration.deprecated
    }

    def "is instantiated with null constructor parameter values (#longOption, #shortOption, #enabledDescription, #disabledDescription)"() {
        when:
        BooleanCommandLineOptionConfiguration.create(longOption, shortOption, enabledDescription, disabledDescription)

        then:
        thrown(AssertionError)

        where:
        longOption  | shortOption  | enabledDescription  | disabledDescription
        null        | SHORT_OPTION | ENABLED_DESCRIPTION | DISABLED_DESCRIPTION
        LONG_OPTION | SHORT_OPTION | null                | DISABLED_DESCRIPTION
        LONG_OPTION | SHORT_OPTION | ENABLED_DESCRIPTION | null
    }
}
