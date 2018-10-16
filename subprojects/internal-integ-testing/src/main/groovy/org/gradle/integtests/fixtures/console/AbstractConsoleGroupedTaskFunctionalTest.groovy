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
        // If stderr is attached to the console or if we'll use the fallback console
        return (consoleAttachment.isStderrAttached() && consoleAttachment.isStdoutAttached()) || usesFallbackConsole()
    }

    boolean usesFallbackConsole() {
        return consoleAttachment == ConsoleAttachment.NOT_ATTACHED && (consoleType == ConsoleOutput.Rich || consoleType == ConsoleOutput.Verbose)
    }

    abstract ConsoleOutput getConsoleType()

    protected StyledOutput styled(String plainOutput, Ansi.Color color, Ansi.Attribute... attributes) {
        return new StyledOutput(plainOutput, color, attributes)
    }

    public class StyledOutput {
        private final String plainOutput
        private final String styledOutput

        StyledOutput(String plainOutput, Ansi.Color color, Ansi.Attribute... attributes) {
            this.plainOutput = plainOutput
            this.styledOutput = styledText(plainOutput, color, attributes)
        }

        public String getOutput() {
            return consoleAttachment.stdoutAttached ? styledOutput : plainOutput
        }

        public String getErrorOutput() {
            return consoleAttachment.stderrAttached ? styledOutput : plainOutput
        }
    }
}
