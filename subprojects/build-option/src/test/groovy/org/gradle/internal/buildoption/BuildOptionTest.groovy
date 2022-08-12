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

import org.gradle.cli.CommandLineArgumentException
import spock.lang.Specification

class BuildOptionTest extends Specification {
    private static final String VALUE = '0'
    private static final String HINT = 'must be positive'
    private static final String GRADLE_PROPERTY = 'org.gradle.property'
    private static final String OPTION = 'option'

    def "can handle invalid value for Gradle property with empty #hint"() {
        when:
        Origin.forGradleProperty(GRADLE_PROPERTY).handleInvalidValue(VALUE, hint)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == "Value '0' given for org.gradle.property Gradle property is invalid"

        where:
        hint << ['', ' ', null]
    }

    def "can handle invalid value for Gradle property with concrete hint"() {
        when:
        Origin.forGradleProperty(GRADLE_PROPERTY).handleInvalidValue(VALUE, HINT)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == "Value '0' given for org.gradle.property Gradle property is invalid (must be positive)"
    }

    def "can handle invalid value for command line option with concrete hint"() {
        when:
        Origin.forCommandLine(OPTION).handleInvalidValue(VALUE, HINT)

        then:
        Throwable t = thrown(CommandLineArgumentException)
        t.message == "Argument value '0' given for --option option is invalid (must be positive)"
    }
}
