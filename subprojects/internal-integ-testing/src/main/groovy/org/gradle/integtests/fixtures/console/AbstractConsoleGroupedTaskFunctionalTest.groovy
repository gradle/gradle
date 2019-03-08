/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.console

import org.fusesource.jansi.Ansi
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.executer.ConsoleAttachment
import org.junit.runner.RunWith

/**
 * A base class for testing the console.
 */
@RunWith(ConsoleAttachmentTestRunner.class)
abstract class AbstractConsoleGroupedTaskFunctionalTest extends AbstractIntegrationSpec implements RichConsoleStyling {
    static ConsoleAttachment consoleAttachment

    def setup() {
        executer.withTestConsoleAttached(consoleAttachment)
        executer.beforeExecute {
            it.withConsole(consoleType)
        }
    }

    boolean errorsShouldAppearOnStdout() {
        // If both stdout and stderr is attached to the console, they are merged together
        return consoleAttachment.isStderrAttached() && consoleAttachment.isStdoutAttached()
    }

    boolean stdoutUsesStyledText() {
        if (!consoleAttachment.stdoutAttached && consoleAttachment.stderrAttached) {
            // Can currently write rich text to one stream at a time, and we prefer stderr when it is attached to the console and stdout is not
            return false
        }
        return consoleType == ConsoleOutput.Rich || consoleType == ConsoleOutput.Verbose || consoleType == ConsoleOutput.Auto && consoleAttachment.stdoutAttached
    }

    boolean stderrUsesStyledText() {
        // Can currently write rich text to one stream at a time, and we prefer stdout when it is attached to the console
        if (!consoleAttachment.stdoutAttached && consoleAttachment.stderrAttached) {
            return consoleType == ConsoleOutput.Rich || consoleType == ConsoleOutput.Verbose || consoleType == ConsoleOutput.Auto && consoleAttachment.stderrAttached
        }
        return false
    }

    abstract ConsoleOutput getConsoleType()

    protected StyledOutput styled(Ansi.Color color, Ansi.Attribute attribute) {
        return new StyledOutput(null, color, attribute)
    }

    protected StyledOutput styled(Ansi.Attribute attribute) {
        return new StyledOutput(null, null, attribute)
    }

    class StyledOutput {
        final String plainOutput
        final String styledOutput
        private final Ansi.Color color
        private final Ansi.Attribute attribute

        private StyledOutput(StyledOutput previous, Ansi.Color color, Ansi.Attribute attribute) {
            if (attribute != Ansi.Attribute.INTENSITY_BOLD && attribute != null) {
                throw new UnsupportedOperationException()
            }
            this.color = color
            this.attribute = attribute
            def previousColor = null
            def previousAttribute = null
            def styled = ""
            if (previous == null) {
                this.plainOutput = ""
            } else {
                this.plainOutput = previous.plainOutput
                previousColor = previous.color
                previousAttribute = previous.attribute
                styled = previous.styledOutput
            }
            if (attribute != null && previousAttribute == null) {
                styled += "{bold-on}"
            }
            if (attribute == null && previousAttribute != null) {
                styled += "{bold-off}"
            }
            if (color != null && color != previousColor) {
                styled += "{foreground-color " + color.name().toLowerCase() + "}"
            }
            if (color == null && previousColor != null) {
                styled += "{foreground-color default}"
            }
            this.styledOutput = styled
        }

        private StyledOutput(StyledOutput previous, String text) {
            this.color = previous.color
            this.attribute = previous.attribute
            this.plainOutput = previous.plainOutput + text
            this.styledOutput = previous.styledOutput + text
        }

        StyledOutput text(String text) {
            return new StyledOutput(this, text)
        }

        StyledOutput styled(Ansi.Attribute attribute) {
            return new StyledOutput(this, color, attribute)
        }

        StyledOutput off() {
            return new StyledOutput(this, null, null)
        }

        String getOutput() {
            return stdoutUsesStyledText() ? styledOutput : plainOutput
        }

        String getErrorOutput() {
            return stderrUsesStyledText() ? styledOutput : plainOutput
        }
    }
}
