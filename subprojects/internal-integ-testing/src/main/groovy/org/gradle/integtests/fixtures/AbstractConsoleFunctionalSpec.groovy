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

/**
 * A base class for testing the console in rich mode. Executes with a Gradle distribution and {@code "--console=rich"} command line option.
 * <p>
 * <b>Note:</b> The console output contains formatting characters.
 */
abstract class AbstractConsoleFunctionalSpec extends AbstractIntegrationSpec {
    private static final String ESC = "\u001b"
    private static final String NEWLINE = "\r?\n"

    public final static String CONTROL_SEQUENCE_START = "\u001B["
    public final static String CONTROL_SEQUENCE_SEPARATOR = ";"
    public final static String CONTROL_SEQUENCE_END = "m"
    public final static String DEFAULT_TEXT = "0;39"

    static String workInProgressLine(String plainText) {
        return boldOn() + plainText + reset()
    }

    /**
     * Wraps the text in the proper control characters for styled output in the rich console
     */
    protected String styled(String plainText, Ansi.Color color, Ansi.Attribute... attributes) {
        String styledString = CONTROL_SEQUENCE_START
        styledString += color != null ? color.fg() : Ansi.Color.DEFAULT.fg()
        if (attributes) {
            attributes.each { attribute ->
                styledString += CONTROL_SEQUENCE_SEPARATOR + attribute.value()
            }
        }
        styledString += CONTROL_SEQUENCE_END + plainText + CONTROL_SEQUENCE_START + DEFAULT_TEXT + CONTROL_SEQUENCE_END

        return styledString
    }

    def setup() {
        executer.requireGradleDistribution().withRichConsole()
    }

    /**
     * Returns all the output group for the specified task in the order they appear.
     *
     * @param taskPath a task path with ':' prefix
     * @return a collection of the output group for the specified task
     */
    Collection<String> taskOutput(String taskPath) {
        def matcher = result.output =~ /(?ms)(> Task $taskPath${quote(reset())}(${quote(eraseEndOfLine())})?$NEWLINE)(.*)$NEWLINE$NEWLINE$NEWLINE((.*${quote(boldOn())})|(BUILD ))?/
        List<String> result = new ArrayList<String>()
        while (matcher.find()) {
            String text = matcher[0][3]
            if (text.endsWith(eraseEndOfLine())) {
                text = text.substring(0, text.length() - eraseEndOfLine().length())
            }
            result.add(text)
        }

        return result
    }

    private static String quote(String ansi) {
        return ansi.replace("[", "\\[")
    }

    private static String boldOn() {
        ansi("1m")
    }

    private static String reset() {
        ansi("m")
    }

    private static String eraseEndOfLine() {
        eraseLine(0)
    }

    private static String eraseLine(int n) {
        ansi("${n}K")
    }

    private static String ansi(String command) {
        "$ESC[${command}"
    }
}
