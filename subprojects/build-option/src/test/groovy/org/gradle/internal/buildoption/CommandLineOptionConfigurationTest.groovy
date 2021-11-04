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

class CommandLineOptionConfigurationTest extends Specification {

    private static final String LONG_OPTION = 'info'
    private static final String SHORT_OPTION = 'i'
    private static final String DESCRIPTION = 'Sets the info log level.'

    def "is instantiated without short option"() {
        when:
        CommandLineOptionConfiguration configuration = CommandLineOptionConfiguration.create(LONG_OPTION, DESCRIPTION)

        then:
        configuration.longOption == LONG_OPTION
        configuration.shortOption == null
        configuration.description == DESCRIPTION
        !configuration.incubating
        !configuration.deprecated
        configuration.allOptions == [LONG_OPTION] as String[]
    }

    def "is instantiated with short option"() {
        when:
        CommandLineOptionConfiguration configuration = CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION)

        then:
        configuration.longOption == LONG_OPTION
        configuration.shortOption == SHORT_OPTION
        configuration.description == DESCRIPTION
        !configuration.incubating
        !configuration.deprecated
        configuration.allOptions == [LONG_OPTION, SHORT_OPTION] as String[]
    }

    def "can mark option as incubating"() {
        when:
        CommandLineOptionConfiguration configuration = CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION)
        configuration.incubating()

        then:
        configuration.incubating
    }

    def "can mark option as deprecated"() {
        when:
        CommandLineOptionConfiguration configuration = CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION)
        configuration.deprecated()

        then:
        configuration.deprecated
    }

    def "is instantiated with null constructor parameter values (#longOption, #shortOption, #description)"() {
        when:
        CommandLineOptionConfiguration.create(longOption, shortOption, description)

        then:
        thrown(AssertionError)

        where:
        longOption  | shortOption  | description
        null        | SHORT_OPTION | DESCRIPTION
        LONG_OPTION | SHORT_OPTION | null
    }
}
