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
    private static final String GRADLE_PROPERTY_FAILURE_MESSAGE = 'Invalid Gradle property value'
    private static final String COMMAND_LINE_FAILUE_MESSAGE = 'Invalid command line option value'

    def "can handle invalid value for Gradle property"() {
        when:
        BuildOption.Origin.GRADLE_PROPERTY.handleInvalidValue(GRADLE_PROPERTY_FAILURE_MESSAGE, COMMAND_LINE_FAILUE_MESSAGE)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == GRADLE_PROPERTY_FAILURE_MESSAGE
    }

    def "can handle invalid value for command line option"() {
        when:
        BuildOption.Origin.COMMAND_LINE.handleInvalidValue(GRADLE_PROPERTY_FAILURE_MESSAGE, COMMAND_LINE_FAILUE_MESSAGE)

        then:
        Throwable t = thrown(CommandLineArgumentException)
        t.message == COMMAND_LINE_FAILUE_MESSAGE
    }
}
