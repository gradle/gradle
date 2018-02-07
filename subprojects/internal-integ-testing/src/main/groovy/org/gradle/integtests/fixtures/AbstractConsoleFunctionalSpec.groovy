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

package org.gradle.integtests.fixtures

import org.fusesource.jansi.Ansi
import org.gradle.api.logging.configuration.ConsoleOutput

/**
 * A base class for testing the console in rich mode. Executes with a Gradle distribution and {@code "--console=rich"} command line option.
 * <p>
 * <b>Note:</b> The console output contains formatting characters.
 */
abstract class AbstractConsoleFunctionalSpec extends AbstractIntegrationSpec {
    public final static String CONTROL_SEQUENCE_START = "\u001B["
    public final static String CONTROL_SEQUENCE_SEPARATOR = ";"
    public final static String CONTROL_SEQUENCE_END = "m"
    public final static String DEFAULT_TEXT = "0;39"

    def setup() {
        executer.withConsole(ConsoleOutput.Rich)
    }

    /**
     * Wraps the text in the proper control characters for styled output in the rich console
     */
    protected String styled(String plainText, Ansi.Color color, Ansi.Attribute... attributes) {
        String styledString = CONTROL_SEQUENCE_START
        if (color) {
            styledString += color.fg()
        }
        if (attributes) {
            attributes.each { attribute ->
                if (styledString.length() > CONTROL_SEQUENCE_START.length()) {
                    styledString += CONTROL_SEQUENCE_SEPARATOR
                }
                styledString += attribute.value()
            }
        }
        styledString += CONTROL_SEQUENCE_END + plainText + CONTROL_SEQUENCE_START
        if (color) {
            styledString += DEFAULT_TEXT
        }
        styledString += CONTROL_SEQUENCE_END

        return styledString
    }

    static String workInProgressLine(String plainText) {
        return boldOn() + plainText + reset()
    }

    private static String boldOn() {
        "${CONTROL_SEQUENCE_START}1m"
    }

    private static String reset() {
        "${CONTROL_SEQUENCE_START}m"
    }
}
